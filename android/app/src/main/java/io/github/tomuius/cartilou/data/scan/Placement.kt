package io.github.tomuius.cartilou.data.scan

import android.graphics.RectF

/**
 * Recherche du meilleur placement (décision du 28/11/2026, option A).
 *
 * La carte tenue à la main ne tombe jamais pile dans le cadre-guide : un peu
 * décalée, un peu plus grande ou plus petite. Or l'empreinte (CardImageHash)
 * change beaucoup pour quelques millimètres d'écart. On garde donc, pour chaque
 * photo, une vignette un peu plus grande que le cadre ([MARGIN] de chaque
 * côté), et l'on y essaie plusieurs découpages : 9 tailles × 5 décalages
 * horizontaux × 5 décalages verticaux = 225 empreintes par photo. Pour chaque
 * carte candidate, on garde le découpage qui lui ressemble le plus.
 *
 * Revers : plus d'essais donnent aussi plus de chances à une mauvaise carte de
 * ressembler par hasard ; les seuils de décision sont à régler en conséquence.
 */
object Placement {
    /** Marge de la vignette autour du cadre, en part de la taille du cadre, de chaque côté. */
    const val MARGIN = 0.2f

    /**
     * Tailles essayées, par rapport au cadre (à revoir après des tests). Le bas
     * de l'échelle descend à 0,75 depuis le 28/11/2026 : en test, le meilleur
     * découpage de Mewtwo était à 0,85, la plus petite taille d'alors.
     */
    private val SCALES = floatArrayOf(0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1f, 1.05f, 1.1f, 1.15f)

    /** Décalages essayés, en part de la taille du cadre (à revoir après des tests). */
    private val OFFSETS = floatArrayOf(-0.1f, -0.05f, 0f, 0.05f, 0.1f)

    /**
     * Un découpage essayé : la vignette, la zone découpée (en points de la
     * vignette), la taille et les décalages correspondants, et son empreinte.
     */
    class Variant(
        val shot: GrayThumbnail,
        val area: RectF,
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val hash: Long,
    )

    /** Les empreintes de tous les découpages essayés dans [shot] (vignette du cadre élargi de MARGIN). */
    fun variantsOf(shot: GrayThumbnail): List<Variant> {
        // Le cadre occupe le centre de la vignette.
        val guideWidth = shot.width / (1 + 2 * MARGIN)
        val guideHeight = shot.height / (1 + 2 * MARGIN)
        val centerX = shot.width / 2f
        val centerY = shot.height / 2f
        return buildList {
            for (scale in SCALES) {
                for (offsetY in OFFSETS) {
                    for (offsetX in OFFSETS) {
                        val width = guideWidth * scale
                        val height = guideHeight * scale
                        val x = centerX + offsetX * guideWidth
                        val y = centerY + offsetY * guideHeight
                        val area = RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2)
                        add(Variant(shot, area, scale, offsetX, offsetY, CardImageHash.ofThumbnail(shot, area)))
                    }
                }
            }
        }
    }
}
