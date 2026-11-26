package io.github.tomuius.cartilou.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import io.github.tomuius.cartilou.ui.common.SetLogo

/** Les extensions d'une série chez TCGdex, les plus récentes en haut (comme dans le Catalogue). */
class OwnedSetsViewModel(serieId: String) :
    LoadViewModel<List<CardSetBrief>>({ Tcgdex.serie(serieId).sets.asReversed() })

/**
 * Ma collection, deuxième écran : les extensions d'une série dont vous
 * possédez au moins une carte, avec l'avancement (« 12 / 201 cartes »).
 * Un appui ouvre l'extension.
 */
@Composable
fun CollectionSerieScreen(
    serieId: String,
    serieName: String,
    onBack: () -> Unit,
    onOpenSet: (CardSetBrief) -> Unit,
) {
    val viewModel = viewModel { OwnedSetsViewModel(serieId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owned = OwnedCards.collection.collectAsStateWithLifecycle().value

    Scaffold(topBar = { CartilouTopBar(serieName, onBack = onBack) }) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        if (owned == null) {
            CollectionUnavailable(modifier)
        } else {
            val countBySet = remember(owned, serieId) {
                distinctCardCounts(owned.filter { it.serieId == serieId }) { it.setId }
            }
            LoadStateContent(state, onRetry = viewModel::reload, modifier = modifier) { sets ->
                val ownedSets = sets.filter { it.id in countBySet }
                if (ownedSets.isEmpty()) {
                    CenteredMessage("Plus aucune carte de cette série dans votre collection.")
                } else {
                    LazyColumn {
                        items(ownedSets, key = { it.id }) { set ->
                            ListItem(
                                headlineContent = { Text(set.name) },
                                supportingContent = {
                                    Text(progressText(countBySet.getValue(set.id), set.cardCount?.total))
                                },
                                leadingContent = { SetLogo(set) },
                                modifier = Modifier.clickable { onOpenSet(set) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
