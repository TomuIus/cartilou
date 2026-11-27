package io.github.tomuius.cartilou.ui.scanner

import android.graphics.RectF
import kotlin.math.max

/**
 * Le cadre-guide dessiné sur l'aperçu de la caméra, et la zone qu'il couvre
 * dans l'image analysée.
 *
 * Il sert à deux choses (décisions du 27/11/2026) : ne lire que le texte de la
 * carte placée dans le cadre, pas celui des cartes posées à côté ; et découper
 * la carte pour comparer son image à celles des cartes candidates.
 */
object CardGuide {
    /** Proportions d'une carte Pokémon (63 × 88 mm). */
    const val RATIO = 63f / 88f

    /** Hauteur du cadre, en part de la hauteur de l'aperçu. */
    const val HEIGHT_FRACTION = 0.9f

    /**
     * Marge autour du cadre dans laquelle une ligne de texte est encore gardée
     * (en part de la taille du cadre) : une carte tenue à la main ne tombe
     * jamais pile dans le cadre. À revoir après des tests.
     */
    private const val TEXT_MARGIN = 0.08f

    /** Le cadre dans l'aperçu, en pixels de l'aperçu : centré, comme dessiné dans ScannerScreen.kt. */
    fun inView(viewWidth: Float, viewHeight: Float): RectF {
        var height = viewHeight * HEIGHT_FRACTION
        var width = height * RATIO
        if (width > viewWidth) {
            width = viewWidth
            height = width / RATIO
        }
        val left = (viewWidth - width) / 2
        val top = (viewHeight - height) / 2
        return RectF(left, top, left + width, top + height)
    }

    /**
     * Le cadre dans l'image analysée, remise à l'endroit ([imageWidth] ×
     * [imageHeight] pixels), ou null si une taille est encore inconnue.
     *
     * L'aperçu montre l'image agrandie juste assez pour remplir toute sa zone,
     * centrée, quitte à en rogner les bords (mode FILL_CENTER) : on fait le
     * calcul inverse. Suppose que l'aperçu et l'analyse filment le même champ :
     * c'est pourquoi les deux sont réglés au même format 16:9 (CameraPreview.kt).
     */
    fun inImage(viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int): RectF? {
        if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
        val scale = max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        val offsetX = (viewWidth - imageWidth * scale) / 2
        val offsetY = (viewHeight - imageHeight * scale) / 2
        val guide = inView(viewWidth.toFloat(), viewHeight.toFloat())
        return RectF(
            (guide.left - offsetX) / scale,
            (guide.top - offsetY) / scale,
            (guide.right - offsetX) / scale,
            (guide.bottom - offsetY) / scale,
        )
    }

    /** Le cadre élargi de sa marge, pour trier les lignes de texte. */
    fun withTextMargin(guide: RectF): RectF = expanded(guide, TEXT_MARGIN)

    /** Le cadre élargi de [fraction] de sa taille de chaque côté, autour du même centre. */
    fun expanded(guide: RectF, fraction: Float): RectF {
        val dx = guide.width() * fraction
        val dy = guide.height() * fraction
        return RectF(guide.left - dx, guide.top - dy, guide.right + dx, guide.bottom + dy)
    }
}
