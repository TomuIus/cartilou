package io.github.tomuius.cartilou.data.scan

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageProxy

/**
 * L'« empreinte » d'une image de carte (méthode dHash, décision du
 * 27/11/2026, option A) : 64 bits qui résument l'image, pour comparer la carte
 * filmée aux images TCGdex des cartes candidates. Aucune bibliothèque : tout
 * le calcul est ici.
 *
 * Calcul :
 * 1. l'image, en niveaux de gris, est découpée en une grille de 9 colonnes sur
 *    8 lignes, et l'on fait la moyenne de chaque case ;
 * 2. dans chaque ligne de la grille, on compare chaque case à sa voisine de
 *    droite : 1 si la voisine est plus claire, 0 sinon. 8 comparaisons sur
 *    8 lignes : 64 bits.
 *
 * Deux images semblables ont des empreintes proches : on compte les bits qui
 * diffèrent ([distance] : 0 = identiques, 64 = tout diffère ; deux images sans
 * rapport tournent autour de 32). Comme on ne compare que des différences de
 * luminosité entre voisines, un éclairage plus fort ou plus faible sur toute la
 * carte ne change presque rien. En revanche, une carte penchée, décalée dans le
 * cadre ou couverte d'un reflet fausse le résultat.
 */
object CardImageHash {
    private const val COLUMNS = 9
    private const val ROWS = 8

    /** Points mesurés dans chaque case, dans chaque sens (8 × 8 = 64 points par case). */
    private const val SAMPLES = 8

    /** Nombre de bits qui diffèrent entre deux empreintes (0 à 64). */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Empreinte d'une image TCGdex : toute l'image, qui ne montre que la carte. */
    fun ofBitmap(bitmap: Bitmap): Long {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return hash(0f, 0f, width.toFloat(), height.toFloat(), width - 1, height - 1) { x, y ->
            val color = pixels[y * width + x]
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            // Luminosité perçue (le vert compte le plus, le bleu le moins).
            (299 * red + 587 * green + 114 * blue) / 1000
        }
    }

    /**
     * Empreinte de la zone [area] d'une vignette de la caméra (voir
     * GrayThumbnail et Placement) ; [area] est en points de la vignette.
     */
    fun ofThumbnail(thumbnail: GrayThumbnail, area: RectF): Long =
        hash(area.left, area.top, area.right, area.bottom, thumbnail.width - 1, thumbnail.height - 1) { x, y ->
            thumbnail.at(x, y)
        }

    /**
     * Le calcul commun : grille de moyennes sur la zone (left, top) – (right,
     * bottom), puis comparaison des voisines. [luminance] donne la luminosité
     * (0 à 255) d'un point ; les points sont gardés entre 0 et [maxX] / [maxY].
     */
    private inline fun hash(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        maxX: Int,
        maxY: Int,
        luminance: (x: Int, y: Int) -> Int,
    ): Long {
        val cellWidth = (right - left) / COLUMNS
        val cellHeight = (bottom - top) / ROWS
        val grid = FloatArray(COLUMNS * ROWS)
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS) {
                var sum = 0
                for (sampleY in 0 until SAMPLES) {
                    for (sampleX in 0 until SAMPLES) {
                        // Points répartis régulièrement dans la case.
                        val x = (left + (column + (sampleX + 0.5f) / SAMPLES) * cellWidth).toInt().coerceIn(0, maxX)
                        val y = (top + (row + (sampleY + 0.5f) / SAMPLES) * cellHeight).toInt().coerceIn(0, maxY)
                        sum += luminance(x, y)
                    }
                }
                grid[row * COLUMNS + column] = sum.toFloat() / (SAMPLES * SAMPLES)
            }
        }
        var bits = 0L
        for (row in 0 until ROWS) {
            for (column in 0 until COLUMNS - 1) {
                bits = bits shl 1
                if (grid[row * COLUMNS + column + 1] > grid[row * COLUMNS + column]) bits = bits or 1L
            }
        }
        return bits
    }
}

/**
 * Une image de la caméra en niveaux de gris, lue point par point comme si elle
 * était remise à l'endroit (portrait), de [width] × [height] pixels.
 *
 * On lit directement le plan « Y » de l'image : c'est sa version en niveaux de
 * gris, sans conversion. À utiliser avant que l'image ne soit refermée.
 */
class CameraGray(image: ImageProxy) {
    private val plane = image.planes[0]
    private val buffer = plane.buffer
    private val rowStride = plane.rowStride
    private val pixelStride = plane.pixelStride
    private val rawWidth = image.width
    private val rawHeight = image.height
    private val rotation = image.imageInfo.rotationDegrees

    val width = if (rotation % 180 != 0) rawHeight else rawWidth
    val height = if (rotation % 180 != 0) rawWidth else rawHeight

    /** Luminosité (0 à 255) du point (x, y) de l'image remise à l'endroit ; un point hors de l'image est ramené au bord. */
    fun at(x: Int, y: Int): Int {
        val uprightX = x.coerceIn(0, width - 1)
        val uprightY = y.coerceIn(0, height - 1)
        // Même point dans l'image brute du capteur, avant rotation.
        val rawX: Int
        val rawY: Int
        when (rotation) {
            90 -> { rawX = uprightY; rawY = rawHeight - 1 - uprightX }
            180 -> { rawX = rawWidth - 1 - uprightX; rawY = rawHeight - 1 - uprightY }
            270 -> { rawX = rawWidth - 1 - uprightY; rawY = uprightX }
            else -> { rawX = uprightX; rawY = uprightY }
        }
        return buffer.get(rawY * rowStride + rawX * pixelStride).toInt() and 0xFF
    }
}

/**
 * Une petite image en niveaux de gris (une valeur de 0 à 255 par point) : la
 * zone de la caméra autour du cadre-guide, gardée pour la comparaison d'images
 * (voir Placement) et montrée dans l'encadré de test.
 */
class GrayThumbnail(val width: Int, val height: Int, private val pixels: ByteArray) {

    /** Luminosité (0 à 255) du point (x, y) ; un point hors de la vignette est ramené au bord. */
    fun at(x: Int, y: Int): Int =
        pixels[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)].toInt() and 0xFF

    /** La zone [area] de cette vignette, réduite (ou agrandie) à [width] × [height] points. */
    fun crop(area: RectF, width: Int, height: Int): GrayThumbnail {
        val pixels = ByteArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceX = (area.left + (x + 0.5f) * area.width() / width).toInt()
                val sourceY = (area.top + (y + 0.5f) * area.height() / height).toInt()
                pixels[y * width + x] = at(sourceX, sourceY).toByte()
            }
        }
        return GrayThumbnail(width, height, pixels)
    }

    fun toBitmap(): Bitmap {
        val colors = IntArray(pixels.size) { index ->
            val value = pixels[index].toInt() and 0xFF
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        return Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
    }

    companion object {
        /** La zone [area] de [gray], réduite à [width] × [height] points. */
        fun of(gray: CameraGray, area: RectF, width: Int, height: Int): GrayThumbnail {
            val pixels = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val sourceX = (area.left + (x + 0.5f) * area.width() / width).toInt()
                    val sourceY = (area.top + (y + 0.5f) * area.height() / height).toInt()
                    pixels[y * width + x] = gray.at(sourceX, sourceY).toByte()
                }
            }
            return GrayThumbnail(width, height, pixels)
        }
    }
}
