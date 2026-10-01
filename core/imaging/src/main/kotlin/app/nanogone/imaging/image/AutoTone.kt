package app.nanogone.imaging.image

import kotlin.math.ln
import kotlin.math.pow

/**
 * Enhance "Light and colour": what a careful editor does first. Sets black and white points from
 * the photo itself, lifts or calms the mid-tones, nudges a colour cast toward neutral and adds a
 * little vibrance to dull colours. Tuned once on a small copy, then applied to any size.
 */
class AutoTone private constructor(
    private val black: Float,
    private val white: Float,
    private val gamma: Float,
    private val gain: FloatArray,
) {
    /** Apply at [strength] 0 (no change) to 1 (full). */
    fun apply(src: Argb, strength: Float): Argb {
        val s = strength.coerceIn(0f, 1f)
        val out = Argb(src.width, src.height)
        val curve = FloatArray(256) { i ->
            val v = ((i / 255f - black) / (white - black)).coerceIn(0f, 1f)
            v.pow(gamma)
        }
        for (i in src.px.indices) {
            val p = src.px[i]
            val r0 = ((p shr 16) and 0xFF) / 255f
            val g0 = ((p shr 8) and 0xFF) / 255f
            val b0 = (p and 0xFF) / 255f
            // White balance gain, then the tone curve on brightness only (colours keep their balance).
            var r = r0 * gain[0]
            var g = g0 * gain[1]
            var b = b0 * gain[2]
            val l0 = 0.299f * r + 0.587f * g + 0.114f * b
            val l1 = curve[(l0 * 255f).toInt().coerceIn(0, 255)]
            val k = if (l0 > 1e-4f) l1 / l0 else 0f
            r *= k; g *= k; b *= k
            // Vibrance: dull colours gain more saturation than strong ones.
            val mx = maxOf(r, g, b)
            val mn = minOf(r, g, b)
            val sat = if (mx <= 0f) 0f else (mx - mn) / mx
            val boost = 0.22f * (1f - sat)
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            r = l + (r - l) * (1f + boost)
            g = l + (g - l) * (1f + boost)
            b = l + (b - l) * (1f + boost)
            r = r.coerceIn(0f, 1f); g = g.coerceIn(0f, 1f); b = b.coerceIn(0f, 1f)
            out.px[i] = (0xFF shl 24) or
                (mix(r0, r, s) shl 16) or (mix(g0, g, s) shl 8) or mix(b0, b, s)
        }
        return out
    }

    private fun mix(a: Float, b: Float, t: Float): Int = ((a + (b - a) * t) * 255f + 0.5f).toInt().coerceIn(0, 255)

    companion object {
        /** Study a (preferably small) copy of the photo. */
        fun analyse(img: Argb): AutoTone {
            val hist = IntArray(256)
            var sr = 0.0; var sg = 0.0; var sb = 0.0
            val step = maxOf(1, (img.px.size / 400_000))
            var n = 0
            var i = 0
            while (i < img.px.size) {
                val p = img.px[i]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                hist[(0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)]++
                sr += r; sg += g; sb += b
                n++
                i += step
            }
            fun pct(q: Double): Int {
                val target = (n * q).toLong()
                var acc = 0L
                for (v in 0..255) { acc += hist[v]; if (acc > target) return v }
                return 255
            }
            val lo = pct(0.005) / 255f
            val hi = pct(0.995) / 255f
            // Stretch to the photo's own black and white points, but never more than about 2.2x,
            // so a foggy or night photo keeps its mood.
            var black = lo
            var white = hi
            if (white - black < 0.45f) {
                val c = (white + black) / 2f
                black = (c - 0.225f).coerceAtLeast(0f)
                white = (black + 0.45f).coerceAtMost(1f)
                black = white - 0.45f
            }
            val mid = ((pct(0.5) / 255f - black) / (white - black)).coerceIn(0.05f, 0.95f)
            val gamma = (ln(0.46) / ln(mid.toDouble())).toFloat().coerceIn(0.75f, 1.35f)
            // Partial grey-world white balance (half way) so mood lighting is kept.
            val avg = ((sr + sg + sb) / (3 * n)).toFloat()
            val gain = doubleArrayOf(sr, sg, sb).let { arr ->
                FloatArray(3) { k -> 1f + 0.5f * (avg / ((arr[k] / n).toFloat().coerceAtLeast(1f)) - 1f) }
            }
            return AutoTone(black, white, gamma, gain)
        }

    }
}
