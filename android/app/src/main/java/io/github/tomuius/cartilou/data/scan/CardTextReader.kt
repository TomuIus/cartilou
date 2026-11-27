package io.github.tomuius.cartilou.data.scan

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Une ligne de texte lue, avec sa place dans l'image (null si l'outil ne la donne pas). */
data class TextLine(val text: String, val box: Rect?)

/**
 * Le texte lu sur une image, ligne par ligne. Les positions sont données dans
 * l'image remise à l'endroit (téléphone en portrait), de largeur [width] et de
 * hauteur [height] pixels.
 */
class ReadText(val lines: List<TextLine>, val width: Int, val height: Int)

/**
 * Lit le texte d'une image de la caméra.
 *
 * Une « interface » : le reste de l'appli ne connaît que ceci, pas l'outil
 * utilisé derrière. Passer un jour de ML Kit à Tesseract (open source) ne
 * demandera qu'une autre classe qui suit ce modèle (décision du 27/11/2026).
 */
interface CardTextReader : AutoCloseable {
    /**
     * Lit [image] puis appelle [onRead] avec les lignes trouvées et leur place.
     * Doit fermer l'image une fois fini, lecture réussie ou non : CameraX
     * n'envoie l'image suivante qu'après.
     */
    fun read(image: ImageProxy, onRead: (ReadText) -> Unit)
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
    override fun read(image: ImageProxy, onRead: (ReadText) -> Unit) {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        // La rotation indique à ML Kit dans quel sens tenir l'image (téléphone
        // en portrait, capteur souvent en paysage).
        val rotation = image.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        // Taille de l'image remise à l'endroit : largeur et hauteur échangées
        // quand elle est tournée d'un quart de tour. Hypothèse (à vérifier en
        // test, grâce à l'encadré « Texte lu ») : ML Kit donne la place des
        // lignes dans cette image remise à l'endroit.
        val turned = rotation % 180 != 0
        val width = if (turned) image.height else image.width
        val height = if (turned) image.width else image.height
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { line -> TextLine(line.text, line.boundingBox) }
                }
                onRead(ReadText(lines, width, height))
            }
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        recognizer.close()
    }
}
