package io.github.tomuius.cartilou.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.tomuius.cartilou.ui.catalogue.SerieScreen
import io.github.tomuius.cartilou.ui.catalogue.SeriesScreen
import io.github.tomuius.cartilou.ui.catalogue.SetScreen
import kotlinx.serialization.Serializable

// Les « clés » de navigation : une par écran, avec ce dont l'écran a besoin.
// @Serializable : Navigation 3 peut ainsi enregistrer l'historique des écrans
// et le retrouver si Android ferme l'appli en arrière-plan.

/** Écran des séries. */
@Serializable
data object SeriesRoute : NavKey

/** Écran d'une série (ses extensions). Le nom sert au titre, sans attendre le chargement. */
@Serializable
data class SerieRoute(val id: String, val name: String) : NavKey

/** Écran d'une extension (ses cartes). */
@Serializable
data class SetRoute(val id: String, val name: String) : NavKey

/**
 * La navigation de l'appli.
 *
 * L'historique (« back stack ») est une simple liste de clés : le dernier
 * élément est l'écran affiché. Ouvrir un écran = ajouter une clé ; revenir en
 * arrière = retirer la dernière.
 */
@Composable
fun CartilouNavigation() {
    val backStack = rememberNavBackStack(SeriesRoute)

    NavDisplay(
        backStack = backStack,
        // Bouton ou geste « retour » du téléphone.
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            // Garde l'état de chaque écran (position dans une liste…) quand on y revient.
            rememberSaveableStateHolderNavEntryDecorator(),
            // Donne à chaque écran ses propres ViewModels, supprimés quand on le quitte.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        // Pour chaque type de clé, l'écran à afficher.
        entryProvider = entryProvider {
            entry<SeriesRoute> {
                SeriesScreen(
                    onOpenSerie = { serie -> backStack.add(SerieRoute(serie.id, serie.name)) },
                )
            }
            entry<SerieRoute> { route ->
                SerieScreen(
                    serieId = route.id,
                    serieName = route.name,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenSet = { set -> backStack.add(SetRoute(set.id, set.name)) },
                )
            }
            entry<SetRoute> { route ->
                SetScreen(
                    setId = route.id,
                    setName = route.name,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
