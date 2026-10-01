package app.nanogone.imaging.jpeg

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pixels to quantized coefficients for one MCU (JFIF YCbCr, box chroma downsampling, float DCT). */
internal object ForwardDct {

    private val cosTable = Array(8) { x -> DoubleArray(8) { u -> cos((2 * x + 1) * u * PI / 16) } }
    private val cScale = DoubleArray(8) { if (it == 0) 1 / sqrt(2.0) else 1.0 }

    /**
     * Recompute every block of MCU ([mx], [my]) from [pixel] (ARGB of any image coordinate inside
     * the image) and store them in [coeffs]. Coordinates outside the image are clamped (edge copy).
     */
    fun encodeMcu(p: ParsedJpeg, coeffs: Coefficients, mx: Int, my: Int, pixel: (Int, Int) -> Int) {
        val f = p.frame
        val gray = f.components.size == 1
        val sample = DoubleArray(64)
        val freq = DoubleArray(64)
        CoefficientDecoder.forEachBlockInMcu(p, mx, my) { _, c, bx, by ->
            val fc = f.components[c]
            val sx = f.hMax / fc.h
            val sy = f.vMax / fc.v
            // Block (bx, by) covers component samples [bx*8, bx*8+8) x [by*8, by*8+8).
            for (j in 0 until 8) for (i in 0 until 8) {
                val compX = bx * 8 + i
                val compY = by * 8 + j
                var sum = 0.0
                for (dy in 0 until sy) for (dx in 0 until sx) {
                    val x = (compX * sx + dx).coerceIn(0, f.width - 1)
                    val y = (compY * sy + dy).coerceIn(0, f.height - 1)
                    sum += channel(pixel(x, y), if (gray) 0 else c)
                }
                sample[j * 8 + i] = sum / (sx * sy) - 128.0
            }
            fdct(sample, freq)
            val q = p.qTables[fc.tq] ?: throw UnsupportedJpegException("missing quant table ${fc.tq}")
            val dst = coeffs.data[c]
            val off = coeffs.offset(c, bx, by)
            for (k in 0 until 64) {
                val v = (freq[ZIGZAG_TO_NATURAL[k]] / q[k]).roundToInt()
                dst[off + k] = (if (k == 0) v.coerceIn(-2047, 2047) else v.coerceIn(-1023, 1023)).toShort()
            }
        }
    }

    /** 0 = Y, 1 = Cb, 2 = Cr (JFIF). */
    private fun channel(argb: Int, which: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return when (which) {
            0 -> 0.299 * r + 0.587 * g + 0.114 * b
            1 -> -0.168735892 * r - 0.331264108 * g + 0.5 * b + 128.0
            else -> 0.5 * r - 0.418687589 * g - 0.081312411 * b + 128.0
        }
    }

    private fun fdct(s: DoubleArray, out: DoubleArray) {
        val tmp = DoubleArray(64)
        for (y in 0 until 8) for (u in 0 until 8) {
            var acc = 0.0
            for (x in 0 until 8) acc += s[y * 8 + x] * cosTable[x][u]
            tmp[y * 8 + u] = acc
        }
        for (v in 0 until 8) for (u in 0 until 8) {
            var acc = 0.0
            for (y in 0 until 8) acc += tmp[y * 8 + u] * cosTable[y][v]
            out[v * 8 + u] = 0.25 * cScale[u] * cScale[v] * acc
        }
    }
}
