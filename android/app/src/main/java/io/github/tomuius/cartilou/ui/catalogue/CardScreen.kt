package io.github.tomuius.cartilou.ui.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.tcgdex.Card
import io.github.tomuius.cartilou.data.tcgdex.CardVariants
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CardImage
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel

/** Charge la fiche complète d'une carte. */
class CardViewModel(cardId: String) : LoadViewModel<Card>({ Tcgdex.card(cardId) })

/**
 * Quatrième écran : la fiche d'une carte. Grande image, informations utiles à
 * la collection, et versions existantes. C'est ici que viendra plus tard
 * l'ajout à la collection.
 */
@Composable
fun CardScreen(cardId: String, cardName: String, onBack: () -> Unit) {
    val viewModel = viewModel { CardViewModel(cardId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { CartilouTopBar(cardName, onBack = onBack) }) { innerPadding ->
        LoadStateContent(state, onRetry = viewModel::reload, modifier = Modifier.padding(innerPadding)) { card ->
            // verticalScroll : la fiche défile si elle dépasse la hauteur de l'écran.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Grande image (600 × 825) : toute la largeur, sans dépasser 360 dp
                // sur les écrans larges (l'ordre compte : la limite d'abord).
                CardImage(
                    image = card.image,
                    name = card.name,
                    quality = "high",
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(),
                )
                CardDetails(card)
            }
        }
    }
}

/** Les informations de la carte, une par ligne ; les lignes sans donnée sont omises. */
@Composable
private fun CardDetails(card: Card) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(card.name, style = MaterialTheme.typography.headlineSmall)
        DetailRow("Numéro", cardNumber(card))
        DetailRow("Extension", card.set.name)
        DetailRow("Rareté", card.rarity)
        DetailRow("Catégorie", card.category)
        DetailRow("Illustrateur", card.illustrator)

        // « Connues de TCGdex » : sa liste est parfois incomplète (des reverse
        // manquent pour des cartes Noir & Blanc, XY, Soleil & Lune, constaté en test).
        Text(
            text = "Versions connues de TCGdex",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        val versions = variantLabels(card.variants)
        if (versions.isEmpty()) {
            Text("Non renseignées", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                versions.forEach { VersionBadge(it) }
            }
        }
    }
}

/** Une ligne « Étiquette : valeur », ou rien si la valeur est absente. */
@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            text = "$label : ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Une pastille avec le nom d'une version (par exemple « Reverse »). */
@Composable
private fun VersionBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Le numéro tel qu'imprimé sur la carte, par exemple « 136/189 » (ou « 136 » si le total est inconnu). */
private fun cardNumber(card: Card): String {
    val official = card.set.cardCount?.official
    return if (official != null) "${card.localId}/$official" else card.localId
}

/** Les noms des versions existantes, dans un ordre fixe. */
private fun variantLabels(variants: CardVariants?): List<String> {
    if (variants == null) return emptyList()
    return buildList {
        if (variants.normal) add("Normale")
        if (variants.reverse) add("Reverse")
        if (variants.holo) add("Holo")
        if (variants.firstEdition) add("1re édition")
    }
}
