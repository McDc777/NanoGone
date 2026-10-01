package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps
import kotlin.random.Random

/**
 * Makes a repaired area as grainy as the photo around it. AI fills tend to be smoother than real
 * camera pictures; this copies real fine texture (the difference between each pixel and its
 * local average) from a ring around the hole into the fill. Pixels outside [mask] never change.
 */
object Grain {

    /**
     * @param filled the repaired crop (fill inside [mask]).
     * @param mask the hole, same size as [filled].
     * @param seed keeps the result the same every time for the same input.
     */
    fun match(filled: Argb, mask: Mask, seed: Int = 7): Argb {
        val w = filled.width
        val h = filled.height
        // Ring: pixels outside the hole, 2 to 14 pixels away from it.
        val inv = Mask(w, h, BooleanArray(w * h) { !mask.bits[it] })
        val dOut = MaskOps.distanceToOff(inv)
        val ring = ArrayList<Int>()
        for (i in 0 until w * h) if (!mask.bits[i] && dOut[i] in 2f..14f) ring.add(i)
        if (ring.size < 16) return filled.copy()
        val detail = highPass(filled)
        // Copy grain from the ring; the fill's own (smooth) detail is replaced, not added to.
        val rnd = Random(seed)
        val out = filled.copy()
        val dIn = MaskOps.distanceToOff(mask)
        for (i in 0 until w * h) {
            if (!mask.bits[i]) continue
            val src = ring[rnd.nextInt(ring.size)]
            val fade = minOf(1f, dIn[i] / 2f)
            val base = blurAt(filled, i % w, i / w)
            var px = 0xFF shl 24
            for ((k, shift) in intArrayOf(16, 8, 0).withIndex()) {
                val own = ((filled.px[i] shr shift) and 0xFF)
                val target = (((base shr shift) and 0xFF) + detail[src * 3 + k]).coerceIn(0f, 255f)
                val v = own + (target - own) * fade
                px = px or ((v + 0.5f).toInt().coerceIn(0, 255) shl shift)
            }
            out.px[i] = px
        }
        return out
    }

    /** Per-pixel, per-channel difference from the 3 x 3 average. */
    private fun highPass(img: Argb): FloatArray {
        val out = FloatArray(img.width * img.height * 3)
        for (y in 0 until img.height) for (x in 0 until img.width) {
            val b = blurAt(img, x, y)
            val p = img[x, y]
            val i = (y * img.width + x) * 3
            out[i] = (((p shr 16) and 0xFF) - ((b shr 16) and 0xFF)).toFloat()
            out[i + 1] = (((p shr 8) and 0xFF) - ((b shr 8) and 0xFF)).toFloat()
            out[i + 2] = ((p and 0xFF) - (b and 0xFF)).toFloat()
        }
        return out
    }

    private fun blurAt(img: Argb, x: Int, y: Int): Int {
        var r = 0; var g = 0; var b = 0; var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until img.width && yy in 0 until img.height) {
                val p = img[xx, yy]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
            }
        }
        return (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }
}
