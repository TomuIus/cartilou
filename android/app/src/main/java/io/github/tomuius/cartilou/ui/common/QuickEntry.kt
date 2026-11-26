package io.github.tomuius.cartilou.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomuius.cartilou.R
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.AccountState
import io.github.tomuius.cartilou.data.supabase.CardVersion
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.data.supabase.OwnedCard
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.supabase.proposedVersion
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// La saisie rapide dans la grille d'une extension (Catalogue et Ma collection) :
// un mode à activer ; tant qu'il est actif, un appui sur une carte ajoute un
// exemplaire de la version choisie, un appui long en retire un.
// Décisions du 26/11/2026 : mode à activer (option B), version choisie dans
// un sélecteur du bandeau (option A).

/** Les choix du sélecteur de version. « Proposée » : la version proposée de chaque carte. */
enum class QuickVersion(val label: String, val version: CardVersion?) {
    Proposed("Proposée", null),
    Normal("Normale", CardVersion.Normal),
    Reverse("Reverse", CardVersion.Reverse),
    Holo("Holo", CardVersion.Holo),
    FirstEdition("1re éd.", CardVersion.FirstEdition),
}

/** Où en est la saisie rapide. */
enum class QuickEntryState { Off, Preparing, NeedsSignIn, On }

/**
 * Le ViewModel de la saisie rapide d'une extension.
 *
 * Les nombres affichés viennent de la collection gardée en mémoire
 * (OwnedCards.collection), mise à jour tout de suite à chaque appui ;
 * l'enregistrement dans Supabase suit (voir OwnedCards.changeQuantity).
 */
class QuickEntryViewModel(private val setId: String) : ViewModel() {

    var state by mutableStateOf(QuickEntryState.Off)
        private set

    /** La version ajoutée ou retirée à chaque appui. */
    var selected by mutableStateOf(QuickVersion.Proposed)

    /** Dernier message d'erreur, ou null. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        // Déconnexion pendant la saisie : on la termine.
        viewModelScope.launch {
            Account.state.collect { account ->
                if (account == AccountState.SignedOut && state == QuickEntryState.On) state = QuickEntryState.Off
            }
        }
    }

    /** Active la saisie : il faut être connecté, et la collection doit être lue. */
    fun start() {
        message = null
        if (Account.currentUserId() == null) {
            state = QuickEntryState.NeedsSignIn
            return
        }
        state = QuickEntryState.Preparing
        viewModelScope.launch {
            state = try {
                OwnedCards.prepareQuickEntry(setId)
                QuickEntryState.On
            } catch (e: DisplayableException) {
                message = e.message
                QuickEntryState.Off
            }
        }
    }

    fun stop() {
        state = QuickEntryState.Off
        message = null
    }

    /** Appui (+1) ou appui long (-1) sur une carte pendant la saisie. */
    fun change(card: CardBrief, delta: Int) {
        if (state != QuickEntryState.On) return
        message = null
        val chosen = selected.version
        if (chosen != null) {
            OwnedCards.changeQuantity(card.id, setId, chosen, delta, onError = { message = it })
            return
        }
        // « Proposée » : la version dépend de la carte, indiquée sur sa fiche
        // TCGdex (une requête la première fois, puis lue dans le cache).
        viewModelScope.launch {
            try {
                val version = proposedVersion(Tcgdex.card(card.id).variants)
                OwnedCards.changeQuantity(card.id, setId, version, delta, onError = { message = it })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Fiche de « ${card.name} » introuvable chez TCGdex. Vérifiez la connexion à Internet."
            }
        }
    }
}

/**
 * Nombre d'exemplaires d'une carte : d'une version précise, ou de toutes
 * ses versions si [version] est null. [lines] : les lignes de la carte dans la collection.
 */
fun ownedCopies(lines: List<OwnedCard>?, version: CardVersion?): Int =
    lines.orEmpty().filter { version == null || it.version == version }.sumOf { it.quantity }

/**
 * Le haut de la grille pour la saisie rapide : un bouton pour l'activer ;
 * une fois active, un bandeau avec le sélecteur de version et « Terminer ».
 *
 * @OptIn : les puces de filtre (FilterChip) sont peut-être encore marquées
 * « expérimentales » dans cette version de la bibliothèque.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryBar(viewModel: QuickEntryViewModel, onSignIn: () -> Unit) {
    when (viewModel.state) {
        QuickEntryState.Off -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::start) {
                Icon(painterResource(R.drawable.ic_ajouter), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Saisie rapide")
            }
            viewModel.message?.let { ErrorText(it) }
        }

        QuickEntryState.Preparing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Préparation de la saisie rapide…", style = MaterialTheme.typography.bodyMedium)
        }

        QuickEntryState.NeedsSignIn -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Connectez-vous pour utiliser la saisie rapide.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.stop(); onSignIn() }) { Text("Se connecter") }
                TextButton(onClick = viewModel::stop) { Text("Annuler") }
            }
        }

        // Bandeau bien visible (bordure de la couleur d'accent) : on voit
        // qu'un appui ajoute au lieu d'ouvrir la fiche.
        QuickEntryState.On -> Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Saisie rapide",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::stop) { Text("Terminer") }
                }
                Text(
                    text = "Appui sur une carte : +1 exemplaire · appui long : −1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Les cinq choix défilent à l'horizontale s'ils ne tiennent pas sur la largeur.
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickVersion.entries.forEach { option ->
                        FilterChip(
                            selected = option == viewModel.selected,
                            onClick = { viewModel.selected = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                viewModel.message?.let { ErrorText(it) }
            }
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}
