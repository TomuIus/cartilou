package io.github.tomuius.cartilou.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.AccountState
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.data.supabase.OwnedCard
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.tcgdex.SerieBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import io.github.tomuius.cartilou.ui.common.SerieLogo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Où en est la lecture de la collection. */
sealed interface CollectionStatus {
    data object Loading : CollectionStatus
    data object SignedOut : CollectionStatus
    data class Error(val message: String) : CollectionStatus

    /** Collection lue : son contenu est dans OwnedCards.collection. */
    data object Ready : CollectionStatus
}

/**
 * Le ViewModel de l'onglet Ma collection. Il suit la connexion : à la
 * connexion, il lit toute la collection dans Supabase ; à la déconnexion, il
 * l'oublie. Les écrans d'une série et d'une extension se servent ensuite de
 * la collection gardée en mémoire, sans relire Supabase.
 *
 * L'écran de départ de l'onglet ne quitte jamais la pile : ce ViewModel vit
 * aussi longtemps que l'appli, une fois l'onglet ouvert.
 */
class CollectionViewModel : ViewModel() {

    var status by mutableStateOf<CollectionStatus>(CollectionStatus.Loading)
        private set

    init {
        viewModelScope.launch {
            Account.state.distinctUntilChanged().collectLatest { account ->
                when (account) {
                    AccountState.Loading -> status = CollectionStatus.Loading
                    AccountState.SignedOut -> {
                        OwnedCards.forgetCollection()
                        status = CollectionStatus.SignedOut
                    }
                    is AccountState.SignedIn -> load()
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        status = CollectionStatus.Loading
        status = try {
            OwnedCards.loadCollection()
            CollectionStatus.Ready
        } catch (e: DisplayableException) {
            CollectionStatus.Error(e.message ?: "Erreur inconnue.")
        }
    }
}

/** Les séries chez TCGdex, les plus récentes en haut (comme dans le Catalogue). */
class OwnedSeriesViewModel : LoadViewModel<List<SerieBrief>>({ Tcgdex.series().asReversed() })

/**
 * L'onglet « Ma collection » : les séries dont vous possédez au moins une
 * carte. Un appui ouvre la série.
 *
 * [onSignIn] : ouvre l'onglet Compte pour se connecter.
 */
@Composable
fun CollectionScreen(onSignIn: () -> Unit, onOpenSerie: (SerieBrief) -> Unit) {
    val viewModel = viewModel { CollectionViewModel() }

    Scaffold(topBar = { CartilouTopBar("Ma collection") }) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (val status = viewModel.status) {
            CollectionStatus.Loading -> CenteredContent(modifier) { CircularProgressIndicator() }
            CollectionStatus.SignedOut -> CenteredContent(modifier) {
                Text("Connectez-vous pour voir votre collection.", textAlign = TextAlign.Center)
                Button(onClick = onSignIn) { Text("Se connecter") }
            }
            is CollectionStatus.Error -> CenteredContent(modifier) {
                Text(status.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::reload) { Text("Réessayer") }
            }
            CollectionStatus.Ready -> OwnedSeriesList(onOpenSerie, modifier)
        }
    }
}

/** La liste des séries possédées, avec un résumé de la collection en haut. */
@Composable
private fun OwnedSeriesList(onOpenSerie: (SerieBrief) -> Unit, modifier: Modifier) {
    val owned = OwnedCards.collection.collectAsStateWithLifecycle().value.orEmpty()
    if (owned.isEmpty()) {
        CenteredMessage(
            "Votre collection est vide.\nAjoutez des cartes depuis leur fiche, dans l'onglet Catalogue.",
            modifier,
        )
        return
    }

    val seriesViewModel = viewModel { OwnedSeriesViewModel() }
    val state by seriesViewModel.state.collectAsStateWithLifecycle()
    // remember(owned) : recalculé seulement quand la collection change.
    val countBySerie = remember(owned) { distinctCardCounts(owned) { it.serieId } }

    LoadStateContent(state, onRetry = seriesViewModel::reload, modifier = modifier) { series ->
        val ownedSeries = series.filter { it.id in countBySerie }
        LazyColumn {
            item {
                Text(
                    text = collectionSummary(owned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(ownedSeries, key = { it.id }) { serie ->
                ListItem(
                    headlineContent = { Text(serie.name) },
                    supportingContent = { Text(cardsText(countBySerie.getValue(serie.id))) },
                    leadingContent = { SerieLogo(serie.logo) },
                    modifier = Modifier.clickable { onOpenSerie(serie) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** Par exemple « 37 cartes différentes · 52 exemplaires ». */
private fun collectionSummary(owned: List<OwnedCard>): String {
    val cards = owned.distinctBy { it.cardId }.size
    val copies = owned.sumOf { it.quantity }
    val cardsPart = if (cards == 1) "1 carte différente" else "$cards cartes différentes"
    val copiesPart = if (copies == 1) "1 exemplaire" else "$copies exemplaires"
    return "$cardsPart · $copiesPart"
}
