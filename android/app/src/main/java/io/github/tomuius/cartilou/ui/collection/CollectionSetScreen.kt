package io.github.tomuius.cartilou.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

/** Le filtre de la grille. « Toutes » par défaut : la vue classeur. */
enum class CardFilter(val label: String) {
    All("Toutes"),
    Owned("Possédées"),
    Missing("Manquantes"),
}

/** Une extension et toutes ses cartes chez TCGdex. */
class OwnedSetViewModel(setId: String) : LoadViewModel<CardSet>({ Tcgdex.set(setId) })

/**
 * Ma collection, troisième écran : les cartes d'une extension, en grille.
 * Vos cartes sont en couleur avec le nombre d'exemplaires (« ×2 ») ; les
 * manquantes, pâles. Le filtre en haut choisit lesquelles afficher.
 * Un appui ouvre la fiche de la carte (où l'on ajoute ou retire), sauf en
 * saisie rapide (voir ui/common/QuickEntry.kt) : un appui ajoute alors un
 * exemplaire, un appui long en retire un.
 *
 * [onSignIn] : ouvre l'onglet Compte (saisie rapide sans être connecté).
 */
@Composable
fun CollectionSetScreen(
    setId: String,
    setName: String,
    onBack: () -> Unit,
    onOpenCard: (CardBrief) -> Unit,
    onSignIn: () -> Unit,
) {
    // Créé avant « val viewModel » ci-dessous, qui sinon masquerait la fonction viewModel { … }.
    val quick = viewModel { QuickEntryViewModel(setId) }
    val viewModel = viewModel { OwnedSetViewModel(setId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owned = OwnedCards.collection.collectAsStateWithLifecycle().value
    // rememberSaveable : le filtre choisi survit à une rotation de l'écran.
    var filter by rememberSaveable { mutableStateOf(CardFilter.All) }

    Scaffold(topBar = { CartilouTopBar(setName, onBack = onBack) }) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        if (owned == null) {
            CollectionUnavailable(modifier)
        } else {
            // Les lignes de la collection pour chaque carte possédée de l'extension.
            val linesByCard = remember(owned, setId) {
                owned.filter { it.setId == setId }.groupBy { it.cardId }
            }
            LoadStateContent(state, onRetry = viewModel::reload, modifier = modifier) { set ->
                val shown = when (filter) {
                    CardFilter.All -> set.cards
                    CardFilter.Owned -> set.cards.filter { it.id in linesByCard }
                    CardFilter.Missing -> set.cards.filter { it.id !in linesByCard }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // En-tête sur toute la largeur : avancement, filtre et saisie rapide.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = progressText(linesByCard.size, set.cardCount?.total ?: set.cards.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilterRow(filter, onSelect = { filter = it })
                            QuickEntryBar(quick, onSignIn = onSignIn)
                        }
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = when (filter) {
                                    CardFilter.Missing -> "Aucune carte manquante : extension complète !"
                                    else -> "Aucune carte à afficher."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                    }
                    items(shown, key = { it.id }) { card ->
                        val lines = linesByCard[card.id]
                        if (quick.state == QuickEntryState.On) {
                            // Saisie rapide : nombre d'exemplaires de la version choisie
                            // (toutes versions pour « Proposée ») ; pâle s'il n'y en a aucun.
                            val copies = ownedCopies(lines, quick.selected.version)
                            CardTile(
                                card = card,
                                onClick = { quick.change(card, +1) },
                                onLongClick = { quick.change(card, -1) },
                                badge = if (copies > 0) "×$copies" else null,
                                dimmed = copies == 0,
                            )
                        } else {
                            val copies = ownedCopies(lines, version = null)
                            CardTile(
                                card = card,
                                onClick = { onOpenCard(card) },
                                badge = if (copies > 0) "×$copies" else null,
                                // Pâle seulement dans « Toutes » : dans « Manquantes », elles le sont toutes.
                                dimmed = copies == 0 && filter == CardFilter.All,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Les trois boutons du filtre, collés (« boutons segmentés » de Material 3).
 * @OptIn : ces boutons sont peut-être encore marqués « expérimentaux » dans
 * cette version de la bibliothèque ; l'annotation l'accepte explicitement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(selected: CardFilter, onSelect: (CardFilter) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        CardFilter.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = CardFilter.entries.size),
            ) {
                Text(option.label)
            }
        }
    }
}
