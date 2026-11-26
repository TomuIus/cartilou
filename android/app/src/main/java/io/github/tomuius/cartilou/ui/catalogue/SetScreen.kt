package io.github.tomuius.cartilou.ui.catalogue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.CardSet
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CardImage
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Charge une extension et la liste de ses cartes (dans l'ordre des numéros, tel que renvoyé par TCGdex). */
class SetViewModel(setId: String) : LoadViewModel<CardSet>({ Tcgdex.set(setId) })

/** Troisième écran : les cartes d'une extension, en grille. Un appui ouvre la fiche de la carte. */
@Composable
fun SetScreen(
    setId: String,
    setName: String,
    onBack: () -> Unit,
    onOpenCard: (CardBrief) -> Unit,
) {
    val viewModel = viewModel { SetViewModel(setId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { CartilouTopBar(setName, onBack = onBack) }) { innerPadding ->
        LoadStateContent(state, onRetry = viewModel::reload, modifier = Modifier.padding(innerPadding)) { set ->
            // Grille « adaptative » : autant de colonnes que possible, d'au moins 104 dp de large.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // En-tête sur toute la largeur : nombre de cartes et date de sortie.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = setSummary(set),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(set.cards, key = { it.id }) { card ->
                    CardTile(card, onClick = { onOpenCard(card) })
                }
            }
        }
    }
}

/** Une carte de la grille : son image (petite qualité), puis son numéro et son nom. */
@Composable
private fun CardTile(card: CardBrief, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        CardImage(card.image, card.name, quality = "low", modifier = Modifier.fillMaxWidth())
        Text(
            text = "${card.localId} · ${card.name}",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private val frenchDate = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

/** Par exemple « 201 cartes · sortie le 14 août 2020 ». */
private fun setSummary(set: CardSet): String {
    val count = "${set.cards.size} cartes"
    val date = set.releaseDate
        ?.let { runCatching { LocalDate.parse(it).format(frenchDate) }.getOrNull() }
    return if (date != null) "$count · sortie le $date" else count
}
