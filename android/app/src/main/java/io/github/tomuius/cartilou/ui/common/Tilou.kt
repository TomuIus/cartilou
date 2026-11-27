package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tomuius.cartilou.R

// Tilou dans l'appli (décision du 27/11/2026, comme dans Canapilou) : à la
// connexion, quand la collection est vide, et en logo dans la barre du haut
// de l'écran des séries. Images tirées de illustrations/tilou_cartes.png
// (à la racine du dépôt), fond retiré.
// Les images sont décoratives (contentDescription = null) : le nom de l'appli
// ou un texte est toujours écrit à côté, pour les lecteurs d'écran.

/** Proportions de drawable-nodpi/tilou_cartes.png (412 × 800 pixels). */
private const val TILOU_RATIO = 412f / 800f

/** Proportions de drawable-nodpi/tilou_logo.png (111 × 160 pixels : tête, oreilles et haut des cartes). */
private const val LOGO_RATIO = 111f / 160f

/** Tilou en entier, avec ses cartes, à la hauteur demandée. */
@Composable
fun TilouImage(height: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.tilou_cartes),
        contentDescription = null,
        modifier = modifier
            .height(height)
            .aspectRatio(TILOU_RATIO),
    )
}

/**
 * Le logo : tête de Tilou et nom de l'appli en framboise, comme dans la barre
 * du haut de Canapilou. Logo de 40 dp de haut : comme Canapilou (à revoir après des tests).
 */
@Composable
fun CartilouLogo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.tilou_logo),
            contentDescription = null,
            modifier = Modifier
                .height(40.dp)
                .aspectRatio(LOGO_RATIO),
        )
        Spacer(Modifier.width(8.dp))
        CartilouName(MaterialTheme.typography.titleLarge)
    }
}

/**
 * Le nom de l'appli en deux couleurs, comme le logo du site de Canapilou
 * (« Canap » en couleur de début de logo, « ilou » en framboise) :
 * « Cart » en couleur de début de logo (rôle « secondary » du thème :
 * #7A1043 en clair, #FFC9E0 en sombre), « ilou » en accent (« primary »).
 */
@Composable
fun CartilouName(style: TextStyle) {
    val start = MaterialTheme.colorScheme.secondary
    val end = MaterialTheme.colorScheme.primary
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = start)) { append("Cart") }
            withStyle(SpanStyle(color = end)) { append("ilou") }
        },
        style = style,
        fontWeight = FontWeight.Bold,
    )
}
