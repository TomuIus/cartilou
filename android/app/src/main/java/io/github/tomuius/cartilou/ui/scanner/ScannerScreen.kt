package io.github.tomuius.cartilou.ui.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.cartilou.R
import io.github.tomuius.cartilou.data.scan.BasketItem
import io.github.tomuius.cartilou.data.scan.ScanBasket
import io.github.tomuius.cartilou.data.tcgdex.CardBrief
import io.github.tomuius.cartilou.ui.common.CardImage
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.QuickVersion

// L'onglet Scanner (décisions du 27/11/2026) : la caméra lit les cartes en
// continu (option B) ; les cartes reconnues vont dans un panier (option C),
// qu'on vérifie avant de tout ajouter à la collection.
// Disposition : l'aperçu de la caméra en haut, le choix de la version au
// milieu, le panier en bas.

/** Proportions d'une carte Pokémon (63 × 88 mm), pour le cadre-guide. */
private const val CARD_RATIO = 63f / 88f

@Composable
fun ScannerScreen(onSignIn: () -> Unit) {
    val viewModel = viewModel { ScannerViewModel() }
    val items by ScanBasket.items.collectAsStateWithLifecycle()

    // Petite vibration à chaque carte mise au panier.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(viewModel.addedCount) {
        if (viewModel.addedCount > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(topBar = { CartilouTopBar("Scanner") }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // weight : la caméra et le panier se partagent la hauteur, moitié-moitié
            // (à revoir après des tests).
            CameraSection(viewModel, Modifier.fillMaxWidth().weight(1f))
            VersionChips(
                selected = viewModel.selected,
                onSelect = { viewModel.selected = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            BasketSection(items, viewModel, onSignIn, Modifier.fillMaxWidth().weight(1f))
        }
    }

    viewModel.choice?.let { cards ->
        ChoiceDialog(
            cards = cards,
            setNameOf = viewModel::setNameOf,
            onChoose = viewModel::choose,
            onDismiss = viewModel::dismissChoice,
        )
    }
}

// --- Caméra ---

/** La zone du haut : autorisation de la caméra, préparation, puis aperçu avec le cadre-guide. */
@Composable
private fun CameraSection(viewModel: ScannerViewModel, modifier: Modifier) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refused = !ok
    }
    // Retour dans l'appli (par exemple après les réglages d'Android) : on revérifie.
    LifecycleResumeEffect(Unit) {
        granted = hasCameraPermission(context)
        onPauseOrDispose { }
    }

    Box(
        modifier = modifier.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !granted -> CenteredColumn {
                Text(
                    "Cartilou a besoin de la caméra pour lire vos cartes. " +
                        "Les images sont lues sur le téléphone et ne sont envoyées nulle part.",
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                if (refused) {
                    // Android ne redemande plus après plusieurs refus : il faut passer par ses réglages.
                    TextButton(onClick = { openAppSettings(context) }) { Text("Ouvrir les réglages de l'appli") }
                }
            }

            else -> when (val catalog = viewModel.catalog) {
                CatalogState.Loading -> CenteredColumn {
                    CircularProgressIndicator()
                    Text("Préparation du scan : chargement de la liste des cartes…", textAlign = TextAlign.Center)
                }

                is CatalogState.Error -> CenteredColumn {
                    Text(catalog.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::loadCatalog) { Text("Réessayer") }
                }

                CatalogState.Ready -> {
                    CameraPreview(onText = viewModel::onText, modifier = Modifier.fillMaxSize())
                    // Cadre-guide de la forme d'une carte.
                    Box(
                        Modifier
                            .fillMaxHeight(0.9f)
                            .aspectRatio(CARD_RATIO)
                            .border(3.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp)),
                    )
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp),
                    ) {
                        Text(
                            text = viewModel.status,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Ouvre la page de Cartilou dans les réglages d'Android (pour autoriser la caméra à la main). */
private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

// --- Version ---

/** La version mise au panier pour les prochaines cartes (mêmes choix que la saisie rapide). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersionChips(selected: QuickVersion, onSelect: (QuickVersion) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickVersion.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
            )
        }
    }
}

// --- Panier ---

@Composable
private fun BasketSection(
    items: List<BasketItem>,
    viewModel: ScannerViewModel,
    onSignIn: () -> Unit,
    modifier: Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val copies = items.sumOf { it.quantity }

    Column(modifier) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Panier", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (copies <= 1) "$copies exemplaire" else "$copies exemplaires",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Le panier est vide. Les cartes reconnues s'ajoutent ici ; rien n'est enregistré dans votre collection avant « Ajouter à ma collection ».",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(items, key = { it.key }) { item ->
                    BasketRow(item)
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
            ) { Text("Vider") }
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Vider le panier ?") },
            text = { Text("Les cartes du panier seront retirées. Rien n'a encore été ajouté à votre collection.") },
            confirmButton = {
                TextButton(onClick = {
                    ScanBasket.clear()
                    confirmClear = false
                }) { Text("Vider") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

/** Une ligne du panier : image, nom, numéro, version (à changer d'un appui), − nombre +. */
@Composable
private fun BasketRow(item: BasketItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardImage(item.image, item.name, quality = "low", modifier = Modifier.width(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "N° ${item.localId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** La version d'une ligne, en couleur d'accent ; un appui ouvre la liste des versions. */
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

// --- Choix entre plusieurs cartes ---

/** Plusieurs cartes portent ce nom et ce numéro (rééditions) : on montre leurs images pour choisir. */
@Composable
private fun ChoiceDialog(
    cards: List<CardBrief>,
    setNameOf: (CardBrief) -> String?,
    onChoose: (CardBrief) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quelle carte ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Plusieurs cartes portent ce nom et ce numéro. Touchez la bonne :")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(cards, key = { it.id }) { card ->
                        Column(
                            modifier = Modifier.clickable { onChoose(card) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CardImage(card.image, card.name, quality = "low", modifier = Modifier.fillMaxWidth())
                            Text(
                                text = setNameOf(card) ?: card.id,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Aucune de celles-ci") } },
    )
}
