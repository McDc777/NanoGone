package app.nanogone.editor

import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps
import kotlin.math.ceil
import kotlin.math.floor

/** Something the person drew to select, in stored-image pixel coordinates. */
sealed interface Shape {
    /** Bounding box in image pixels (may reach outside the image). */
    fun bounds(): IntRect
}

/** A round-tipped brush stroke. [erase] strokes remove from the selection. */
class BrushStroke(val xs: FloatArray, val ys: FloatArray, val radius: Float, val erase: Boolean) : Shape {
    override fun bounds(): IntRect = IntRect(
        floor(xs.min() - radius).toInt(), floor(ys.min() - radius).toInt(),
        ceil(xs.max() + radius).toInt() + 1, ceil(ys.max() + radius).toInt() + 1,
    )
}

/** A loop drawn around something: everything inside is selected. */
class Loop(val xs: FloatArray, val ys: FloatArray) : Shape {
    override fun bounds(): IntRect = IntRect(
        floor(xs.min()).toInt(), floor(ys.min()).toInt(), ceil(xs.max()).toInt() + 1, ceil(ys.max()).toInt() + 1,
    )
}

/** A one-tap spot (dust, speck). */
class Spot(val cx: Float, val cy: Float, val radius: Float) : Shape {
    override fun bounds(): IntRect = IntRect(
        floor(cx - radius).toInt(), floor(cy - radius).toInt(), ceil(cx + radius).toInt() + 1, ceil(cy + radius).toInt() + 1,
    )
}

/**
 * An outline found by magic tap: [mask] stretched over [rect] (image pixels). The mask may be
 * smaller than the rect (outlines of huge photos are kept at a sensible size).
 */
class MaskShape(val rect: IntRect, val mask: Mask, val erase: Boolean = false) : Shape {
    override fun bounds(): IntRect = rect

    fun at(x: Int, y: Int): Boolean {
        val mx = ((x - rect.left + 0.5f) * mask.width / rect.width).toInt().coerceIn(0, mask.width - 1)
        val my = ((y - rect.top + 0.5f) * mask.height / rect.height).toInt().coerceIn(0, mask.height - 1)
        return mask[mx, my]
    }
}

/** One finished removal: the repaired pixels of [rect] and which of them changed. */
class Patch(val rect: IntRect, val pixels: IntArray, val changed: Mask)

/** Everything about an edit at one moment. Immutable, so undo is just stepping back. */
data class DocState(val selection: List<Shape> = emptyList(), val patches: List<Patch> = emptyList())

/** The edit history of one photo of size [width] x [height]. */
class EditDocument(val width: Int, val height: Int) {
    private val history = ArrayList<DocState>().apply { add(DocState()) }
    private var index = 0

    val state: DocState get() = history[index]
    val canUndo: Boolean get() = index > 0
    val canRedo: Boolean get() = index < history.size - 1

    private fun push(s: DocState) {
        while (history.size > index + 1) history.removeAt(history.size - 1)
        history.add(s)
        index++
    }

    fun addShape(shape: Shape) = push(state.copy(selection = state.selection + shape))

    fun clearSelection() {
        if (state.selection.isNotEmpty()) push(state.copy(selection = emptyList()))
    }

    /** A removal consumes the current selection. */
    fun addPatch(patch: Patch) = push(DocState(selection = emptyList(), patches = state.patches + patch))

    fun undo() { if (canUndo) index-- }

    fun redo() { if (canRedo) index++ }

    val image: IntRect get() = IntRect(0, 0, width, height)

    /** Bounds of the current selection, clipped to the image; null if nothing is selected. */
    fun selectionBounds(): IntRect? {
        val adds = state.selection.filter { !(it is BrushStroke && it.erase) && !(it is MaskShape && it.erase) }
        if (adds.isEmpty()) return null
        var r = adds.first().bounds()
        for (s in adds.drop(1)) {
            val b = s.bounds()
            r = IntRect(minOf(r.left, b.left), minOf(r.top, b.top), maxOf(r.right, b.right), maxOf(r.bottom, b.bottom))
        }
        val clipped = r.intersect(image)
        return if (clipped.isEmpty) null else clipped
    }

    /** The selection drawn into a mask covering [rect] (image coordinates). */
    fun selectionMask(rect: IntRect): Mask = rasterize(state.selection, rect)

    companion object {
        fun rasterize(shapes: List<Shape>, rect: IntRect): Mask {
            val m = Mask(rect.width, rect.height)
            for (s in shapes) {
                when (s) {
                    is BrushStroke -> stroke(m, rect, s)
                    is Spot -> disc(m, rect, s.cx, s.cy, s.radius, true)
                    is MaskShape -> {
                        val o = s.rect.intersect(rect)
                        if (!o.isEmpty) for (y in o.top until o.bottom) for (x in o.left until o.right) {
                            if (s.at(x, y)) m[x - rect.left, y - rect.top] = !s.erase
                        }
                    }
                    is Loop -> {
                        val xs = FloatArray(s.xs.size) { s.xs[it] - rect.left }
                        val ys = FloatArray(s.ys.size) { s.ys[it] - rect.top }
                        if (xs.size >= 3) {
                            val fill = MaskOps.rasterizePolygon(m.width, m.height, xs, ys)
                            for (i in m.bits.indices) if (fill.bits[i]) m.bits[i] = true
                        }
                    }
                }
            }
            return m
        }

        private fun stroke(m: Mask, rect: IntRect, s: BrushStroke) {
            if (s.xs.size == 1) {
                disc(m, rect, s.xs[0], s.ys[0], s.radius, !s.erase)
                return
            }
            for (i in 0 until s.xs.size - 1) capsule(m, rect, s.xs[i], s.ys[i], s.xs[i + 1], s.ys[i + 1], s.radius, !s.erase)
        }

        private fun disc(m: Mask, rect: IntRect, cx: Float, cy: Float, r: Float, value: Boolean) =
            capsule(m, rect, cx, cy, cx, cy, r, value)

        /** All pixels whose centre lies within [r] of the segment (ax,ay)-(bx,by). */
        private fun capsule(m: Mask, rect: IntRect, ax: Float, ay: Float, bx: Float, by: Float, r: Float, value: Boolean) {
            val x0 = maxOf(rect.left, floor(minOf(ax, bx) - r).toInt())
            val x1 = minOf(rect.right - 1, ceil(maxOf(ax, bx) + r).toInt())
            val y0 = maxOf(rect.top, floor(minOf(ay, by) - r).toInt())
            val y1 = minOf(rect.bottom - 1, ceil(maxOf(ay, by) + r).toInt())
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val r2 = r * r
            for (y in y0..y1) for (x in x0..x1) {
                val px = x + 0.5f
                val py = y + 0.5f
                val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
                val qx = ax + t * dx - px
                val qy = ay + t * dy - py
                if (qx * qx + qy * qy <= r2) m[x - rect.left, y - rect.top] = value
            }
        }
    }
}
