package io.github.tomuius.cartilou.data.scan

import android.util.Log
import io.github.tomuius.cartilou.data.supabase.CardVersion
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Une ligne du panier : une carte, une version, un nombre d'exemplaires.
 * [version] : valeur de la colonne « version » (« normale », « reverse »…),
 * ou null pour « Proposée » (la version proposée de la carte, déterminée à
 * l'enregistrement dans la collection).
 */
@Serializable
data class BasketItem(
    val cardId: String,
    val name: String,
    val localId: String,
    val image: String? = null,
    val version: String? = null,
    val quantity: Int = 1,
) {
    /** Deux lignes sont fusionnées si la carte et la version sont les mêmes. */
    val key: String get() = "$cardId|${version.orEmpty()}"

    val cardVersion: CardVersion? get() = version?.let { CardVersion.fromDb(it) }
}

/**
 * Le panier du scan (décision du 27/11/2026, option C) : les cartes
 * reconnues s'y accumulent sans être enregistrées ; on vérifie, puis on
 * ajoute tout à la collection d'un coup.
 *
 * Gardé dans un petit fichier JSON sur le téléphone (dossier privé de
 * l'appli) et réécrit à chaque changement : le panier survit à la fermeture
 * de l'appli. Préparé au démarrage par [init] (dans CartilouApplication).
 */
object ScanBasket {

    private val json = Json { ignoreUnknownKeys = true }
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileLock = Mutex()
    private lateinit var file: File

    private val _items = MutableStateFlow<List<BasketItem>>(emptyList())
    val items: StateFlow<List<BasketItem>> = _items.asStateFlow()

    /** À appeler une fois au démarrage, avec le dossier privé de l'appli (filesDir). */
    fun init(filesDir: File) {
        file = File(filesDir, "panier_scan.json")
        io.launch {
            val saved = runCatching { json.decodeFromString<List<BasketItem>>(file.readText()) }.getOrNull()
            if (saved != null) _items.value = saved
        }
    }

    /** Ajoute un exemplaire (ligne existante + 1, ou nouvelle ligne en haut). */
    fun add(card: CardBrief, version: CardVersion?) {
        val item = BasketItem(card.id, card.name, card.localId, card.image, version?.dbValue)
        change { list ->
            val existing = list.firstOrNull { it.key == item.key }
            if (existing != null) list.map { if (it.key == item.key) it.copy(quantity = it.quantity + 1) else it }
            else listOf(item) + list
        }
    }

    /** Change le nombre d'exemplaires d'une ligne ; à 0, la ligne disparaît. */
    fun setQuantity(item: BasketItem, quantity: Int) = change { list ->
        if (quantity <= 0) list.filterNot { it.key == item.key }
        else list.map { if (it.key == item.key) it.copy(quantity = quantity) else it }
    }

    /** Change la version d'une ligne (fusionnée avec une ligne identique s'il y en a une). */
    fun setVersion(item: BasketItem, version: CardVersion?) = change { list ->
        val changed = item.copy(version = version?.dbValue)
        val others = list.filterNot { it.key == item.key }
        val twin = others.firstOrNull { it.key == changed.key }
        if (twin != null) others.map { if (it.key == twin.key) it.copy(quantity = it.quantity + item.quantity) else it }
        else list.map { if (it.key == item.key) changed else it }
    }

    fun remove(item: BasketItem) = change { list -> list.filterNot { it.key == item.key } }

    fun clear() = change { emptyList() }

    private fun change(transform: (List<BasketItem>) -> List<BasketItem>) {
        _items.update(transform)
        save()
    }

    /** Réécrit le fichier (fichier temporaire puis renommage : jamais de fichier à moitié écrit). */
    private fun save() {
        val snapshot = _items.value
        io.launch {
            fileLock.withLock {
                runCatching {
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(json.encodeToString(snapshot))
                    if (!tmp.renameTo(file)) {
                        file.delete()
                        tmp.renameTo(file)
                    }
                }.onFailure { Log.w("Cartilou", "Panier non enregistré : $it") }
            }
        }
    }
}
