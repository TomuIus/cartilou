package io.github.tomuius.cartilou.data.scan

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.cardImageUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Les empreintes (voir CardImageHash) des images TCGdex des cartes candidates.
 *
 * Les images sont téléchargées avec Coil, comme toutes celles de l'appli :
 * elles passent par le même cache sur le téléphone, donc une carte déjà
 * affichée (dans le catalogue ou dans le choix entre cartes) n'est pas
 * retéléchargée. Les empreintes calculées restent en mémoire tant que l'appli
 * tourne.
 */
object ReferenceHashes {

    /** Empreinte de chaque carte déjà calculée, d'après son identifiant (fil principal seulement). */
    private val known = mutableMapOf<String, Long>()

    /**
     * Les empreintes de [cards], téléchargées et calculées si besoin, plusieurs
     * à la fois. Une carte sans image chez TCGdex (ou dont l'image ne se charge
     * pas) est absente du résultat.
     */
    suspend fun of(context: Context, cards: List<CardBrief>): Map<String, Long> {
        val missing = cards.filter { it.id !in known }
        coroutineScope {
            missing.map { card -> async { card.id to hashOf(context, card) } }.awaitAll()
        }.forEach { (id, hash) -> if (hash != null) known[id] = hash }
        return cards.mapNotNull { card -> known[card.id]?.let { card.id to it } }.toMap()
    }

    private suspend fun hashOf(context: Context, card: CardBrief): Long? {
        // Mêmes adresses que l'affichage : petite image en webp, puis en png
        // (certaines images françaises n'existent qu'en png).
        for (url in cardImageUrls(card.image, "low")) {
            val bitmap = load(context, url) ?: continue
            return withContext(Dispatchers.Default) { CardImageHash.ofBitmap(bitmap) }
        }
        return null
    }

    private suspend fun load(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            // Image en mémoire ordinaire (et non « matérielle ») : ses pixels
            // doivent pouvoir être lus pour calculer l'empreinte.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        return (result as? SuccessResult)?.image?.toBitmap()
    }
}
