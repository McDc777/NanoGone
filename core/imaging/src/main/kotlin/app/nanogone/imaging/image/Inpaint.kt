package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask

/**
 * Simple non-AI hole filling: a coarse-to-fine smooth fill (membrane interpolation).
 * Used as the safety net when no AI brain is available. Pixels outside the mask are untouched.
 */
object Inpaint {

    fun smoothFill(img: Argb, mask: Mask): Argb {
        require(img.width == mask.width && img.height == mask.height) { "mask must match image" }
        val w = img.width
        val h = img.height
        val ch = Array(3) { c -> FloatArray(w * h) { ((img.px[it] shr (16 - 8 * c)) and 0xFF).toFloat() } }
        val known = BooleanArray(w * h) { !mask.bits[it] }
        if (known.none { it }) return img.copy()
        val filled = solve(ch, known, w, h)
        val out = img.copy()
        for (i in 0 until w * h) {
            if (mask.bits[i]) {
                val r = filled[0][i].toInt().coerceIn(0, 255)
                val g = filled[1][i].toInt().coerceIn(0, 255)
                val b = filled[2][i].toInt().coerceIn(0, 255)
                out.px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    private fun solve(ch: Array<FloatArray>, known: BooleanArray, w: Int, h: Int): Array<FloatArray> {
        val vals = Array(3) { ch[it].copyOf() }
        if (maxOf(w, h) > 16 && w >= 2 && h >= 2) {
            // Coarser level: average the known children of each 2x2 cell.
            val cw = (w + 1) / 2
            val chh = (h + 1) / 2
            val cVals = Array(3) { FloatArray(cw * chh) }
            val cKnown = BooleanArray(cw * chh)
            for (cy in 0 until chh) for (cx in 0 until cw) {
                var n = 0
                val sum = FloatArray(3)
                for (dy in 0..1) for (dx in 0..1) {
                    val x = cx * 2 + dx
                    val y = cy * 2 + dy
                    if (x < w && y < h && known[y * w + x]) {
                        n++
                        for (c in 0..2) sum[c] += ch[c][y * w + x]
                    }
                }
                if (n > 0) {
                    cKnown[cy * cw + cx] = true
                    for (c in 0..2) cVals[c][cy * cw + cx] = sum[c] / n
                }
            }
            val coarse = solve(cVals, cKnown, cw, chh)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (!known[i]) for (c in 0..2) vals[c][i] = coarse[c][(y / 2) * cw + (x / 2)]
            }
            relax(vals, known, w, h, 40)
        } else {
            var n = 0
            val mean = FloatArray(3)
            for (i in known.indices) if (known[i]) { n++; for (c in 0..2) mean[c] += ch[c][i] }
            for (c in 0..2) mean[c] = if (n > 0) mean[c] / n else 128f
            for (i in known.indices) if (!known[i]) for (c in 0..2) vals[c][i] = mean[c]
            relax(vals, known, w, h, 300)
        }
        return vals
    }

    /** Gauss-Seidel smoothing of unknown pixels toward the average of their 4 neighbours. */
    private fun relax(vals: Array<FloatArray>, known: BooleanArray, w: Int, h: Int, iterations: Int) {
        repeat(iterations) {
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (known[i]) continue
                for (c in 0..2) {
                    val v = vals[c]
                    var s = 0f
                    var n = 0
                    if (x > 0) { s += v[i - 1]; n++ }
                    if (x < w - 1) { s += v[i + 1]; n++ }
                    if (y > 0) { s += v[i - w]; n++ }
                    if (y < h - 1) { s += v[i + w]; n++ }
                    v[i] = s / n
                }
            }
        }
    }
}
