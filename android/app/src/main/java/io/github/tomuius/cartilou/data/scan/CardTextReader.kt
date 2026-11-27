package io.github.tomuius.cartilou.data.scan

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Lit le texte d'une image de la caméra.
 *
 * Une « interface » : le reste de l'appli ne connaît que ceci, pas l'outil
 * utilisé derrière. Passer un jour de ML Kit à Tesseract (open source) ne
 * demandera qu'une autre classe qui suit ce modèle (décision du 27/11/2026).
 */
interface CardTextReader : AutoCloseable {
    /**
     * Lit [image] puis appelle [onText] avec tout le texte trouvé (une ligne
     * par ligne lue). Doit fermer l'image une fois fini, lecture réussie ou non :
     * CameraX n'envoie l'image suivante qu'après.
     */
    fun read(image: ImageProxy, onText: (String) -> Unit)
}

/**
 * Lecture avec ML Kit (Google), modèle de l'alphabet latin intégré à l'appli :
 * tout se passe sur le téléphone, sans réseau ; l'image n'est envoyée nulle part.
 */
class MlKitCardTextReader : CardTextReader {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // @OptIn : « image.image » (l'image brute d'Android sous l'ImageProxy de
    // CameraX) est marquée expérimentale par CameraX ; c'est pourtant la
    // façon indiquée pour la passer à ML Kit.
    @OptIn(ExperimentalGetImage::class)
    override fun read(image: ImageProxy, onText: (String) -> Unit) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        // La rotation indique à ML Kit dans quel sens tenir l'image (téléphone
        // en portrait, capteur souvent en paysage).
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result -> onText(result.text) }
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        recognizer.close()
    }
}
