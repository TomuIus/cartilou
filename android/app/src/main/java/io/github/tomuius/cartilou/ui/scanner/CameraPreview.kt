package io.github.tomuius.cartilou.ui.scanner

import android.graphics.RectF
import android.util.Size
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.tomuius.cartilou.data.scan.CameraGray
import io.github.tomuius.cartilou.data.scan.CardLine
import io.github.tomuius.cartilou.data.scan.GrayThumbnail
import io.github.tomuius.cartilou.data.scan.MlKitCardTextReader
import io.github.tomuius.cartilou.data.scan.Placement
import io.github.tomuius.cartilou.data.scan.ReadText

/** Ce qu'a donné une image de la caméra. */
class ScanFrame(
    /** Les lignes lues dans le cadre-guide, du haut vers le bas de la carte. */
    val lines: List<CardLine>,
    /** Nombre de lignes lues hors du cadre, donc ignorées (pour l'encadré de test). */
    val ignoredLines: Int,
    /**
     * La zone autour du cadre-guide (élargie de Placement.MARGIN de chaque côté),
     * en petite image en niveaux de gris, pour la comparaison d'images ; null si
     * la place du cadre est encore inconnue.
     */
    val thumbnail: GrayThumbnail?,
)

/**
 * Taille de la vignette : 2 points par millimètre de carte, sur le cadre
 * élargi de 20 % de chaque côté (63 × 1,4 = 88 mm de large, 88 × 1,4 = 123 mm de haut).
 */
private const val THUMBNAIL_WIDTH = 176
private const val THUMBNAIL_HEIGHT = 246

/**
 * L'aperçu de la caméra, avec lecture du texte de chaque image.
 *
 * CameraX fait le gros du travail : un « contrôleur » (LifecycleCameraController)
 * ouvre la caméra arrière, affiche l'aperçu dans une PreviewView (vue Android
 * classique, insérée dans Compose avec AndroidView) et envoie les images à
 * analyser une par une : l'image suivante n'arrive qu'une fois la
 * précédente lue (on garde toujours la plus récente).
 *
 * Pour chaque image : on garde une vignette en niveaux de gris de la zone du
 * cadre-guide (un peu élargie), puis ML Kit lit le texte, dont on ne garde que
 * les lignes situées dans le cadre.
 *
 * La caméra s'ouvre quand cet élément apparaît à l'écran et se ferme quand il
 * disparaît (changement d'onglet), grâce à DisposableEffect.
 */
@Composable
fun CameraPreview(onFrame: (ScanFrame) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Toujours appeler la version la plus récente de onFrame, même si l'écran
    // est redessiné pendant que la caméra tourne.
    val currentOnFrame by rememberUpdatedState(onFrame)
    // Taille de l'aperçu à l'écran, pour situer le cadre-guide dans l'image analysée.
    val viewSize = remember { mutableStateOf(IntSize.Zero) }

    // « cameraController » et non « controller » : dans le bloc PreviewView plus
    // bas, « controller » désigne la propriété de la PreviewView elle-même.
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            // Aperçu (toujours actif) + analyse des images ; pas de prise de photo.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Aperçu et analyse au même format, 16:9 : ils filment alors le même
            // champ, et le cadre-guide de l'aperçu couvre la même zone de l'image
            // analysée (voir CardGuide.inImage).
            setPreviewResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            // Images d'analyse assez grandes pour lire le petit texte de la carte :
            // autour de 1920 × 1080 si la caméra le permet (à revoir après des
            // tests : plus grand = plus lisible mais plus lent).
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1920, 1080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val reader = MlKitCardTextReader()
        cameraController.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context)) { image ->
            // L'image en niveaux de gris, remise à l'endroit (portrait).
            val gray = CameraGray(image)
            val size = viewSize.value
            val guide = CardGuide.inImage(size.width, size.height, gray.width, gray.height)
            // La vignette est prise tout de suite, avant que la lecture du texte
            // ne referme l'image.
            val thumbnail = guide?.let {
                GrayThumbnail.of(gray, CardGuide.expanded(it, Placement.MARGIN), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
            }
            reader.read(image) { read -> currentOnFrame(frameOf(read, guide, thumbnail)) }
        }
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            reader.close()
        }
    }

    AndroidView(
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                controller = cameraController
                // L'image remplit toute la zone (quitte à être un peu rognée sur les bords).
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier.onSizeChanged { viewSize.value = it },
    )
}

/**
 * Ne garde que les lignes dont le centre est dans le cadre-guide (avec une
 * petite marge), et note la hauteur de chacune sur la carte.
 */
private fun frameOf(read: ReadText, guide: RectF?, thumbnail: GrayThumbnail?): ScanFrame {
    // Taille de l'aperçu pas encore connue : on garde tout, faute de mieux.
    if (guide == null) return ScanFrame(read.lines.map { CardLine(it.text, 0.5f) }, 0, null)
    val zone = CardGuide.withTextMargin(guide)
    val kept = mutableListOf<CardLine>()
    var ignored = 0
    for (line in read.lines) {
        val box = line.box
        if (box == null || !zone.contains(box.exactCenterX(), box.exactCenterY())) {
            ignored++
            continue
        }
        val position = ((box.exactCenterY() - guide.top) / guide.height()).coerceIn(0f, 1f)
        kept += CardLine(line.text, position)
    }
    kept.sortBy { it.position }
    return ScanFrame(kept, ignored, thumbnail)
}
