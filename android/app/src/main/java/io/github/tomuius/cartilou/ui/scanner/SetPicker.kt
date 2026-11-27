package io.github.tomuius.cartilou.ui.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.data.tcgdex.SerieBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.catalogue.HIDDEN_SERIES
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.LoadStateContent
import io.github.tomuius.cartilou.ui.common.LoadViewModel
import io.github.tomuius.cartilou.ui.common.SetLogo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer

// Le choix de l'extension à scanner (décisions du 28/11/2026 : seulement dans
// l'extension choisie, option A ; choix dans l'onglet Scanner, option A).

/** Une série et ses extensions, les plus récentes d'abord. */
class SerieWithSets(val serie: SerieBrief, val sets: List<CardSetBrief>)

/**
 * Charge toutes les séries (sauf les séries masquées) et leurs extensions, les
 * plus récentes en haut, comme dans le catalogue. Une requête par série la
 * première fois (lancées en même temps), puis le cache de 7 jours.
 */
class SetPickerViewModel : LoadViewModel<List<SerieWithSets>>({
    coroutineScope {
        Tcgdex.series().filter { it.id !in HIDDEN_SERIES }.asReversed()
            .map { serie -> async { SerieWithSets(serie, Tcgdex.serie(serie.id).sets.asReversed()) } }
            .awaitAll()
    }
})

/**
 * La fenêtre de choix, en plein écran : un champ de recherche, « Toutes les
 * extensions », puis les extensions rangées par série. [onPick] reçoit
 * l'extension choisie, ou null pour toutes.
 */
@Composable
fun SetPickerDialog(onPick: (CardSetBrief?) -> Unit, onDismiss: () -> Unit) {
    val viewModel = viewModel { SetPickerViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        // Toute la largeur de l'écran, et non une petite fenêtre au centre.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                CartilouTopBar("Extension à scanner", onBack = onDismiss)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Rechercher une extension ou une série") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LoadStateContent(state, onRetry = viewModel::reload) { series ->
                    val shown = remember(series, query) { filterSeries(series, query) }
                    LazyColumn {
                        if (query.isBlank()) {
                            item(key = "toutes") {
                                ListItem(
                                    headlineContent = { Text("Toutes les extensions") },
                                    supportingContent = { Text("Pour un paquet de cartes mélangées") },
                                    modifier = Modifier.clickable { onPick(null) },
                                )
                                HorizontalDivider()
                            }
                        }
                        shown.forEach { group ->
                            item(key = "serie-${group.serie.id}") {
                                Text(
                                    text = group.serie.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(group.sets, key = { "set-${group.serie.id}-${it.id}" }) { set ->
                                ListItem(
                                    headlineContent = { Text(set.name) },
                                    supportingContent = set.cardCount?.official?.let { count -> @Composable { Text("$count cartes") } },
                                    leadingContent = { SetLogo(set) },
                                    modifier = Modifier.clickable { onPick(set) },
                                )
                                HorizontalDivider()
                            }
                        }
                        if (shown.isEmpty()) {
                            item(key = "aucune") {
                                Text(
                                    "Aucune extension ne correspond à « $query ».",
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Les séries et extensions dont le nom contient [query] (sans tenir compte des
 * accents ni des majuscules). Si c'est le nom de la série qui correspond, on
 * garde toutes ses extensions.
 */
private fun filterSeries(series: List<SerieWithSets>, query: String): List<SerieWithSets> {
    val wanted = simplified(query.trim())
    if (wanted.isEmpty()) return series
    return series.mapNotNull { group ->
        if (simplified(group.serie.name).contains(wanted)) {
            group
        } else {
            val sets = group.sets.filter { simplified(it.name).contains(wanted) }
            if (sets.isEmpty()) null else SerieWithSets(group.serie, sets)
        }
    }
}

/** Minuscules, sans accents : « Écarlate » → « ecarlate ». */
private fun simplified(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").lowercase()
