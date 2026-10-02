package app.nanogone.ai

import android.content.Context
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.image.AutoTone

/** What the Enhance panel asks for. */
data class EnhanceOptions(
    val lightAndColour: Boolean = true,
    val sharper: Boolean = false,
    /** 1 = same size, 2 or 4 = bigger. */
    val bigger: Int = 1,
    /** 0..1, how strong Light and colour, Sharper and Face fix are. */
    val strength: Float = 0.8f,
    /** Restore faces (GFPGAN). */
    val faceFix: Boolean = false,
)

/**
 * Enhance. Light and colour is a careful tone pass; Sharper and Bigger use Real-ESRGAN x4
 * (Qualcomm build) on 128 x 128 tiles with overlap, then scale the result to the asked size,
 * so the whole photo gets real detail, not just a blur-up.
 */
class Enhancer(context: Context) : AutoCloseable {

    private val esrgan: TfliteModel? =
        if (TfliteModel.exists(context, ASSET)) runCatching { TfliteModel(context, ASSET) }.getOrNull() else null
    val canUpscale: Boolean get() = esrgan != null

    private val faces: FaceFixer? = runCatching { FaceFixer(context) }.getOrNull()
    val canFixFaces: Boolean get() = faces?.available == true
    val backend: String get() = esrgan?.backend ?: "none"

    fun enhance(img: Argb, preview: Argb, o: EnhanceOptions, progress: (Float) -> Unit): Argb {
        var cur = img
        if (o.lightAndColour) cur = AutoTone.analyse(preview).apply(cur, o.strength)
        progress(0.05f)
        val faceShare = if (o.faceFix && canFixFaces) 0.3f else 0f
        if ((o.sharper || o.bigger > 1) && esrgan != null) {
            val restored = upscale(cur, o.bigger) { progress(0.05f + (0.95f - faceShare) * (it - 0.05f) / 0.95f) }
            cur = if (o.bigger == 1) blend(cur, restored, o.strength) else restored
        }
        if (faceShare > 0f) {
            val start = 1f - faceShare
            cur = faces!!.fix(cur, o.strength) { progress(start + faceShare * it) }
        }
        progress(1f)
        return cur
    }

    /** Real detail for a small image, [scale] 2 or 4 times bigger. Null if the brain is missing. */
    fun detail(src: Argb, scale: Int): Argb? = if (esrgan == null) null else upscale(src, scale) {}

    /** Real-ESRGAN x4 over the whole photo, returned at [scale] times the input size. */
    @Synchronized
    private fun upscale(src: Argb, scale: Int, progress: (Float) -> Unit): Argb {
        val model = esrgan!!
        val down = 4 / scale
        val outW = src.width * scale
        val outH = src.height * scale
        val out = Argb(outW, outH)
        val input = FloatArray(T * T * 3)
        val stride = T - 2 * OVERLAP
        val tilesX = (src.width + stride - 1) / stride
        val tilesY = (src.height + stride - 1) / stride
        var done = 0
        for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
            // The tile's useful core starts here; the tile itself reaches OVERLAP further each way.
            val cx = tx * stride
            val cy = ty * stride
            val x0 = cx - OVERLAP
            val y0 = cy - OVERLAP
            for (y in 0 until T) for (x in 0 until T) {
                val p = src[(x0 + x).coerceIn(0, src.width - 1), (y0 + y).coerceIn(0, src.height - 1)]
                val i = (y * T + x) * 3
                input[i] = ((p shr 16) and 0xFF) / 255f
                input[i + 1] = ((p shr 8) and 0xFF) / 255f
                input[i + 2] = (p and 0xFF) / 255f
            }
            val up = model.run(input)[0] // 512 x 512 x 3
            // Copy the core (without the overlap) into the output at the asked scale.
            val coreW = minOf(stride, src.width - cx)
            val coreH = minOf(stride, src.height - cy)
            for (oy in 0 until coreH * scale) for (ox in 0 until coreW * scale) {
                var r = 0f; var g = 0f; var b = 0f
                for (dy in 0 until down) for (dx in 0 until down) {
                    val ux = (OVERLAP * 4) + ox * down + dx
                    val uy = (OVERLAP * 4) + oy * down + dy
                    val j = (uy * U + ux) * 3
                    r += up[j]; g += up[j + 1]; b += up[j + 2]
                }
                val n = (down * down).toFloat()
                out[cx * scale + ox, cy * scale + oy] = (0xFF shl 24) or
                    (((r / n) * 255f + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                    (((g / n) * 255f + 0.5f).toInt().coerceIn(0, 255) shl 8) or
                    ((b / n) * 255f + 0.5f).toInt().coerceIn(0, 255)
            }
            done++
            progress(0.05f + 0.95f * done / (tilesX * tilesY))
        }
        return out
    }

    private fun blend(a: Argb, b: Argb, t: Float): Argb {
        val out = Argb(a.width, a.height)
        for (i in a.px.indices) {
            val p = a.px[i]; val q = b.px[i]
            fun ch(s: Int): Int {
                val x = (p shr s) and 0xFF
                val y = (q shr s) and 0xFF
                return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
            }
            out.px[i] = (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        return out
    }

    override fun close() {
        esrgan?.close()
        faces?.close()
    }

    companion object {
        const val ASSET = "models/esrgan_x4.tflite"
        private const val T = 128
        private const val U = 512
        private const val OVERLAP = 8
        /** Largest result Enhance builds in memory today. */
        const val MAX_OUTPUT_PIXELS = 64_000_000L
    }
}
