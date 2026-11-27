package io.github.tomuius.cartilou.ui.scanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.cartilou.data.scan.CardMatcher
import io.github.tomuius.cartilou.data.scan.MatchResult
import io.github.tomuius.cartilou.data.scan.ScanBasket
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.supabase.proposedVersion
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.QuickVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La liste des cartes nécessaire au scan : en préparation, en erreur, ou prête. */
sealed interface CatalogState {
    data object Loading : CatalogState
    data class Error(val message: String) : CatalogState
    data object Ready : CatalogState
}

/**
 * Nombre d'images lues de suite sans revoir la dernière carte ajoutée, avant
 * de considérer qu'elle a quitté le cadre (et qu'un nouvel exemplaire de la
 * même carte peut être compté). À revoir après des tests : trop bas, une carte
 * mal lue un instant serait comptée deux fois ; trop haut, il faut attendre
 * plus longtemps entre deux exemplaires identiques.
 */
private const val FRAMES_TO_LEAVE = 10

/**
 * Le ViewModel de l'onglet Scanner.
 *
 * Chaque image de la caméra donne un texte (voir CameraPreview.kt), que
 * [onText] confie au CardMatcher. Une carte n'est mise au panier qu'après
 * deux lectures identiques de suite (pour éviter les fausses lectures
 * pendant que la carte bouge), puis plus jamais tant qu'elle reste devant
 * l'objectif.
 */
class ScannerViewModel : ViewModel() {

    var catalog by mutableStateOf<CatalogState>(CatalogState.Loading)
        private set

    private var matcher: CardMatcher? = null

    /** La version mise au panier pour chaque nouvelle carte. */
    var selected by mutableStateOf(QuickVersion.Proposed)

    /** Le message sous l'aperçu de la caméra. */
    var status by mutableStateOf("Placez une carte dans le cadre, numéro bien visible.")
        private set

    /** Cartes entre lesquelles choisir (plusieurs correspondent), ou null. */
    var choice by mutableStateOf<List<CardBrief>?>(null)
        private set

    /** Augmente à chaque carte mise au panier : l'écran fait alors vibrer le téléphone. */
    var addedCount by mutableIntStateOf(0)
        private set

    var validating by mutableStateOf(false)
        private set

    /** Message de la validation du panier, ou null. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Vrai si la validation a échoué faute de connexion au compte. */
    var needsSignIn by mutableStateOf(false)
        private set

    /** Lu à l'image précédente, en attente d'une deuxième lecture identique. */
    private var pendingKey: String? = null

    /** Déjà mis au panier et encore devant l'objectif. */
    private var inViewKey: String? = null
    private var framesWithoutInView = 0

    init {
        loadCatalog()
    }

    /** Charge la liste de toutes les cartes (réseau la première fois, puis cache) et prépare la recherche. */
    fun loadCatalog() {
        viewModelScope.launch {
            catalog = CatalogState.Loading
            catalog = try {
                // Lecture de la liste (plusieurs milliers de cartes, plusieurs Mo de
                // JSON) et préparation de la recherche en arrière-plan
                // (Dispatchers.Default), pour ne pas figer l'écran.
                matcher = withContext(Dispatchers.Default) {
                    CardMatcher(Tcgdex.allCards(), Tcgdex.allSets())
                }
                CatalogState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CatalogState.Error("Impossible de charger la liste des cartes chez TCGdex. Vérifiez la connexion à Internet.")
            }
        }
    }

    /** Texte lu sur une image de la caméra. */
    fun onText(text: String) {
        val matcher = matcher ?: return
        if (choice != null) return // un choix est en cours : on attend
        when (val result = matcher.match(text)) {
            is MatchResult.One -> handle(result.card.id, listOf(result.card))
            is MatchResult.Several -> handle(result.cards.map { it.id }.sorted().joinToString(","), result.cards)
            is MatchResult.NumberOnly -> {
                noCard()
                status = "Numéro lu : ${result.number}. Nom pas encore reconnu…"
            }
            MatchResult.NoNumber -> {
                noCard()
                if (inViewKey == null) status = "Placez une carte dans le cadre, numéro bien visible."
            }
        }
    }

    private fun handle(key: String, cards: List<CardBrief>) {
        if (key == inViewKey) {
            framesWithoutInView = 0 // toujours la même carte devant l'objectif
            return
        }
        if (key != pendingKey) {
            pendingKey = key // première lecture : on attend la confirmation
            return
        }
        pendingKey = null
        inViewKey = key
        framesWithoutInView = 0
        if (cards.size == 1) {
            putInBasket(cards.first())
        } else {
            choice = cards
            status = "Plusieurs cartes possibles : choisissez la bonne."
        }
    }

    private fun noCard() {
        pendingKey = null
        if (inViewKey != null && ++framesWithoutInView >= FRAMES_TO_LEAVE) inViewKey = null
    }

    private fun putInBasket(card: CardBrief) {
        ScanBasket.add(card, selected.version)
        addedCount++
        status = "Ajoutée au panier : ${card.name} (n° ${card.localId})"
        message = null
    }

    /** Choix fait parmi plusieurs cartes. */
    fun choose(card: CardBrief) {
        choice = null
        putInBasket(card)
    }

    /** Aucune des cartes proposées : on referme sans rien ajouter. */
    fun dismissChoice() {
        choice = null
        status = "Placez une carte dans le cadre, numéro bien visible."
    }

    /** Le nom de l'extension d'une carte (pour départager des rééditions), si connu. */
    fun setNameOf(card: CardBrief): String? = matcher?.setNameOf(card)

    /**
     * Ajoute tout le panier à la collection, ligne par ligne. Chaque ligne
     * enregistrée quitte le panier ; au premier échec, on s'arrête et le reste
     * attend dans le panier.
     */
    fun validate() {
        if (validating) return
        needsSignIn = false
        if (Account.currentUserId() == null) {
            message = "Connectez-vous dans l'onglet Compte pour ajouter le panier à votre collection."
            needsSignIn = true
            return
        }
        viewModelScope.launch {
            validating = true
            message = null
            var added = 0
            var failed = false
            for (item in ScanBasket.items.value.reversed()) {
                try {
                    // La fiche donne l'extension et, pour « Proposée », la version.
                    val card = try {
                        Tcgdex.card(item.cardId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw DisplayableException(
                            "Fiche de « ${item.name} » introuvable chez TCGdex. Vérifiez la connexion à Internet.", e,
                        )
                    }
                    val version = item.cardVersion ?: proposedVersion(card.variants)
                    OwnedCards.addCopies(card.id, card.set.id, version, item.quantity)
                    ScanBasket.remove(item)
                    added += item.quantity
                } catch (e: DisplayableException) {
                    message = e.message
                    failed = true
                    break
                }
            }
            if (!failed) {
                message = if (added == 1) "1 exemplaire ajouté à votre collection."
                else "$added exemplaires ajoutés à votre collection."
            }
            validating = false
        }
    }
}
