package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.data.tcgdex.assetUrls

// Logos des séries et des extensions, communs au Catalogue et à Ma collection.
// Sans logo (absent chez TCGdex, ou aucune adresse ne se charge), on affiche
// à la place les initiales du nom dans une pastille (décision du 26/11/2026).

/** Le logo d'une série, sinon les initiales de son nom. */
@Composable
fun SerieLogo(logo: String?, name: String) {
    FallbackImage(
        urls = assetUrls(logo),
        contentDescription = null, // purement décoratif : le nom est écrit à côté
        modifier = Modifier.size(width = 72.dp, height = 40.dp),
        onAllFailed = { InitialsBadge(initials(name)) },
    )
}

/**
 * Le logo de l'extension ; s'il ne se charge pas (ni en français, ni en
 * anglais), son symbole ; sinon les initiales de son nom.
 */
@Composable
fun SetLogo(set: CardSetBrief) {
    FallbackImage(
        urls = assetUrls(set.logo) + assetUrls(set.symbol),
        contentDescription = null,
        modifier = Modifier.size(width = 72.dp, height = 40.dp),
        onAllFailed = { InitialsBadge(initials(set.name)) },
    )
}

/** La pastille aux initiales, de la taille d'un logo, sur le fond teinté des pastilles. */
@Composable
private fun InitialsBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Petits mots ignorés dans les initiales. */
private val SMALL_WORDS = setOf(
    "de", "du", "des", "la", "le", "les", "l", "d", "et", "à", "a", "au", "aux", "en",
    "of", "the", "and",
)

/** Au plus 5 caractères, pour tenir dans la pastille (à revoir après des tests). */
private const val MAX_INITIALS = 5

/**
 * Les initiales d'un nom : la première lettre de chaque mot, sans les petits
 * mots (« de », « et »…). Les sigles et nombres courts (« XY », « EX », « 151 »)
 * sont gardés en entier. Exemples : « Destinées de Paldea » → « DP »,
 * « L'Éveil des Légendes » → « ÉL », « Écarlate et Violet – 151 » → « ÉV151 ».
 */
internal fun initials(name: String): String {
    // Découpe aux espaces, tirets, apostrophes, « & » et ponctuation.
    val words = name.split(Regex("[\\s\\-–—&'’:.,()/]+")).filter { it.isNotBlank() }
    val result = StringBuilder()
    for (word in words) {
        if (word.lowercase() in SMALL_WORDS) continue
        val isAcronym = word.length <= 3 && word.all { it.isUpperCase() || it.isDigit() }
        val part = if (isAcronym) word else word.first().uppercase()
        if (result.length + part.length > MAX_INITIALS) break
        result.append(part)
    }
    return result.toString().ifEmpty { name.take(2).uppercase() }
}
