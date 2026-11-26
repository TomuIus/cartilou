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
import io.github.tomuius.cartilou.data.tcgdex.SerieBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import io.github.tomuius.cartilou.ui.common.SerieLogo

/**
 * Séries masquées dans le catalogue. « tcgp » : TCG Pocket, le jeu de cartes
 * sur téléphone, dont les cartes n'existent pas en vrai.
 */
private val HIDDEN_SERIES = setOf("tcgp")

/**
 * Charge la liste des séries, sans les séries masquées.
 *
 * TCGdex les renvoie de la plus ancienne à la plus récente (constaté en test
 * le 26/11/2026) : on inverse l'ordre pour avoir les plus récentes en haut.
 */
class SeriesViewModel : LoadViewModel<List<SerieBrief>>({
    Tcgdex.series().filter { it.id !in HIDDEN_SERIES }.asReversed()
})

/** Premier écran : la liste des séries. Un appui ouvre la série. */
@Composable
fun SeriesScreen(onOpenSerie: (SerieBrief) -> Unit) {
    // viewModel { … } : crée le ViewModel la première fois, puis le réutilise
    // tant que l'écran reste dans l'historique de navigation.
    val viewModel = viewModel { SeriesViewModel() }
    // Observe l'état : l'écran se redessine à chaque changement.
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { CartilouTopBar("Séries") }) { innerPadding ->
        LoadStateContent(state, onRetry = viewModel::reload, modifier = Modifier.padding(innerPadding)) { series ->
            // LazyColumn : liste qui ne dessine que les lignes visibles à l'écran.
            LazyColumn {
                items(series, key = { it.id }) { serie ->
                    ListItem(
                        headlineContent = { Text(serie.name) },
                        leadingContent = { SerieLogo(serie.logo) },
                        modifier = Modifier.clickable { onOpenSerie(serie) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
