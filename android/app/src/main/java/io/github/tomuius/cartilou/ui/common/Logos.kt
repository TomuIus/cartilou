package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.data.tcgdex.assetUrls

// Logos des séries et des extensions, communs au Catalogue et à Ma collection.

/** Le logo d'une série, ou un espace vide de même taille s'il n'y en a pas. */
@Composable
fun SerieLogo(logo: String?) {
    FallbackImage(
        urls = assetUrls(logo),
        contentDescription = null, // purement décoratif : le nom est écrit à côté
        modifier = Modifier.size(width = 72.dp, height = 40.dp),
    )
}

/**
 * Le logo de l'extension ; s'il ne se charge pas (ni en français, ni en
 * anglais), son symbole ; sinon un espace vide de même taille.
 */
@Composable
fun SetLogo(set: CardSetBrief) {
    FallbackImage(
        urls = assetUrls(set.logo) + assetUrls(set.symbol),
        contentDescription = null,
        modifier = Modifier.size(width = 72.dp, height = 40.dp),
    )
}
