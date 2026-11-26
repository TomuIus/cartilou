package io.github.tomuius.cartilou.ui.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.CardSet
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CardTile
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import io.github.tomuius.cartilou.ui.common.QuickEntryBar
import io.github.tomuius.cartilou.ui.common.QuickEntryState
import io.github.tomuius.cartilou.ui.common.QuickEntryViewModel
import io.github.tomuius.cartilou.ui.common.ownedCopies
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Charge une extension et la liste de ses cartes (dans l'ordre des numéros, tel que renvoyé par TCGdex). */
class SetViewModel(setId: String) : LoadViewModel<CardSet>({ Tcgdex.set(setId) })

/**
 * Troisième écran : les cartes d'une extension, en grille. Un appui ouvre la
 * fiche de la carte, sauf en saisie rapide (voir ui/common/QuickEntry.kt) :
 * un appui ajoute alors un exemplaire, un appui long en retire un.
 *
 * [onSignIn] : ouvre l'onglet Compte (saisie rapide sans être connecté).
 */
@Composable
fun SetScreen(
    setId: String,
    setName: String,
    onBack: () -> Unit,
    onOpenCard: (CardBrief) -> Unit,
    onSignIn: () -> Unit,
) {
    // Créé avant « val viewModel » ci-dessous, qui sinon masquerait la fonction viewModel { … }.
    val quick = viewModel { QuickEntryViewModel(setId) }
    val viewModel = viewModel { SetViewModel(setId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // La collection en mémoire : lue au lancement de la saisie rapide si besoin.
    val owned by OwnedCards.collection.collectAsStateWithLifecycle()
    // Les lignes de la collection pour chaque carte de l'extension.
    val linesByCard = remember(owned, setId) {
        owned.orEmpty().filter { it.setId == setId }.groupBy { it.cardId }
    }

    Scaffold(topBar = { CartilouTopBar(setName, onBack = onBack) }) { innerPadding ->
        LoadStateContent(state, onRetry = viewModel::reload, modifier = Modifier.padding(innerPadding)) { set ->
            // Grille « adaptative » : autant de colonnes que possible, d'au moins 104 dp de large.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // En-tête sur toute la largeur : nombre de cartes, date de sortie, saisie rapide.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = setSummary(set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        QuickEntryBar(quick, onSignIn = onSignIn)
                    }
                }
                items(set.cards, key = { it.id }) { card ->
                    if (quick.state == QuickEntryState.On) {
                        // Saisie rapide : nombre d'exemplaires de la version choisie
                        // (toutes versions pour « Proposée ») ; pâle s'il n'y en a aucun.
                        val copies = ownedCopies(linesByCard[card.id], quick.selected.version)
                        CardTile(
                            card = card,
                            onClick = { quick.change(card, +1) },
                            onLongClick = { quick.change(card, -1) },
                            badge = if (copies > 0) "×$copies" else null,
                            dimmed = copies == 0,
                        )
                    } else {
                        CardTile(card, onClick = { onOpenCard(card) })
                    }
                }
            }
        }
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
