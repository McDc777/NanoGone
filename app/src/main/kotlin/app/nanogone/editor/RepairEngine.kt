package app.nanogone.editor

import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.image.Inpaint
import app.nanogone.imaging.mask.Mask

/** Fills the masked part of a crop. AI brains implement this; [SmoothFillEngine] is the safety net. */
interface RepairEngine {
    val name: String
    fun repair(crop: Argb, mask: Mask): Argb
}

/**
 * Works on a smaller copy when the crop is big, then scales only the filled pixels back up,
 * so the pixels outside the mask always come from the full-detail crop.
 */
abstract class ScaledRepairEngine(private val workSide: Int) : RepairEngine {

    protected abstract fun repairSmall(crop: Argb, mask: Mask): Argb

    override fun repair(crop: Argb, mask: Mask): Argb {
        val longSide = maxOf(crop.width, crop.height)
        if (longSide <= workSide) return repairSmall(crop, mask)
        val f = (longSide + workSide - 1) / workSide
        val sw = (crop.width + f - 1) / f
        val sh = (crop.height + f - 1) / f
        val small = Argb(sw, sh)
        val smallMask = Mask(sw, sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            var r = 0; var g = 0; var b = 0; var n = 0; var any = false
            for (dy in 0 until f) for (dx in 0 until f) {
                val sx = x * f + dx
                val sy = y * f + dy
                if (sx < crop.width && sy < crop.height) {
                    val p = crop[sx, sy]
                    r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                    if (mask[sx, sy]) any = true
                }
            }
            small[x, y] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            smallMask[x, y] = any
        }
        val filled = repairSmall(small, smallMask)
        val out = crop.copy()
        for (y in 0 until crop.height) for (x in 0 until crop.width) {
            if (mask[x, y]) out[x, y] = bilinear(filled, (x + 0.5f) / f - 0.5f, (y + 0.5f) / f - 0.5f)
        }
        return out
    }

    private fun bilinear(img: Argb, fx: Float, fy: Float): Int {
        val x0 = fx.toInt().coerceIn(0, img.width - 1)
        val y0 = fy.toInt().coerceIn(0, img.height - 1)
        val x1 = (x0 + 1).coerceAtMost(img.width - 1)
        val y1 = (y0 + 1).coerceAtMost(img.height - 1)
        val ax = (fx - x0).coerceIn(0f, 1f)
        val ay = (fy - y0).coerceIn(0f, 1f)
        val p00 = img[x0, y0]; val p10 = img[x1, y0]; val p01 = img[x0, y1]; val p11 = img[x1, y1]
        fun ch(shift: Int): Int {
            val a = (p00 shr shift) and 0xFF
            val b = (p10 shr shift) and 0xFF
            val c = (p01 shr shift) and 0xFF
            val d = (p11 shr shift) and 0xFF
            val top = a + (b - a) * ax
            val bottom = c + (d - c) * ax
            return ((top + (bottom - top) * ay) + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

/** No-AI fill: smooth colours from the edges inward. */
class SmoothFillEngine : ScaledRepairEngine(workSide = 768) {
    override val name = "smooth fill"
    override fun repairSmall(crop: Argb, mask: Mask): Argb = Inpaint.smoothFill(crop, mask)
}
