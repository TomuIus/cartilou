package io.github.tomuius.cartilou.ui.scanner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.cartilou.data.scan.CardImageHash
import io.github.tomuius.cartilou.data.scan.CardMatcher
import io.github.tomuius.cartilou.data.scan.GrayThumbnail
import io.github.tomuius.cartilou.data.scan.MatchResult
import io.github.tomuius.cartilou.data.scan.Placement
import io.github.tomuius.cartilou.data.scan.ReferenceHashes
import io.github.tomuius.cartilou.data.scan.ScanBasket
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.supabase.proposedVersion
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.QuickVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
 * Comparaison d'images : la carte la plus ressemblante n'est retenue d'office
 * que si son empreinte diffère de celle de la photo d'au plus MAX_DISTANCE bits
 * (sur 64), et si elle devance la suivante d'au moins MIN_GAP bits. Sinon, on
 * laisse choisir, les cartes rangées de la plus ressemblante à la moins
 * ressemblante.
 *
 * Valeurs choisies le 28/11/2026 (option A) d'après deux essais du Set de Base :
 * bonne carte à 9 et 10, réédition à la même illustration 3 points derrière,
 * illustrations différentes à 16 et plus. À revoir après des tests (l'encadré
 * de test affiche les distances).
 */
private const val MAX_DISTANCE = 13
private const val MIN_GAP = 5

/**
 * Photos de la carte (vignettes des images où du texte a été lu dans le cadre)
 * gardées pour la comparaison : les SHOTS_BEFORE dernières avant que la carte
 * soit reconnue, puis celles qui arrivent pendant la comparaison, jusqu'à
 * SHOTS_MAX en tout. Chaque carte candidate est comparée à chacune, et l'on
 * garde la meilleure ressemblance : une photo floue ou prise pendant un
 * mouvement ne gâche donc pas tout. À revoir après des tests.
 */
private const val SHOTS_BEFORE = 4
private const val SHOTS_MAX = 12

/**
 * Durée minimale d'une comparaison, en millisecondes : le temps que la carte
 * soit bien posée et que d'autres photos arrivent, même quand les images des
 * candidates sont déjà téléchargées. À revoir après des tests.
 */
private const val SETTLE_MILLIS = 700L

/** Une carte candidate après comparaison : distance au découpage le plus ressemblant, et ce découpage. */
private class Ranked(val card: CardBrief, val distance: Int?, val variant: Placement.Variant?)

private const val DEFAULT_STATUS = "Placez une carte dans le cadre."

/**
 * Le ViewModel de l'onglet Scanner.
 *
 * Chaque image de la caméra donne les lignes lues dans le cadre et
 * l'empreinte de la carte (voir CameraPreview.kt), que [onFrame] confie au
 * CardMatcher. Une carte (ou une liste de cartes possibles) n'est retenue
 * qu'après deux lectures identiques de suite, pour éviter les fausses lectures
 * pendant que la carte bouge, puis plus jamais tant qu'elle reste devant
 * l'objectif. Plusieurs cartes possibles : on compare leurs images à la photo.
 *
 * [appContext] : le contexte de l'appli entière (pas d'un écran), pour
 * télécharger les images des cartes candidates.
 */
class ScannerViewModel(private val appContext: Context) : ViewModel() {

    var catalog by mutableStateOf<CatalogState>(CatalogState.Loading)
        private set

    private var matcher: CardMatcher? = null

    /** La version mise au panier pour chaque nouvelle carte. */
    var selected by mutableStateOf(QuickVersion.Proposed)

    /**
     * L'extension dans laquelle chercher, ou null pour toutes (décision du
     * 28/11/2026). Reste choisie tant que l'appli est ouverte.
     */
    var selectedSet by mutableStateOf<CardSetBrief?>(null)
        private set

    /** Les cartes de l'extension choisie ; null tant qu'elles se chargent (ou sans extension choisie). */
    private var selectedCardIds: Set<String>? = null
    private var setLoading: Job? = null

    /** Le message sous l'aperçu de la caméra. */
    var status by mutableStateOf(DEFAULT_STATUS)
        private set

    /** Cartes entre lesquelles choisir, de la plus ressemblante à la moins ressemblante, ou null. */
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

    /**
     * Le contenu de l'encadré de test (décision du 27/11/2026, option B) : le
     * texte lu dans le cadre, le nombre de lignes ignorées hors du cadre, et le
     * résultat de la dernière comparaison d'images. Ne change plus tant que
     * [testTextFrozen] est vrai.
     */
    var testText by mutableStateOf("")
        private set

    /** Vrai quand l'encadré de test est figé (pour lire ou copier son contenu). */
    var testTextFrozen by mutableStateOf(false)
        private set

    /**
     * Pour l'encadré de test : la zone de la caméra qui a servi à la dernière
     * comparaison d'images (celle qui ressemblait le plus à la première carte du
     * classement), pour vérifier que le découpage tombe bien sur la carte.
     */
    var testThumbnail by mutableStateOf<ImageBitmap?>(null)
        private set

    private var lastThumbnail: ImageBitmap? = null

    /** Résultat de la dernière comparaison d'images, pour l'encadré de test. */
    private var lastComparison: String? = null

    /** Vrai pendant une comparaison d'images (téléchargement des images candidates). */
    private var comparing = false

    /** Les dernières photos de la carte (voir SHOTS_BEFORE). */
    private val recentShots = ArrayDeque<GrayThumbnail>()

    /** Pendant une comparaison : les photos qui serviront (voir SHOTS_MAX) ; null sinon. */
    private var comparisonShots: MutableList<GrayThumbnail>? = null

    /** Lu à l'image précédente, en attente d'une deuxième lecture identique. */
    private var pendingKey: String? = null

    /** Cartes déjà traitées (mise au panier ou choix proposé) et encore devant l'objectif. */
    private var inViewIds: Set<String> = emptySet()
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

    /** Une image de la caméra a été lue. */
    fun onFrame(frame: ScanFrame) {
        val shot = frame.thumbnail
        if (shot != null && frame.lines.isNotEmpty()) {
            recentShots.addLast(shot)
            while (recentShots.size > SHOTS_BEFORE) recentShots.removeFirst()
            comparisonShots?.let { shots ->
                shots += shot
                while (shots.size > SHOTS_MAX) shots.removeAt(0)
            }
        }
        if (!testTextFrozen) {
            testText = testTextOf(frame)
            testThumbnail = lastThumbnail
        }
        val matcher = matcher ?: return
        if (choice != null || comparing) return // un choix ou une comparaison est en cours : on attend
        if (selectedSet != null && selectedCardIds == null) return // l'extension choisie se charge encore
        when (val result = matcher.match(frame.lines, selectedCardIds)) {
            is MatchResult.One -> handle(setOf(result.card.id)) { putInBasket(result.card) }
            is MatchResult.Candidates -> handle(result.cards.map { it.id }.toSet()) {
                compareImages(result.cards, result.clue)
            }
            is MatchResult.TooMany -> {
                noCard()
                status = "${result.clue} : ${result.count} cartes possibles, trop pour comparer les images. Montrez bien le numéro."
            }
            MatchResult.NoClue -> {
                noCard()
                if (inViewIds.isEmpty()) status = defaultStatus()
            }
            MatchResult.NotInSet -> {
                noCard()
                status = "Cette carte n'est pas dans « ${selectedSet?.name} ». Touchez ✕ pour chercher dans toutes les extensions."
            }
        }
    }

    /**
     * [ids] : la carte (ou les cartes possibles) lue sur cette image. Rien à
     * faire si elle a déjà été traitée et n'a pas quitté l'objectif ; sinon,
     * [onConfirmed] à la deuxième lecture identique de suite.
     */
    private fun handle(ids: Set<String>, onConfirmed: () -> Unit) {
        if (ids.any { it in inViewIds }) {
            framesWithoutInView = 0 // toujours la même carte devant l'objectif
            return
        }
        val key = ids.sorted().joinToString(",")
        if (key != pendingKey) {
            pendingKey = key // première lecture : on attend la confirmation
            return
        }
        pendingKey = null
        inViewIds = ids
        framesWithoutInView = 0
        onConfirmed()
    }

    private fun noCard() {
        pendingKey = null
        if (inViewIds.isNotEmpty() && ++framesWithoutInView >= FRAMES_TO_LEAVE) inViewIds = emptySet()
    }

    /**
     * Compare les photos de la carte (voir SHOTS_BEFORE et SHOTS_MAX) aux images
     * TCGdex de [cards], téléchargées si besoin, en essayant pour chaque photo
     * plusieurs découpages (voir Placement). Une carte nettement plus
     * ressemblante que les autres va au panier ; sinon, on propose de choisir.
     */
    private fun compareImages(cards: List<CardBrief>, clue: String) {
        comparing = true
        status = "$clue. Comparaison des images (${cards.size} ${if (cards.size == 1) "carte" else "cartes"})…"
        // Les photos d'avant la reconnaissance, puis celles qui arrivent pendant
        // la comparaison (onFrame les ajoute), carte en principe posée.
        val shots = recentShots.toMutableList()
        comparisonShots = shots
        viewModelScope.launch {
            val ranked: List<Ranked> = try {
                val references = coroutineScope {
                    val downloads = async { ReferenceHashes.of(appContext, cards) }
                    delay(SETTLE_MILLIS)
                    downloads.await()
                }
                comparisonShots = null // on arrête de collecter des photos
                val collected = shots.toList()
                // 225 découpages par photo : calculés en arrière-plan.
                val variants = withContext(Dispatchers.Default) { collected.flatMap { Placement.variantsOf(it) } }
                cards.map { card ->
                    val reference = references[card.id]
                    val best = if (reference == null) null else variants.minByOrNull { CardImageHash.distance(it.hash, reference) }
                    if (reference == null || best == null) {
                        Ranked(card, null, null) // pas d'image chez TCGdex, ou pas de photo : impossible à comparer
                    } else {
                        Ranked(card, CardImageHash.distance(best.hash, reference), best)
                    }
                }.sortedBy { it.distance ?: Int.MAX_VALUE }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cards.map { Ranked(it, null, null) } // comparaison impossible : on laisse choisir
            } finally {
                comparisonShots = null
            }
            comparing = false
            lastComparison = buildString {
                append(
                    ranked.take(5).joinToString("\n") { candidate ->
                        "${candidate.card.name} (${setNameOf(candidate.card) ?: candidate.card.id}) : ${candidate.distance ?: "?"}"
                    },
                )
                append("\n— Photos comparées : ${shots.size}")
                ranked.firstOrNull()?.variant?.let { append("\n— Meilleur découpage de la première : ").append(describe(it)) }
            }
            // La vignette : le découpage le plus ressemblant à la première carte du classement.
            val shown = ranked.firstOrNull()?.variant?.let { it.shot.crop(it.area, 126, 176) } ?: shots.lastOrNull()
            lastThumbnail = shown?.toBitmap()?.asImageBitmap()
            val winner = winnerOf(ranked)
            if (winner != null) {
                putInBasket(winner)
            } else {
                choice = ranked.map { it.card }
                status = "Plusieurs cartes possibles : choisissez la bonne."
            }
        }
    }

    /** « taille × 1,05, décalage +5 % / −5 % » : le découpage retenu, pour l'encadré de test. */
    private fun describe(variant: Placement.Variant): String {
        fun percent(value: Float): String {
            val rounded = (value * 100).roundToInt()
            return if (rounded > 0) "+$rounded %" else "$rounded %"
        }
        return "taille × ${"%.2f".format(variant.scale)}, décalage ${percent(variant.offsetX)} (horizontal) / ${percent(variant.offsetY)} (vertical)"
    }

    /** La carte retenue d'office, ou null s'il faut laisser choisir (voir MAX_DISTANCE et MIN_GAP). */
    private fun winnerOf(ranked: List<Ranked>): CardBrief? {
        val best = ranked.firstOrNull() ?: return null
        val bestDistance = best.distance ?: return null
        if (bestDistance > MAX_DISTANCE) return null
        val secondDistance = ranked.getOrNull(1)?.distance
        if (secondDistance != null && secondDistance - bestDistance < MIN_GAP) return null
        return best.card
    }

    private fun testTextOf(frame: ScanFrame): String = buildString {
        append(frame.lines.joinToString("\n") { it.text }.ifEmpty { "(aucun texte lu dans le cadre)" })
        if (frame.ignoredLines > 0) append("\n— Lignes hors du cadre, ignorées : ${frame.ignoredLines}")
        lastComparison?.let { append("\n— Dernière comparaison d'images (distance sur 64, 0 = identique) :\n").append(it) }
    }

    /** Fige l'encadré de test, ou le remet à jour. La lecture des cartes, elle, continue. */
    fun toggleTestTextFrozen() {
        testTextFrozen = !testTextFrozen
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
        status = defaultStatus()
    }

    /** Le message d'attente, avec l'extension choisie s'il y en a une. */
    private fun defaultStatus(): String =
        selectedSet?.let { "Placez une carte de « ${it.name} » dans le cadre." } ?: DEFAULT_STATUS

    /**
     * Ne chercher que parmi les cartes de [set] (null : toutes les extensions).
     * La liste des cartes de l'extension est lue chez TCGdex (une requête la
     * première fois, puis le cache).
     */
    fun selectSet(set: CardSetBrief?) {
        setLoading?.cancel()
        selectedSet = set
        selectedCardIds = null
        pendingKey = null
        inViewIds = emptySet()
        if (set == null) {
            status = defaultStatus()
            return
        }
        status = "Préparation de « ${set.name} »…"
        setLoading = viewModelScope.launch {
            try {
                selectedCardIds = Tcgdex.set(set.id).cards.map { it.id }.toSet()
                status = defaultStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                selectedSet = null
                status = "Impossible de charger « ${set.name} ». Vérifiez la connexion à Internet."
            }
        }
    }

    /** Le nom de l'extension d'une carte (pour départager des cartes), si connu. */
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
