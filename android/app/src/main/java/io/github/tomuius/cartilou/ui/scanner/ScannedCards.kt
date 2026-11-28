package io.github.tomuius.cartilou.ui.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.R
import io.github.tomuius.cartilou.data.scan.BasketItem
import io.github.tomuius.cartilou.data.scan.ScanBasket
import io.github.tomuius.cartilou.data.supabase.Account
import io.github.tomuius.cartilou.data.supabase.DisplayableException
import io.github.tomuius.cartilou.data.supabase.OwnedCards
import io.github.tomuius.cartilou.data.supabase.proposedVersion
import io.github.tomuius.cartilou.data.tcgdex.Tcgdex
import io.github.tomuius.cartilou.ui.common.CardImage
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.QuickVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Les cartes scannées (ancien « panier », renommé le 28/11/2026, option A) :
// les cartes reconnues s'y accumulent sans être enregistrées. Elles se
// corrigent sur l'écran du scan, ou sur cet écran en grand, d'où l'on ajoute
// tout à la collection (décision du 28/11/2026).
// Dans le code, la liste garde son nom technique (ScanBasket, BasketItem,
// fichier panier_scan.json), pour ne pas perdre les cartes déjà scannées.

/**
 * Le ViewModel de l'écran des cartes scannées : l'ajout de toutes les cartes à
 * la collection, et le nom de l'extension de chaque carte (pour vérifier qu'il
 * s'agit de la bonne réédition).
 */
class ScannedCardsViewModel : ViewModel() {

    var validating by mutableStateOf(false)
        private set

    /** Message de l'ajout à la collection, ou null. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Vrai si l'ajout a échoué faute de connexion au compte. */
    var needsSignIn by mutableStateOf(false)
        private set

    /** Nom de chaque extension, d'après son identifiant (liste en cache, déjà lue par le scan). */
    private var setNames by mutableStateOf<Map<String, String>>(emptyMap())

    init {
        viewModelScope.launch {
            try {
                setNames = Tcgdex.allSets().associate { it.id to it.name }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sans la liste, on n'affiche simplement pas le nom de l'extension.
            }
        }
    }

    /**
     * Le nom de l'extension d'une carte, si connu. L'extension est déduite de
     * l'identifiant de la carte (« base1-10 » → « base1 », même hypothèse que
     * dans CardMatcher).
     */
    fun setNameOf(item: BasketItem): String? = setNames[item.cardId.removeSuffix("-${item.localId}")]

    /**
     * Ajoute toutes les cartes scannées à la collection, ligne par ligne. Chaque
     * ligne enregistrée quitte la liste ; au premier échec, on s'arrête et le
     * reste attend.
     */
    fun validate() {
        if (validating) return
        needsSignIn = false
        if (Account.currentUserId() == null) {
            message = "Connectez-vous dans l'onglet Compte pour ajouter ces cartes à votre collection."
            needsSignIn = true
            return
        }
        viewModelScope.launch {
            validating = true
            message = null
            var added = 0
            var failed = false
            for (item in ScanBasket.items.value.reversed()) {
                try {
                    // La fiche donne l'extension et, pour « Proposée », la version.
                    val card = try {
                        Tcgdex.card(item.cardId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw DisplayableException(
                            "Fiche de « ${item.name} » introuvable chez TCGdex. Vérifiez la connexion à Internet.", e,
                        )
                    }
                    val version = item.cardVersion ?: proposedVersion(card.variants)
                    OwnedCards.addCopies(card.id, card.set.id, version, item.quantity)
                    ScanBasket.remove(item)
                    added += item.quantity
                } catch (e: DisplayableException) {
                    message = e.message
                    failed = true
                    break
                }
            }
            if (!failed) {
                message = if (added == 1) "1 exemplaire ajouté à votre collection."
                else "$added exemplaires ajoutés à votre collection."
            }
            validating = false
        }
    }
}

/** L'écran des cartes scannées, en grand : vérifier, corriger, puis tout ajouter à la collection. */
@Composable
fun ScannedCardsScreen(onBack: () -> Unit, onSignIn: () -> Unit) {
    val viewModel = viewModel { ScannedCardsViewModel() }
    val items by ScanBasket.items.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val copies = items.sumOf { it.quantity }

    Scaffold(topBar = { CartilouTopBar("Cartes scannées", onBack = onBack) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Aucune carte scannée. Les cartes reconnues par la caméra apparaissent ici.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "${cardsLabel(items.size)} différentes · ${copiesLabel(copies)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(items, key = { it.key }) { item ->
                        ScannedCardRow(item, setName = viewModel.setNameOf(item), imageWidth = 64.dp)
                        HorizontalDivider()
                    }
                }
            }
            viewModel.message?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (viewModel.needsSignIn) {
                TextButton(onClick = onSignIn, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Se connecter") }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = items.isNotEmpty() && !viewModel.validating,
                ) { Text("Tout retirer") }
                Button(
                    onClick = viewModel::validate,
                    enabled = items.isNotEmpty() && !viewModel.validating,
                    modifier = Modifier.weight(1f),
                ) {
                    if (viewModel.validating) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Ajouter à ma collection")
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Retirer toutes les cartes scannées ?") },
            text = { Text("Elles seront retirées de cette liste. Rien n'a encore été ajouté à votre collection.") },
            confirmButton = {
                TextButton(onClick = {
                    ScanBasket.clear()
                    confirmClear = false
                }) { Text("Tout retirer") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

/** « 1 carte », « 3 cartes ». */
internal fun cardsLabel(count: Int): String = if (count <= 1) "$count carte" else "$count cartes"

/** « 1 exemplaire », « 3 exemplaires ». */
internal fun copiesLabel(count: Int): String = if (count <= 1) "$count exemplaire" else "$count exemplaires"

/**
 * Une carte scannée : image, nom, numéro (et extension si [setName] est
 * donné), version (à changer d'un appui), − nombre +. Le − à 1 exemplaire
 * retire la carte de la liste. Sert sur l'écran du scan (petite image) et sur
 * l'écran des cartes scannées (grande image, avec l'extension).
 */
@Composable
internal fun ScannedCardRow(item: BasketItem, setName: String?, imageWidth: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(item.image, item.name, quality = "low", modifier = Modifier.width(imageWidth))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = if (setName != null) "N° ${item.localId} · $setName" else "N° ${item.localId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            VersionMenu(item)
        }
        IconButton(onClick = { ScanBasket.setQuantity(item, item.quantity - 1) }) {
            Icon(painterResource(R.drawable.ic_retirer), contentDescription = "Retirer un exemplaire de ${item.name}")
        }
        Text(
            text = item.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        IconButton(onClick = { ScanBasket.setQuantity(item, item.quantity + 1) }) {
            Icon(painterResource(R.drawable.ic_ajouter), contentDescription = "Ajouter un exemplaire de ${item.name}")
        }
    }
}

/** La version d'une carte scannée, en couleur d'accent ; un appui ouvre la liste des versions. */
@Composable
private fun VersionMenu(item: BasketItem) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            text = (item.cardVersion?.label ?: QuickVersion.Proposed.label) + " ▾",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { open = true }
                .padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            QuickVersion.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        ScanBasket.setVersion(item, option.version)
                    },
                )
            }
        }
    }
}
