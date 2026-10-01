package app.nanogone.ai

import android.content.Context
import app.nanogone.editor.RepairEngine
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.mask.Mask

/**
 * The fast brain: LaMa (Qualcomm LaMa-Dilated build), 512 x 512 in and out.
 * The crop is scaled so its longer side is 512 (small crops are scaled up, so tiny objects get
 * the model's full attention), the rest of the square is filled by repeating the edge and
 * marked as known, and only the filled pixels are scaled back to full detail.
 */
class LamaEngine(context: Context) : RepairEngine, AutoCloseable {

    private val model = TfliteModel(context, ASSET)
    override val name = "LaMa on ${model.backend}"

    private val image = FloatArray(S * S * 3)
    private val holes = FloatArray(S * S)

    @Synchronized
    override fun repair(crop: Argb, mask: Mask): Argb {
        val scale = S.toFloat() / maxOf(crop.width, crop.height)
        val sw = minOf(S, Math.round(crop.width * scale).coerceAtLeast(1))
        val sh = minOf(S, Math.round(crop.height * scale).coerceAtLeast(1))
        for (y in 0 until S) for (x in 0 until S) {
            val i = y * S + x
            // Inside the scaled crop: sample it; outside: repeat the nearest edge (known pixels).
            val cx = minOf(x, sw - 1)
            val cy = minOf(y, sh - 1)
            val fx = ((cx + 0.5f) / scale - 0.5f).coerceIn(0f, crop.width - 1f)
            val fy = ((cy + 0.5f) / scale - 0.5f).coerceIn(0f, crop.height - 1f)
            val hole = x < sw && y < sh && maskAt(mask, fx, fy)
            val p = Bilinear.sample(crop, fx, fy)
            image[i * 3] = if (hole) 0f else ((p shr 16) and 0xFF) / 255f
            image[i * 3 + 1] = if (hole) 0f else ((p shr 8) and 0xFF) / 255f
            image[i * 3 + 2] = if (hole) 0f else (p and 0xFF) / 255f
            holes[i] = if (hole) 1f else 0f
        }
        val out = model.run(image, holes)[0]
        val painted = Argb(S, S)
        for (i in 0 until S * S) {
            val r = (out[i * 3] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = (out[i * 3 + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (out[i * 3 + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)
            painted.px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val result = crop.copy()
        for (y in 0 until crop.height) for (x in 0 until crop.width) {
            if (mask[x, y]) {
                result[x, y] = Bilinear.sample(painted, ((x + 0.5f) * scale - 0.5f).coerceIn(0f, sw - 1f), ((y + 0.5f) * scale - 0.5f).coerceIn(0f, sh - 1f))
            }
        }
        return result
    }

    /** True if the area of the crop that this model pixel covers touches the hole. */
    private fun maskAt(m: Mask, fx: Float, fy: Float): Boolean {
        val x = fx.toInt().coerceIn(0, m.width - 1)
        val y = fy.toInt().coerceIn(0, m.height - 1)
        if (m[x, y]) return true
        val x1 = (x + 1).coerceAtMost(m.width - 1)
        val y1 = (y + 1).coerceAtMost(m.height - 1)
        return m[x1, y] || m[x, y1] || m[x1, y1]
    }

    override fun close() = model.close()

    companion object {
        const val ASSET = "models/lama.tflite"
        private const val S = 512
    }
}

/** Shared bilinear sampling of ARGB images. */
object Bilinear {
    fun sample(img: Argb, fx: Float, fy: Float): Int {
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
