package io.github.tomuius.cartilou.ui.catalogue

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.tcgdex.CardCount
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import io.github.tomuius.cartilou.ui.common.SetLogo

/**
 * Charge les extensions d'une série, les plus récentes en haut (même
 * hypothèse d'ordre que pour les séries, à vérifier).
 */
class SerieViewModel(serieId: String) :
    LoadViewModel<List<CardSetBrief>>({ Tcgdex.serie(serieId).sets.asReversed() })

/** Deuxième écran : les extensions d'une série. Un appui ouvre l'extension. */
@Composable
fun SerieScreen(
    serieId: String,
    serieName: String,
    onBack: () -> Unit,
    onOpenSet: (CardSetBrief) -> Unit,
) {
    // Le ViewModel reçoit l'identifiant de la série à charger.
    val viewModel = viewModel { SerieViewModel(serieId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { CartilouTopBar(serieName, onBack = onBack) }) { innerPadding ->
        LoadStateContent(state, onRetry = viewModel::reload, modifier = Modifier.padding(innerPadding)) { sets ->
            LazyColumn {
                items(sets, key = { it.id }) { set ->
                    val countText = cardCountText(set.cardCount)
                    ListItem(
                        headlineContent = { Text(set.name) },
                        // Deuxième ligne seulement si TCGdex donne le nombre de cartes.
                        supportingContent = countText?.let { text -> @Composable { Text(text) } },
                        leadingContent = { SetLogo(set) },
                        modifier = Modifier.clickable { onOpenSet(set) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Le nombre de cartes à afficher, par exemple « 198 cartes (+ 60 secrètes) ».
 * Renvoie null si TCGdex ne donne pas ce nombre.
 */
private fun cardCountText(count: CardCount?): String? {
    val official = count?.official ?: return null
    val secret = (count?.total ?: official) - official
    return if (secret > 0) "$official cartes (+ $secret secrètes)" else "$official cartes"
}
