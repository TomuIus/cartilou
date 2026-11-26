package io.github.tomuius.cartilou.data.supabase

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.tomuius.cartilou.data.tcgdex.Card
import io.github.tomuius.cartilou.data.tcgdex.CardVariants
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * Les versions d'une carte que l'on peut posséder.
 * [dbValue] : la valeur enregistrée dans la colonne « version » (voir le
 * fichier SQL, qui n'accepte que ces quatre valeurs).
 */
enum class CardVersion(val dbValue: String, val label: String) {
    Normal("normale", "Normale"),
    Reverse("reverse", "Reverse"),
    Holo("holo", "Holo"),
    FirstEdition("premiere_edition", "1re édition");

    companion object {
        fun fromDb(value: String): CardVersion? = entries.firstOrNull { it.dbValue == value }
    }
}

/**
 * Les versions que TCGdex indique pour une carte, dans un ordre fixe.
 * Liste parfois incomplète (des reverse manquent, constaté en test).
 */
fun tcgdexVersions(variants: CardVariants?): List<CardVersion> {
    if (variants == null) return emptyList()
    return buildList {
        if (variants.normal) add(CardVersion.Normal)
        if (variants.reverse) add(CardVersion.Reverse)
        if (variants.holo) add(CardVersion.Holo)
        if (variants.firstEdition) add(CardVersion.FirstEdition)
    }
}

// Les lignes de la table telles qu'échangées avec Supabase. @SerialName :
// le nom de la colonne, quand il diffère du nom Kotlin.

/** Ce qu'on lit pour une fiche : la version et le nombre d'exemplaires. */
@Serializable
internal data class VersionQuantityRow(
    val version: String,
    @SerialName("quantite") val quantity: Int,
)

/** Ce qu'on lit pour toute la collection : tout sauf l'utilisateur et les dates. */
@Serializable
internal data class CollectionRow(
    @SerialName("carte_id") val cardId: String,
    val version: String,
    @SerialName("quantite") val quantity: Int,
    @SerialName("extension_id") val setId: String,
    @SerialName("serie_id") val serieId: String,
)

/** Une ligne complète, pour l'ajout ou la mise à jour. */
@Serializable
internal data class OwnedCardRow(
    @SerialName("utilisateur_id") val userId: String,
    @SerialName("carte_id") val cardId: String,
    val version: String,
    @SerialName("quantite") val quantity: Int,
    @SerialName("extension_id") val setId: String,
    @SerialName("serie_id") val serieId: String,
)

/** Une version d'une carte possédée, telle que gardée en mémoire pour Ma collection. */
data class OwnedCard(
    val cardId: String,
    val version: CardVersion,
    val quantity: Int,
    val setId: String,
    val serieId: String,
)

/**
 * Les cartes possédées, dans la table « cartilou.cartes_possedees »
 * (le schéma « cartilou » est indiqué une fois pour toutes dans Supabase.kt).
 *
 * Les règles de sécurité de la table limitent déjà chacun à ses propres
 * lignes ; on filtre quand même sur « utilisateur_id », pour que chaque
 * requête dise clairement ce qu'elle vise.
 */
object OwnedCards {

    private val table get() = Supabase.client.from("cartes_possedees")

    /**
     * Les enregistrements tournent ici, et non dans l'écran : si l'on quitte
     * la fiche juste après un appui sur « + », l'enregistrement continue
     * (dans l'écran, il serait annulé avec lui).
     */
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Un seul enregistrement à la fois, dans l'ordre des appuis. */
    private val writeLock = Mutex()

    /** Série de chaque extension déjà rencontrée (lue chez TCGdex une seule fois). */
    private val serieBySet = ConcurrentHashMap<String, String>()

    /**
     * Supabase renvoie au plus 1 000 lignes par requête (réglage par défaut
     * des projets, d'après sa documentation) : la collection est lue par
     * paquets de cette taille.
     */
    private const val PAGE_SIZE = 1000L

    private val _collection = MutableStateFlow<List<OwnedCard>?>(null)

    /**
     * Toute la collection, gardée en mémoire pour l'onglet Ma collection :
     * null tant qu'elle n'est pas lue (ou après une déconnexion). Chaque
     * enregistrement réussi la met à jour : Ma collection reste juste sans
     * relire Supabase.
     */
    val collection: StateFlow<List<OwnedCard>?> = _collection.asStateFlow()

    /** Lit toute la collection dans Supabase (par paquets de [PAGE_SIZE] lignes). */
    suspend fun loadCollection() {
        withFrenchErrors {
            val userId = requireUserId()
            val all = mutableListOf<OwnedCard>()
            var from = 0L
            while (true) {
                val page = table.select(
                    Columns.list("carte_id", "version", "quantite", "extension_id", "serie_id"),
                ) {
                    filter { eq("utilisateur_id", userId) }
                    // Un ordre fixe, pour que les paquets se suivent sans trou ni doublon.
                    order("carte_id", Order.ASCENDING)
                    order("version", Order.ASCENDING)
                    // Lignes « from » à « from + 999 », bornes comprises.
                    range(from until from + PAGE_SIZE)
                }.decodeList<CollectionRow>()
                page.mapNotNullTo(all) { row ->
                    CardVersion.fromDb(row.version)?.let { version ->
                        OwnedCard(row.cardId, version, row.quantity, row.setId, row.serieId)
                    }
                }
                if (page.size < PAGE_SIZE) break // dernier paquet
                from += PAGE_SIZE
            }
            _collection.value = all
        }
    }

    /** Oublie la collection gardée en mémoire (à la déconnexion). */
    fun forgetCollection() {
        _collection.value = null
    }

    /** Le nombre d'exemplaires possédés de chaque version d'une carte (versions absentes = 0). */
    suspend fun quantities(cardId: String): Map<CardVersion, Int> = withFrenchErrors {
        val userId = requireUserId()
        table.select(Columns.list("version", "quantite")) {
            filter {
                eq("utilisateur_id", userId)
                eq("carte_id", cardId)
            }
        }.decodeList<VersionQuantityRow>()
            .mapNotNull { row -> CardVersion.fromDb(row.version)?.let { it to row.quantity } }
            .toMap()
    }

    /**
     * Enregistre le nombre d'exemplaires d'une version.
     * À 0, la ligne est supprimée (une ligne n'existe que si l'on possède la carte).
     * Sinon, « upsert » : ajoute la ligne si elle n'existe pas, la met à jour sinon.
     */
    suspend fun setQuantity(card: Card, version: CardVersion, quantity: Int) {
        withFrenchErrors {
            val userId = requireUserId()
            if (quantity <= 0) {
                table.delete {
                    filter {
                        eq("utilisateur_id", userId)
                        eq("carte_id", card.id)
                        eq("version", version.dbValue)
                    }
                }
                updateCollection(card.id, version, newLine = null)
            } else {
                val serieId = serieIdOf(card.set.id)
                val row = OwnedCardRow(
                    userId = userId,
                    cardId = card.id,
                    version = version.dbValue,
                    quantity = quantity,
                    setId = card.set.id,
                    serieId = serieId,
                )
                table.upsert(row) {
                    // Deux lignes sont « la même » si ces trois colonnes sont égales (la clé de la table).
                    onConflict = "utilisateur_id,carte_id,version"
                    // Colonnes absentes (dates) : valeur par défaut de la table, pas « vide ».
                    defaultToNull = false
                }
                updateCollection(card.id, version, OwnedCard(card.id, version, quantity, card.set.id, serieId))
            }
        }
    }

    /**
     * Reporte un enregistrement réussi dans la collection gardée en mémoire
     * (si elle a été lue) : remplace la ligne de cette carte et version, ou
     * la retire si [newLine] est null.
     */
    private fun updateCollection(cardId: String, version: CardVersion, newLine: OwnedCard?) {
        _collection.update { list ->
            list?.filterNot { it.cardId == cardId && it.version == version }
                ?.let { rest -> if (newLine != null) rest + newLine else rest }
        }
    }

    /** Lance un enregistrement, après ceux déjà en cours. */
    fun launchWrite(write: suspend () -> Unit): Job = writes.launch {
        writeLock.withLock { write() }
    }

    private fun requireUserId(): String = Account.currentUserId()
        ?: throw DisplayableException("Vous n'êtes plus connecté. Reconnectez-vous dans l'onglet Compte.")

    /** La série d'une extension : une carte TCGdex ne la donne pas, son extension si. */
    private suspend fun serieIdOf(setId: String): String {
        serieBySet[setId]?.let { return it }
        val serieId = Tcgdex.set(setId).serie?.id
            ?: throw DisplayableException("TCGdex n'indique pas la série de cette extension : la carte ne peut pas être enregistrée.")
        serieBySet[setId] = serieId
        return serieId
    }
}
