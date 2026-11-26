package io.github.tomuius.cartilou.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.tomuius.cartilou.data.supabase.OwnedCard

// Petits morceaux communs aux écrans de Ma collection.

/** Nombre de cartes différentes (toutes versions confondues) par série ou par extension. */
internal fun distinctCardCounts(owned: List<OwnedCard>, groupKey: (OwnedCard) -> String): Map<String, Int> =
    owned.groupBy(groupKey).mapValues { (_, lines) -> lines.distinctBy { it.cardId }.size }

/** « 1 carte », « 12 cartes ». */
internal fun cardsText(count: Int): String = if (count == 1) "1 carte" else "$count cartes"

/**
 * L'avancement d'une extension : « 12 / 201 cartes », secrètes comprises
 * dans le total (décision du 26/11/2026) ; « 12 cartes » si TCGdex ne donne
 * pas le total.
 */
internal fun progressText(owned: Int, total: Int?): String =
    if (total != null) "$owned / $total cartes" else cardsText(owned)

/** Contenu centré dans l'écran (messages, roue de chargement). */
@Composable
internal fun CenteredContent(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Un message centré, en gris. */
@Composable
internal fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    CenteredContent(modifier) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Affiché dans les écrans d'une série ou d'une extension si la collection n'est plus là (déconnexion). */
@Composable
internal fun CollectionUnavailable(modifier: Modifier = Modifier) {
    CenteredMessage("Collection indisponible : connectez-vous dans l'onglet Compte.", modifier)
}
