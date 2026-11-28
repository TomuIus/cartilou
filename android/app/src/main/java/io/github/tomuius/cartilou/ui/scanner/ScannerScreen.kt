package io.github.tomuius.cartilou.ui.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
import io.github.tomuius.cartilou.data.tcgdex.CardSetBrief
import io.github.tomuius.cartilou.ui.common.CardImage
import io.github.tomuius.cartilou.ui.common.CartilouTopBar
import io.github.tomuius.cartilou.ui.common.QuickVersion

// L'onglet Scanner (décisions du 27/11/2026) : la caméra lit les cartes en
// continu (option B) ; les cartes reconnues s'ajoutent aux « cartes scannées »
// (ancien « panier », option C), qu'on vérifie avant de tout ajouter à la
// collection. Quand plusieurs cartes sont possibles, leurs images sont
// comparées à la photo (option C, puis A : empreinte d'image maison).
// Disposition : l'aperçu de la caméra en haut, le choix de la version au
// milieu, puis l'extension et les cartes scannées (avec, si l'on active
// « Tests », le texte lu au-dessus). « Vérifier et ajouter » ouvre les cartes
// scannées en grand (ScannedCards.kt), d'où l'on ajoute tout à la collection.

/**
 * Outils de test (décisions du 27 et du 28/11/2026) : l'interrupteur « Tests »
 * dans la barre du haut, qui affiche l'encadré du texte lu (masqué par
 * défaut). À passer à false (ou à retirer avec ReadTextPanel) une fois les
 * réglages du scan terminés.
 */
private const val TEST_TOOLS = true

@Composable
fun ScannerScreen(onReview: () -> Unit) {
    // Le contexte de l'appli entière (et non de cet écran) : le ViewModel peut
    // le garder sans empêcher Android de libérer l'écran.
    val appContext = LocalContext.current.applicationContext
    val viewModel = viewModel { ScannerViewModel(appContext) }
    val items by ScanBasket.items.collectAsStateWithLifecycle()
    var showSetPicker by rememberSaveable { mutableStateOf(false) }

    // Petite vibration à chaque carte ajoutée aux cartes scannées.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(viewModel.addedCount) {
        if (viewModel.addedCount > 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            CartilouTopBar(
                title = "Scanner",
                actions = {
                    if (TEST_TOOLS) {
                        FilterChip(
                            selected = viewModel.showTests,
                            onClick = viewModel::toggleTests,
                            label = { Text("Tests") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // weight : la caméra et les cartes scannées se partagent la hauteur,
            // moitié-moitié (à revoir après des tests).
            CameraSection(viewModel, Modifier.fillMaxWidth().weight(1f))
            VersionChips(
                selected = viewModel.selected,
                onSelect = { viewModel.selected = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            // Le bas de l'écran : l'extension, l'encadré de test s'il est affiché,
            // puis les cartes scannées. L'encadré prend sa place sur cette liste et
            // non sur la caméra : la taille de l'aperçu décide de la taille de la
            // carte dans l'image analysée.
            Column(Modifier.fillMaxWidth().weight(1f)) {
                // Placé ici (et non au-dessus des versions) pour ne pas réduire la caméra.
                SetSelector(
                    selected = viewModel.selectedSet,
                    onOpen = { showSetPicker = true },
                    onClear = { viewModel.selectSet(null) },
                )
                if (TEST_TOOLS && viewModel.showTests) {
                    ReadTextPanel(
                        text = viewModel.testText,
                        thumbnail = viewModel.testThumbnail,
                        frozen = viewModel.testTextFrozen,
                        onToggleFreeze = viewModel::toggleTestTextFrozen,
                    )
                }
                ScannedCardsSection(items, onReview, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    if (showSetPicker) {
        SetPickerDialog(
            onPick = { set ->
                viewModel.selectSet(set)
                showSetPicker = false
            },
            onDismiss = { showSetPicker = false },
        )
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
                    CameraPreview(onFrame = viewModel::onFrame, modifier = Modifier.fillMaxSize())
                    // Cadre-guide de la forme d'une carte (même calcul que CardGuide.inView).
                    Box(
                        Modifier
                            .fillMaxHeight(CardGuide.HEIGHT_FRACTION)
                            .aspectRatio(CardGuide.RATIO)
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

// --- Extension ---

/**
 * « Extension : Toutes ▾ » ; un appui ouvre le choix de l'extension. Avec une
 * extension choisie, une croix permet de revenir à toutes les extensions.
 */
@Composable
private fun SetSelector(selected: CardSetBrief?, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Extension :", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        AssistChip(
            onClick = onOpen,
            label = { Text((selected?.name ?: "Toutes") + " ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.weight(1f, fill = false),
        )
        if (selected != null) {
            IconButton(onClick = onClear) {
                Icon(painterResource(R.drawable.ic_fermer), contentDescription = "Chercher dans toutes les extensions")
            }
        }
    }
}

// --- Version ---

/** La version donnée aux prochaines cartes scannées (mêmes choix que la saisie rapide). */
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

// --- Texte lu (tests) ---

/**
 * L'encadré de test : le texte que ML Kit lit dans le cadre sur l'image en
 * cours, tel quel (une ligne par ligne lue), le nombre de lignes ignorées hors
 * du cadre, et les distances de la dernière comparaison d'images. À gauche,
 * [thumbnail] : le découpage de la caméra qui ressemblait le plus à la première
 * carte du classement, en noir et blanc, pour vérifier qu'il tombe bien sur la carte.
 * « Figer » arrête de le mettre à jour, pour le lire ou le copier ;
 * « Copier » le met dans le presse-papiers (pour le coller dans un message).
 */
@Composable
private fun ReadTextPanel(text: String, thumbnail: ImageBitmap?, frozen: Boolean, onToggleFreeze: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Texte lu (pour les tests)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggleFreeze) { Text(if (frozen) "Reprendre" else "Figer") }
            TextButton(onClick = { copyToClipboard(context, text) }, enabled = text.isNotEmpty()) { Text("Copier") }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            // Hauteur fixe (à revoir après des tests) ; le texte défile à l'intérieur.
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
        ) {
            Row {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = "Zone de la caméra comparée aux images des cartes",
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxHeight()
                            .aspectRatio(CardGuide.RATIO),
                    )
                }
                Text(
                    text = text.ifEmpty { "(en attente d'une image)" },
                    style = MaterialTheme.typography.bodySmall,
                    // Police à chasse fixe : on distingue mieux « 0 » et « O », « 1 », « l » et « I ».
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                )
            }
        }
    }
}

/** Met [text] dans le presse-papiers d'Android. */
private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("Texte lu par Cartilou", text))
    // Depuis Android 13, le système affiche lui-même une confirmation de copie.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Texte copié", Toast.LENGTH_SHORT).show()
    }
}

// --- Cartes scannées ---

/**
 * Les cartes scannées, en petit sous la caméra : on peut déjà y corriger le
 * nombre et la version. « Vérifier et ajouter » ouvre l'écran en grand, d'où
 * l'on ajoute tout à la collection.
 */
@Composable
private fun ScannedCardsSection(items: List<BasketItem>, onReview: () -> Unit, modifier: Modifier) {
    val copies = items.sumOf { it.quantity }
    Column(modifier) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Cartes scannées", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                copiesLabel(copies),
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
                    "Aucune carte scannée pour l'instant. Rien n'est ajouté à votre collection avant « Vérifier et ajouter ».",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(items, key = { it.key }) { item ->
                    ScannedCardRow(item, setName = null, imageWidth = 44.dp)
                    HorizontalDivider()
                }
            }
        }
        Button(
            onClick = onReview,
            enabled = items.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) { Text("Vérifier et ajouter") }
    }
}

// --- Choix entre plusieurs cartes ---

/**
 * Plusieurs cartes possibles (rééditions, ou cartes trouvées par le nom seul ou
 * le numéro seul) et pas de gagnante nette à la comparaison d'images : on
 * montre leurs images, de la plus ressemblante à la moins ressemblante.
 */
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
                Text("Plusieurs cartes sont possibles, de la plus ressemblante à la moins ressemblante. Touchez la bonne :")
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
