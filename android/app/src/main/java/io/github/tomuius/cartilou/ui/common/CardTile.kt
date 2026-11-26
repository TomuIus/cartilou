package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tomuius.cartilou.data.tcgdex.CardBrief

/** Transparence d'une carte manquante dans Ma collection (à revoir après des tests). */
private const val DIMMED_ALPHA = 0.35f

/**
 * Une carte de grille : son image (petite qualité), puis son numéro et son nom.
 * Commune au Catalogue et à Ma collection.
 *
 * @param badge petit texte en haut à droite de l'image (par exemple « ×2 »), ou null.
 * @param dimmed vrai pour une carte manquante : affichée pâle, comme un
 *   emplacement vide de classeur.
 */
@Composable
fun CardTile(
    card: CardBrief,
    onClick: () -> Unit,
    badge: String? = null,
    dimmed: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .alpha(if (dimmed) DIMMED_ALPHA else 1f),
    ) {
        // Box : superpose le badge sur l'image.
        Box {
            CardImage(card.image, card.name, quality = "low", modifier = Modifier.fillMaxWidth())
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            text = "${card.localId} · ${card.name}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
