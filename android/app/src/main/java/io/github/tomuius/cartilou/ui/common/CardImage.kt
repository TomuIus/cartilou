package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tomuius.cartilou.data.tcgdex.cardImageUrls

/** Rapport largeur / hauteur des images de cartes TCGdex (245 × 337, comme 600 × 825). */
const val CARD_RATIO = 245f / 337f

/**
 * L'image d'une carte, aux proportions d'une carte, utilisée dans la grille
 * (qualité « low ») et sur la fiche (qualité « high »).
 *
 * Trois cas : l'image s'affiche (webp, ou png en secours) ; TCGdex ne donne
 * aucune image (cadre « pas d'image ») ; l'image ne se charge pas (cadre
 * « image introuvable »).
 */
@Composable
fun CardImage(image: String?, name: String, quality: String, modifier: Modifier = Modifier) {
    val cardModifier = modifier
        .aspectRatio(CARD_RATIO)
        .clip(RoundedCornerShape(6.dp))
    if (image == null) {
        MissingCardImage(name, reason = "pas d'image", modifier = cardModifier)
    } else {
        FallbackImage(
            urls = cardImageUrls(image, quality),
            contentDescription = name,
            modifier = cardModifier,
            onAllFailed = {
                MissingCardImage(name, reason = "image introuvable", modifier = Modifier.fillMaxSize())
            },
        )
    }
}

/** Cadre gris à la place d'une image de carte, avec le nom et la raison. */
@Composable
private fun MissingCardImage(name: String, reason: String, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$name\n($reason)",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp),
        )
    }
}
