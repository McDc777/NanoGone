package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps

/** Puts a repaired crop back into the photo. Pixels outside the mask are never written. */
object Paste {

    /**
     * Copy [src] into [dst] at ([atX], [atY]) where [mask] (same size as [src]) is on.
     * Near the mask edge the new pixels fade in over [feather] pixels, measured inward,
     * so the seam is soft but nothing outside the mask changes.
     */
    fun feathered(dst: Argb, src: Argb, atX: Int, atY: Int, mask: Mask, feather: Float) {
        require(mask.width == src.width && mask.height == src.height) { "mask must match src" }
        require(atX >= 0 && atY >= 0 && atX + src.width <= dst.width && atY + src.height <= dst.height) {
            "paste area outside the destination"
        }
        val dist = if (feather > 0f) MaskOps.distanceToOff(mask) else null
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val i = y * src.width + x
                if (!mask.bits[i]) continue
                val a = if (dist == null) 1f else minOf(1f, dist[i] / feather)
                val di = (atY + y) * dst.width + atX + x
                dst.px[di] = if (a >= 1f) src.px[i] else mix(dst.px[di], src.px[i], a)
            }
        }
    }

    private fun mix(under: Int, over: Int, a: Float): Int {
        fun ch(shift: Int): Int {
            val u = (under ushr shift) and 0xFF
            val o = (over ushr shift) and 0xFF
            return (u + (o - u) * a + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
