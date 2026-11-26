package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Les trois états possibles d'un écran qui charge des données :
 * en cours, en erreur, ou prêt avec les données.
 *
 * « sealed » : il n'existe que ces trois cas, et Kotlin vérifie qu'on les
 * traite tous dans un « when ».
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
}

/**
 * Un ViewModel qui charge une donnée au démarrage, avec possibilité de
 * réessayer. Chaque écran du catalogue en hérite en indiquant quoi charger
 * (le paramètre « fetch »).
 *
 * L'état est exposé dans un StateFlow : une valeur qui change dans le temps,
 * que l'écran observe et réaffiche automatiquement.
 */
open class LoadViewModel<T>(private val fetch: suspend () -> T) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<T>>(LoadState.Loading)
    val state: StateFlow<LoadState<T>> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = LoadState.Loading
        // viewModelScope : la coroutine est annulée d'elle-même si l'écran est
        // fermé avant la fin du chargement.
        viewModelScope.launch {
            _state.value = try {
                LoadState.Success(fetch())
            } catch (e: CancellationException) {
                // Annulation normale (écran fermé) : on la laisse passer.
                throw e
            } catch (e: Exception) {
                LoadState.Error(e.message ?: e.toString())
            }
        }
    }
}

/**
 * Affiche le bon contenu selon l'état : une roue pendant le chargement, un
 * message et un bouton « Réessayer » en cas d'erreur, sinon le contenu.
 */
@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is LoadState.Error -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Impossible de charger les données. Vérifiez la connexion à Internet.",
                textAlign = TextAlign.Center,
            )
            // Le détail technique, utile pour comprendre un problème pendant les tests.
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("Réessayer") }
        }

        is LoadState.Success -> Box(modifier) { content(state.data) }
    }
}
