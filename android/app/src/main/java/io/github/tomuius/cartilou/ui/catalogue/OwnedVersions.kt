package io.github.tomuius.cartilou.ui.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.R
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.AccountState
import io.github.tomuius.cartilou.data.supabase.CardVersion
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.supabase.tcgdexVersions
import io.github.tomuius.cartilou.data.tcgdex.Card
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Ce que la partie « Ma collection » de la fiche doit afficher. */
sealed interface OwnedState {
    /** Connexion ou quantités en cours de chargement. */
    data object Loading : OwnedState

    /** Personne n'est connecté : on propose de se connecter. */
    data object SignedOut : OwnedState

    /** Les quantités n'ont pas pu être lues. */
    data class Error(val message: String) : OwnedState

    /** Le nombre d'exemplaires de chaque version (versions absentes = 0). */
    data class Ready(val quantities: Map<CardVersion, Int>) : OwnedState
}

/**
 * Le ViewModel de la partie « Ma collection » d'une fiche.
 *
 * Un appui sur « + » ou « − » change le nombre affiché tout de suite, puis
 * l'enregistre dans Supabase. Si l'enregistrement échoue, le nombre revient
 * à sa valeur enregistrée et un message s'affiche.
 *
 * Plusieurs appuis rapides : les enregistrements passent l'un après l'autre,
 * et chacun envoie le nombre affiché à ce moment-là ; ceux qui n'ont plus
 * rien à changer ne partent pas. Le dernier appui l'emporte donc.
 */
class OwnedVersionsViewModel(private val cardId: String) : ViewModel() {

    var state by mutableStateOf<OwnedState>(OwnedState.Loading)
        private set

    /** Message du dernier enregistrement raté, ou null. */
    var saveError by mutableStateOf<String?>(null)
        private set

    /** Les nombres déjà enregistrés dans Supabase, version par version. */
    private val saved = mutableMapOf<CardVersion, Int>()

    init {
        // Suit la connexion : à la connexion, lit les quantités ; à la
        // déconnexion, propose de se connecter.
        // collectLatest : si l'état change pendant une lecture, elle est
        // abandonnée au profit du nouvel état.
        viewModelScope.launch {
            Account.state.distinctUntilChanged().collectLatest { account ->
                when (account) {
                    AccountState.Loading -> state = OwnedState.Loading
                    AccountState.SignedOut -> state = OwnedState.SignedOut
                    is AccountState.SignedIn -> load()
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        state = OwnedState.Loading
        state = try {
            val quantities = OwnedCards.quantities(cardId)
            saved.clear()
            saved.putAll(quantities)
            OwnedState.Ready(quantities)
        } catch (e: DisplayableException) {
            OwnedState.Error(e.message ?: "Erreur inconnue.")
        }
    }

    /** Ajoute ([delta] = +1) ou retire (-1) un exemplaire d'une version. */
    fun change(card: Card, version: CardVersion, delta: Int) {
        val ready = state as? OwnedState.Ready ?: return
        val current = ready.quantities[version] ?: 0
        val wanted = (current + delta).coerceAtLeast(0)
        if (wanted == current) return
        state = ready.copy(quantities = ready.quantities + (version to wanted))
        saveError = null
        OwnedCards.launchWrite { save(card, version) }
    }

    /** Envoie le nombre affiché pour [version], s'il diffère de celui déjà enregistré. */
    private suspend fun save(card: Card, version: CardVersion) {
        val wanted = (state as? OwnedState.Ready)?.quantities?.get(version) ?: return
        val before = saved[version] ?: 0
        if (wanted == before) return
        try {
            OwnedCards.setQuantity(card, version, wanted)
            saved[version] = wanted
        } catch (e: DisplayableException) {
            saveError = e.message
            val ready = state as? OwnedState.Ready ?: return
            state = ready.copy(quantities = ready.quantities + (version to before))
        }
    }
}

/**
 * La partie « Ma collection » de la fiche : un compteur par version si l'on
 * est connecté, sinon les versions connues de TCGdex et un bouton pour se
 * connecter.
 */
@Composable
fun OwnedVersionsSection(card: Card, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = viewModel { OwnedVersionsViewModel(card.id) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val state = viewModel.state) {
            OwnedState.Loading -> {
                SectionTitle("Ma collection")
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            OwnedState.SignedOut -> {
                TcgdexVersions(card)
                Button(onClick = onSignIn) { Text("Se connecter pour ajouter cette carte") }
            }
            is OwnedState.Error -> {
                SectionTitle("Ma collection")
                Text(state.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = viewModel::reload) { Text("Réessayer") }
            }
            is OwnedState.Ready -> VersionCounters(
                card = card,
                quantities = state.quantities,
                saveError = viewModel.saveError,
                onChange = { version, delta -> viewModel.change(card, version, delta) },
            )
        }
    }
}

/**
 * Les compteurs. Lignes affichées : les versions connues de TCGdex (la
 * version normale si TCGdex n'en indique aucune) et celles déjà possédées ;
 * « Autre version… » montre les autres (TCGdex en oublie parfois).
 */
@Composable
private fun VersionCounters(
    card: Card,
    quantities: Map<CardVersion, Int>,
    saveError: String?,
    onChange: (CardVersion, Int) -> Unit,
) {
    val known = tcgdexVersions(card.variants)
    val proposed = known.ifEmpty { listOf(CardVersion.Normal) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    // « it in quantities » : la version figure dans la collection (même à 0
    // après un retrait : la ligne reste affichée, pour pouvoir se corriger).
    val shown = CardVersion.entries.filter { showAll || it in proposed || it in quantities }

    SectionTitle("Ma collection")
    shown.forEach { version ->
        VersionCounter(
            version = version,
            quantity = quantities[version] ?: 0,
            knownByTcgdex = version in known,
            onRemove = { onChange(version, -1) },
            onAdd = { onChange(version, +1) },
        )
    }
    if (shown.size < CardVersion.entries.size) {
        TextButton(onClick = { showAll = true }) { Text("Autre version…") }
    }
    if (saveError != null) {
        Text(saveError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Une ligne : « Reverse   −  2  + ». */
@Composable
private fun VersionCounter(
    version: CardVersion,
    quantity: Int,
    knownByTcgdex: Boolean,
    onRemove: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // weight(1f) : le nom prend toute la place restante, les boutons restent à droite.
        Column(modifier = Modifier.weight(1f)) {
            Text(version.label, style = MaterialTheme.typography.bodyLarge)
            if (!knownByTcgdex) {
                Text(
                    text = "Non indiquée par TCGdex",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove, enabled = quantity > 0) {
            Icon(painterResource(R.drawable.ic_retirer), contentDescription = "Retirer un exemplaire (${version.label})")
        }
        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp),
        )
        FilledTonalIconButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_ajouter), contentDescription = "Ajouter un exemplaire (${version.label})")
        }
    }
}

/** Les versions connues de TCGdex, en pastilles (affichées quand on n'est pas connecté). */
@Composable
private fun TcgdexVersions(card: Card) {
    // « Connues de TCGdex » : sa liste est parfois incomplète (des reverse
    // manquent pour des cartes Noir & Blanc, XY, Soleil & Lune, constaté en test).
    SectionTitle("Versions connues de TCGdex")
    val versions = tcgdexVersions(card.variants)
    if (versions.isEmpty()) {
        Text("Non renseignées", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            versions.forEach { VersionBadge(it.label) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

/** Une pastille avec le nom d'une version (par exemple « Reverse »). */
@Composable
private fun VersionBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
