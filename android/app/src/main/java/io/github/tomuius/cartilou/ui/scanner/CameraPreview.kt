package io.github.tomuius.cartilou.ui.scanner

import android.util.Size
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.tomuius.cartilou.data.scan.MlKitCardTextReader

/**
 * L'aperçu de la caméra, avec lecture du texte de chaque image.
 *
 * CameraX fait le gros du travail : un « contrôleur » (LifecycleCameraController)
 * ouvre la caméra arrière, affiche l'aperçu dans une PreviewView (vue Android
 * classique, insérée dans Compose avec AndroidView) et envoie les images à
 * analyser une par une : l'image suivante n'arrive qu'une fois la
 * précédente lue (on garde toujours la plus récente).
 *
 * La caméra s'ouvre quand cet élément apparaît à l'écran et se ferme quand il
 * disparaît (changement d'onglet), grâce à DisposableEffect.
 *
 * [onText] reçoit le texte lu sur chaque image.
 */
@Composable
fun CameraPreview(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Toujours appeler la version la plus récente de onText, même si l'écran
    // est redessiné pendant que la caméra tourne.
    val currentOnText by rememberUpdatedState(onText)

    // « cameraController » et non « controller » : dans le bloc PreviewView plus
    // bas, « controller » désigne la propriété de la PreviewView elle-même.
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            // Aperçu (toujours actif) + analyse des images ; pas de prise de photo.
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Images d'analyse assez grandes pour lire le petit numéro en bas de
            // la carte : autour de 1920 × 1080 si la caméra le permet (à revoir
            // après des tests : plus grand = plus lisible mais plus lent).
            setImageAnalysisResolutionSelector(
                ResolutionSelector.Builder()
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
            reader.read(image) { text -> currentOnText(text) }
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
        modifier = modifier,
    )
}
