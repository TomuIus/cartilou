package io.github.tomuius.cartilou.ui.catalogue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.tomuius.cartilou.data.tcgdex.SerieBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.data.tcgdex.assetUrl
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel

/**
 * Charge la liste des séries.
 *
 * TCGdex semble les renvoyer de la plus ancienne à la plus récente (à vérifier
 * en testant) : on inverse l'ordre pour avoir les plus récentes en haut.
 */
class SeriesViewModel : LoadViewModel<List<SerieBrief>>({ Tcgdex.series().asReversed() })

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

/** Le logo d'une série, ou un espace vide de même taille s'il n'y en a pas. */
@Composable
private fun SerieLogo(logo: String?) {
    val url = assetUrl(logo)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null, // purement décoratif : le nom est écrit à côté
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 72.dp, height = 40.dp),
        )
    } else {
        Box(Modifier.size(width = 72.dp, height = 40.dp))
    }
}
