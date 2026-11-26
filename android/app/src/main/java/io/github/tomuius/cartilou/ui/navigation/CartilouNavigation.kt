package io.github.tomuius.cartilou.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.tomuius.cartilou.R
import io.github.tomuius.cartilou.ui.account.AccountScreen
import io.github.tomuius.cartilou.ui.catalogue.CardScreen
import io.github.tomuius.cartilou.ui.catalogue.SerieScreen
import io.github.tomuius.cartilou.ui.catalogue.SeriesScreen
import io.github.tomuius.cartilou.ui.catalogue.SetScreen
import io.github.tomuius.cartilou.ui.collection.CollectionScreen
import io.github.tomuius.cartilou.ui.collection.CollectionSerieScreen
import io.github.tomuius.cartilou.ui.collection.CollectionSetScreen
import kotlinx.serialization.Serializable

// Les « clés » de navigation : une par écran, avec ce dont l'écran a besoin.

/** Écran des séries (racine de l'onglet Catalogue). */
@Serializable
data object SeriesRoute : NavKey

/** Écran d'une série (ses extensions). Le nom sert au titre, sans attendre le chargement. */
@Serializable
data class SerieRoute(val id: String, val name: String) : NavKey

/** Écran d'une extension (ses cartes). */
@Serializable
data class SetRoute(val id: String, val name: String) : NavKey

/** Fiche d'une carte. */
@Serializable
data class CardRoute(val id: String, val name: String) : NavKey

/** Racine de l'onglet Ma collection. */
@Serializable
data object CollectionRoute : NavKey

// Les écrans de Ma collection ont leurs propres clés, même pour la fiche
// d'une carte : une même carte peut ainsi être ouverte à la fois dans le
// Catalogue et dans Ma collection sans que Navigation 3 confonde les deux
// (chaque écran est repéré par sa clé ; deux clés identiques désigneraient
// le même écran).

/** Ma collection : une série. */
@Serializable
data class CollectionSerieRoute(val id: String, val name: String) : NavKey

/** Ma collection : une extension. */
@Serializable
data class CollectionSetRoute(val id: String, val name: String) : NavKey

/** Ma collection : la fiche d'une carte (même écran que dans le Catalogue). */
@Serializable
data class CollectionCardRoute(val id: String, val name: String) : NavKey

/** Racine de l'onglet Compte. */
@Serializable
data object AccountRoute : NavKey

/** Les onglets de la barre du bas, dans l'ordre d'affichage, avec leur écran de départ. */
enum class Tab(val label: String, @param:DrawableRes val icon: Int, val root: NavKey) {
    Catalogue("Catalogue", R.drawable.ic_onglet_catalogue, SeriesRoute),
    Collection("Ma collection", R.drawable.ic_onglet_collection, CollectionRoute),
    Account("Compte", R.drawable.ic_onglet_compte, AccountRoute),
}

/**
 * L'état de la navigation : une pile d'écrans (« back stack ») par onglet,
 * et l'onglet affiché.
 *
 * Dans un ViewModel pour survivre à une rotation de l'écran.
 *
 * Chaque onglet garde sa pile quand on en change : en revenant sur
 * Catalogue, on retrouve l'extension qu'on regardait.
 */
class TabsViewModel : ViewModel() {

    private val stacks: Map<Tab, SnapshotStateList<NavKey>> =
        Tab.entries.associateWith { mutableStateListOf(it.root) }

    /** L'onglet affiché. */
    var current by mutableStateOf(Tab.Catalogue)
        private set

    /**
     * Ce qu'on donne à Navigation 3 : les écrans de tous les onglets, ceux de
     * l'onglet affiché en dernier (c'est le dernier écran qui est montré).
     * Les écrans des autres onglets restent dans la liste : leurs ViewModels
     * et leur position (défilement…) sont ainsi conservés.
     */
    val entries: List<NavKey>
        get() = Tab.entries.filter { it != current }.flatMap { stacks.getValue(it) } +
            stacks.getValue(current)

    /** Appui sur un onglet. Sur l'onglet déjà affiché : retour à son écran de départ. */
    fun select(tab: Tab) {
        if (tab == current) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            current = tab
        }
    }

    /** Ouvre un écran dans l'onglet affiché. */
    fun open(key: NavKey) {
        stacks.getValue(current).add(key)
    }

    /**
     * Bouton ou geste « retour » :
     * - s'il y a un écran précédent dans l'onglet, on y revient ;
     * - sinon, depuis un autre onglet, on revient au Catalogue ;
     * - sinon (écran de départ du Catalogue), renvoie false : l'appli passe
     *   alors en arrière-plan, comme avec le bouton retour habituel.
     */
    fun back(): Boolean {
        val stack = stacks.getValue(current)
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }
            current != Tab.Catalogue -> {
                current = Tab.Catalogue
                true
            }
            else -> false
        }
    }
}

/** La navigation de l'appli : la barre d'onglets en bas, et l'écran affiché au-dessus. */
@Composable
fun CartilouNavigation() {
    val tabs = viewModel { TabsViewModel() }
    val activity = LocalActivity.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == tabs.current,
                        onClick = { tabs.select(tab) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        // Les écrans ont leur propre barre du haut, qui gère déjà la barre d'état :
        // ici, on ne réserve que la place de la barre d'onglets.
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        NavDisplay(
            backStack = tabs.entries,
            // consumeWindowInsets : indique aux écrans que le bas de l'écran est
            // déjà pris en compte (sinon ils laisseraient un espace en double).
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            onBack = { if (!tabs.back()) activity?.moveTaskToBack(true) },
            entryDecorators = listOf(
                // Garde l'état de chaque écran (position dans une liste…).
                rememberSaveableStateHolderNavEntryDecorator(),
                // Donne à chaque écran ses propres ViewModels, supprimés quand on le quitte.
                rememberViewModelStoreNavEntryDecorator(),
            ),
            // Pour chaque type de clé, l'écran à afficher.
            entryProvider = entryProvider {
                entry<SeriesRoute> {
                    SeriesScreen(onOpenSerie = { serie -> tabs.open(SerieRoute(serie.id, serie.name)) })
                }
                entry<SerieRoute> { route ->
                    SerieScreen(
                        serieId = route.id,
                        serieName = route.name,
                        onBack = { tabs.back() },
                        onOpenSet = { set -> tabs.open(SetRoute(set.id, set.name)) },
                    )
                }
                entry<SetRoute> { route ->
                    SetScreen(
                        setId = route.id,
                        setName = route.name,
                        onBack = { tabs.back() },
                        onOpenCard = { card -> tabs.open(CardRoute(card.id, card.name)) },
                    )
                }
                entry<CardRoute> { route ->
                    CardScreen(
                        cardId = route.id,
                        cardName = route.name,
                        onBack = { tabs.back() },
                        onSignIn = { tabs.select(Tab.Account) },
                    )
                }
                entry<CollectionRoute> {
                    CollectionScreen(
                        onSignIn = { tabs.select(Tab.Account) },
                        onOpenSerie = { serie -> tabs.open(CollectionSerieRoute(serie.id, serie.name)) },
                    )
                }
                entry<CollectionSerieRoute> { route ->
                    CollectionSerieScreen(
                        serieId = route.id,
                        serieName = route.name,
                        onBack = { tabs.back() },
                        onOpenSet = { set -> tabs.open(CollectionSetRoute(set.id, set.name)) },
                    )
                }
                entry<CollectionSetRoute> { route ->
                    CollectionSetScreen(
                        setId = route.id,
                        setName = route.name,
                        onBack = { tabs.back() },
                        onOpenCard = { card -> tabs.open(CollectionCardRoute(card.id, card.name)) },
                    )
                }
                entry<CollectionCardRoute> { route ->
                    CardScreen(
                        cardId = route.id,
                        cardName = route.name,
                        onBack = { tabs.back() },
                        onSignIn = { tabs.select(Tab.Account) },
                    )
                }
                entry<AccountRoute> {
                    AccountScreen()
                }
            },
        )
    }
}
