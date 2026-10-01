package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Shadow catcher: finds the shadow attached to a selected object. A shadow touches the object,
 * is clearly darker than the lit ground around it, and keeps the ground's colour balance.
 * Starting from the object's edge, the search grows only through pixels that look like that,
 * and never further than about one and a half object sizes.
 */
object ShadowFinder {

    /** @return the shadow (not including the object itself), or an empty mask. */
    fun find(img: Argb, obj: Mask): Mask {
        // Work only in a window around the object: the shadow cannot be further than the reach.
        val b = obj.bounds() ?: return Mask(img.width, img.height)
        val pad = (1.5f * sqrt(obj.count().toFloat())).toInt() + 34
        val win = app.nanogone.imaging.geom.IntRect(b.left - pad, b.top - pad, b.right + pad, b.bottom + pad)
            .intersect(app.nanogone.imaging.geom.IntRect(0, 0, img.width, img.height))
        if (win.width == img.width && win.height == img.height) return findIn(img, obj)
        val subMask = Mask(win.width, win.height)
        for (y in 0 until win.height) for (x in 0 until win.width) subMask[x, y] = obj[win.left + x, win.top + y]
        val found = findIn(img.crop(win), subMask)
        val out = Mask(img.width, img.height)
        for (y in 0 until win.height) for (x in 0 until win.width) if (found[x, y]) out[win.left + x, win.top + y] = true
        return out
    }

    private fun findIn(img: Argb, obj: Mask): Mask {
        val w = img.width
        val h = img.height
        val out = Mask(w, h)
        val area = obj.count()
        if (area == 0) return out
        val reach = 1.5f * sqrt(area.toFloat())
        val dist = MaskOps.distanceFrom(obj) // distance from the object

        // The lit ground: pixels 4 to 30 px around the object; take a bright-ish level (70th
        // percentile) so the shadow itself does not drag the reference down.
        val ringL = ArrayList<Float>()
        var cr = 0.0; var cg = 0.0; var n = 0
        for (i in 0 until w * h) {
            if (obj.bits[i] || dist[i] !in 4f..30f) continue
            val p = img.px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            ringL.add(lum(r, g, b))
            val s = (r + g + b).coerceAtLeast(1)
            cr += r.toDouble() / s; cg += g.toDouble() / s; n++
        }
        if (n < 20) return out
        ringL.sort()
        val lit = ringL[(ringL.size * 0.7).toInt().coerceAtMost(ringL.size - 1)]
        val chromaR = (cr / n).toFloat()
        val chromaG = (cg / n).toFloat()

        fun shadowLike(i: Int): Boolean {
            if (obj.bits[i] || dist[i] > reach) return false
            val p = img.px[i]
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            val l = lum(r, g, b)
            if (l > 0.82f * lit || l < 0.12f * lit) return false
            val s = (r + g + b).coerceAtLeast(1).toFloat()
            return abs(r / s - chromaR) < 0.07f && abs(g / s - chromaG) < 0.07f
        }

        // Grow from shadow-like pixels that touch the object.
        val stack = IntArray(w * h)
        var sp = 0
        for (i in 0 until w * h) {
            if (dist[i] in 0.5f..1.5f && shadowLike(i)) { out.bits[i] = true; stack[sp++] = i }
        }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w
            val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val xx = x + dx
                val yy = y + dy
                if (xx !in 0 until w || yy !in 0 until h) continue
                val j = yy * w + xx
                if (!out.bits[j] && shadowLike(j)) { out.bits[j] = true; stack[sp++] = j }
            }
        }
        // Ignore specks: a real shadow is a few percent of the object at least.
        return if (out.count() < maxOf(12, area / 50)) Mask(w, h) else out
    }

    private fun lum(r: Int, g: Int, b: Int): Float = 0.299f * r + 0.587f * g + 0.114f * b
}
