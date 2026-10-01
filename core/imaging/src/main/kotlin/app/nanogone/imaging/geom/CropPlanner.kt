package app.nanogone.imaging.geom

import kotlin.math.roundToInt

/** Picks the box of the photo that a repair model looks at. */
object CropPlanner {

    /**
     * Grow [maskBounds] on every side by max([minPad], [contextScale] x its longer side),
     * so the model sees enough surroundings, then clamp to the image.
     */
    fun contextBox(
        maskBounds: IntRect,
        imageW: Int,
        imageH: Int,
        minPad: Int = 32,
        contextScale: Float = 0.75f,
    ): IntRect {
        val pad = maxOf(minPad, (contextScale * maxOf(maskBounds.width, maskBounds.height)).roundToInt())
        return IntRect(
            maxOf(0, maskBounds.left - pad),
            maxOf(0, maskBounds.top - pad),
            minOf(imageW, maskBounds.right + pad),
            minOf(imageH, maskBounds.bottom + pad),
        )
    }

    /** Snap a rectangle outward to a grid (for example JPEG MCUs), then clamp to the image. */
    fun alignOut(r: IntRect, gridW: Int, gridH: Int, imageW: Int, imageH: Int): IntRect = IntRect(
        maxOf(0, Math.floorDiv(r.left, gridW) * gridW),
        maxOf(0, Math.floorDiv(r.top, gridH) * gridH),
        minOf(imageW, Math.floorDiv(r.right + gridW - 1, gridW) * gridW),
        minOf(imageH, Math.floorDiv(r.bottom + gridH - 1, gridH) * gridH),
    )
}
