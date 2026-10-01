package app.nanogone.imaging.mask

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Mask rules. The tap rules are what stop "magnet drift": a tap can only add or remove
 * the one connected piece under the finger, so the rest of the selection never moves.
 */
object MaskOps {

    /** The 4-connected piece of [m] that contains (x, y). Empty if that pixel is off. */
    fun componentAt(m: Mask, x: Int, y: Int): Mask {
        val out = Mask(m.width, m.height)
        if (x !in 0 until m.width || y !in 0 until m.height || !m[x, y]) return out
        val w = m.width
        val stack = IntArray(m.width * m.height)
        var sp = 0
        stack[sp++] = y * w + x
        out.bits[y * w + x] = true
        while (sp > 0) {
            val i = stack[--sp]
            val px = i % w
            val py = i / w
            if (px > 0) sp = visit(m, out, stack, sp, i - 1)
            if (px < w - 1) sp = visit(m, out, stack, sp, i + 1)
            if (py > 0) sp = visit(m, out, stack, sp, i - w)
            if (py < m.height - 1) sp = visit(m, out, stack, sp, i + w)
        }
        return out
    }

    private fun visit(m: Mask, out: Mask, stack: IntArray, sp: Int, i: Int): Int {
        if (m.bits[i] && !out.bits[i]) {
            out.bits[i] = true
            stack[sp] = i
            return sp + 1
        }
        return sp
    }

    fun union(a: Mask, b: Mask): Mask = combine(a, b) { p, q -> p || q }

    fun intersect(a: Mask, b: Mask): Mask = combine(a, b) { p, q -> p && q }

    fun subtract(a: Mask, b: Mask): Mask = combine(a, b) { p, q -> p && !q }

    private inline fun combine(a: Mask, b: Mask, op: (Boolean, Boolean) -> Boolean): Mask {
        require(a.width == b.width && a.height == b.height) { "mask sizes differ" }
        return Mask(a.width, a.height, BooleanArray(a.bits.size) { op(a.bits[it], b.bits[it]) })
    }

    /** Plus tap: add only the piece of the model's [proposal] that contains the tap. */
    fun plusTap(current: Mask, proposal: Mask, x: Int, y: Int): Mask =
        union(current, componentAt(proposal, x, y))

    /**
     * Minus tap: remove only the piece of (current AND proposal) under the tap.
     * If the tap is not inside that overlap, nothing changes.
     */
    fun minusTap(current: Mask, proposal: Mask, x: Int, y: Int): Mask {
        val piece = componentAt(intersect(current, proposal), x, y)
        return if (piece.isEmpty()) current.copy() else subtract(current, piece)
    }

    /** Fill a polygon (even-odd rule, sampled at pixel centres). */
    fun rasterizePolygon(width: Int, height: Int, xs: FloatArray, ys: FloatArray): Mask {
        require(xs.size == ys.size && xs.size >= 3) { "polygon needs 3 or more points" }
        val out = Mask(width, height)
        val n = xs.size
        val crossings = FloatArray(n)
        for (y in 0 until height) {
            val cy = y + 0.5f
            var c = 0
            var j = n - 1
            for (i in 0 until n) {
                val yi = ys[i]; val yj = ys[j]
                if ((yi > cy) != (yj > cy)) {
                    crossings[c++] = xs[i] + (cy - yi) * (xs[j] - xs[i]) / (yj - yi)
                }
                j = i
            }
            crossings.sort(0, c)
            var k = 0
            while (k + 1 < c) {
                val from = maxOf(0, kotlin.math.ceil(crossings[k] - 0.5f).toInt())
                val to = minOf(width - 1, kotlin.math.floor(crossings[k + 1] - 0.5f).toInt())
                for (x in from..to) out.bits[y * width + x] = true
                k += 2
            }
        }
        return out
    }

    /** Keep only the part of [m] inside the polygon (used by the smart loop). */
    fun clipToPolygon(m: Mask, xs: FloatArray, ys: FloatArray): Mask =
        intersect(m, rasterizePolygon(m.width, m.height, xs, ys))

    /**
     * For each on pixel, the Euclidean distance (in pixels) to the nearest off pixel or to
     * the outside of the mask. Off pixels get 0.
     */
    fun distanceToOff(m: Mask): FloatArray {
        // Squared EDT where "features" are off pixels; pixels outside the mask count as off.
        val w = m.width + 2
        val h = m.height + 2
        val inf = 1e20f
        val grid = FloatArray(w * h) { inf }
        for (y in 0 until h) for (x in 0 until w) {
            val inside = x in 1..m.width && y in 1..m.height && m[x - 1, y - 1]
            if (!inside) grid[y * w + x] = 0f
        }
        edt2d(grid, w, h)
        val out = FloatArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) {
            out[y * m.width + x] = sqrt(grid[(y + 1) * w + (x + 1)])
        }
        return out
    }

    /**
     * For every pixel, the Euclidean distance to the nearest ON pixel of [m] (0 on the mask).
     * Unlike [distanceToOff], the picture's border means nothing here. Infinite if [m] is empty.
     */
    fun distanceFrom(m: Mask): FloatArray {
        val g = FloatArray(m.width * m.height) { if (m.bits[it]) 0f else 1e20f }
        edt2d(g, m.width, m.height)
        for (i in g.indices) g[i] = sqrt(g[i])
        return g
    }

    /** Grow the mask by [radius] pixels (a round brush, exact distance). */
    fun grow(m: Mask, radius: Int): Mask {
        if (radius <= 0) return m.copy()
        // Only the area near the mask can change: work in that window.
        val b = m.bounds() ?: return m.copy()
        val wl = maxOf(0, b.left - radius); val wt = maxOf(0, b.top - radius)
        val wr = minOf(m.width, b.right + radius); val wb = minOf(m.height, b.bottom + radius)
        if (wl > 0 || wt > 0 || wr < m.width || wb < m.height) {
            val sub = Mask(wr - wl, wb - wt)
            for (y in 0 until sub.height) for (x in 0 until sub.width) sub[x, y] = m[wl + x, wt + y]
            val grown = grow(sub, radius)
            val out = m.copy()
            for (y in 0 until sub.height) for (x in 0 until sub.width) if (grown[x, y]) out[wl + x, wt + y] = true
            return out
        }
        val w = m.width
        val h = m.height
        val grid = FloatArray(w * h) { if (m.bits[it]) 0f else 1e20f }
        edt2d(grid, w, h)
        val r2 = radius.toFloat() * radius
        return Mask(w, h, BooleanArray(w * h) { grid[it] <= r2 })
    }

    /** How much to grow a selection before repair: bigger things get a bigger margin. */
    fun growRadiusFor(m: Mask): Int = (0.03 * sqrt(m.count().toDouble())).roundToInt().coerceIn(2, 16)

    /** Felzenszwalb and Huttenlocher squared distance transform, in place. */
    private fun edt2d(g: FloatArray, w: Int, h: Int) {
        val n = maxOf(w, h)
        val f = FloatArray(n)
        val d = FloatArray(n)
        val v = IntArray(n)
        val z = FloatArray(n + 1)
        for (x in 0 until w) {
            for (y in 0 until h) f[y] = g[y * w + x]
            edt1d(f, h, d, v, z)
            for (y in 0 until h) g[y * w + x] = d[y]
        }
        for (y in 0 until h) {
            for (x in 0 until w) f[x] = g[y * w + x]
            edt1d(f, w, d, v, z)
            for (x in 0 until w) g[y * w + x] = d[x]
        }
    }

    private fun edt1d(f: FloatArray, n: Int, d: FloatArray, v: IntArray, z: FloatArray) {
        var k = 0
        v[0] = 0
        z[0] = Float.NEGATIVE_INFINITY
        z[1] = Float.POSITIVE_INFINITY
        for (q in 1 until n) {
            var s = ((f[q] + q.toFloat() * q) - (f[v[k]] + v[k].toFloat() * v[k])) / (2f * q - 2f * v[k])
            while (s <= z[k]) {
                k--
                s = ((f[q] + q.toFloat() * q) - (f[v[k]] + v[k].toFloat() * v[k])) / (2f * q - 2f * v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.POSITIVE_INFINITY
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val dq = (q - v[k]).toFloat()
            d[q] = dq * dq + f[v[k]]
        }
    }
}
