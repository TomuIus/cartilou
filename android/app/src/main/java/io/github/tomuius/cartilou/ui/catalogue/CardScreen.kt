package io.github.tomuius.cartilou.ui.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.tcgdex.Card
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CardImage
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel

/** Charge la fiche complète d'une carte. */
class CardViewModel(cardId: String) : LoadViewModel<Card>({ Tcgdex.card(cardId) })

/**
 * Quatrième écran : la fiche d'une carte. Grande image, nom, la partie
 * « Ma collection » (voir OwnedVersions.kt), puis les informations utiles
 * à la collection.
 *
 * [onSignIn] : ouvre l'onglet Compte (bouton affiché si l'on n'est pas connecté).
 */
@Composable
fun CardScreen(cardId: String, cardName: String, onBack: () -> Unit, onSignIn: () -> Unit) {
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
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Juste sous le nom : c'est ce qu'on touche le plus souvent en saisissant.
                OwnedVersionsSection(card, onSignIn = onSignIn)
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
        DetailRow("Numéro", cardNumber(card))
        DetailRow("Extension", card.set.name)
        DetailRow("Rareté", card.rarity)
        DetailRow("Catégorie", card.category)
        DetailRow("Illustrateur", card.illustrator)
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

/** Le numéro tel qu'imprimé sur la carte, par exemple « 136/189 » (ou « 136 » si le total est inconnu). */
private fun cardNumber(card: Card): String {
    val official = card.set.cardCount?.official
    return if (official != null) "${card.localId}/$official" else card.localId
}
