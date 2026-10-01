package app.nanogone.imaging.mask

import app.nanogone.imaging.geom.IntRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MaskOpsTest {

    private fun box(w: Int, h: Int, vararg rects: IntRect): Mask {
        val m = Mask(w, h)
        for (r in rects) for (y in r.top until r.bottom) for (x in r.left until r.right) m[x, y] = true
        return m
    }

    private val left = IntRect(1, 1, 4, 4)
    private val right = IntRect(10, 1, 13, 4)

    @Test
    fun `plus tap adds only the touched piece of the proposal`() {
        val proposal = box(16, 8, left, right)
        val result = MaskOps.plusTap(Mask(16, 8), proposal, 2, 2)
        assertEquals(box(16, 8, left), result)
    }

    @Test
    fun `plus tap keeps what was already selected`() {
        val current = box(16, 8, right)
        val proposal = box(16, 8, left)
        assertEquals(box(16, 8, left, right), MaskOps.plusTap(current, proposal, 2, 2))
    }

    @Test
    fun `tap outside the proposal changes nothing`() {
        val current = box(16, 8, right)
        val proposal = box(16, 8, left)
        assertEquals(current, MaskOps.plusTap(current, proposal, 7, 6))
        assertEquals(current, MaskOps.minusTap(current, proposal, 7, 6))
    }

    @Test
    fun `minus tap removes only the touched piece`() {
        val current = box(16, 8, left, right)
        val proposal = box(16, 8, left, right)
        assertEquals(box(16, 8, right), MaskOps.minusTap(current, proposal, 2, 2))
    }

    @Test
    fun `square polygon rasterizes to the expected pixels`() {
        val m = MaskOps.rasterizePolygon(10, 10, floatArrayOf(2f, 6f, 6f, 2f), floatArrayOf(2f, 2f, 6f, 6f))
        assertEquals(box(10, 10, IntRect(2, 2, 6, 6)), m)
    }

    @Test
    fun `clip keeps only what is inside the loop`() {
        val m = box(10, 10, IntRect(0, 0, 10, 10))
        val clipped = MaskOps.clipToPolygon(m, floatArrayOf(0f, 5f, 5f, 0f), floatArrayOf(0f, 0f, 10f, 10f))
        assertEquals(50, clipped.count())
        assertTrue(clipped[4, 9])
        assertFalse(clipped[5, 0])
    }

    @Test
    fun `grow turns a dot into a round disk`() {
        val m = Mask(9, 9).also { it[4, 4] = true }
        val g = MaskOps.grow(m, 2)
        assertEquals(13, g.count())
        assertTrue(g[4, 2])
        assertFalse(g[2, 2])
    }

    @Test
    fun `distance to off is zero outside and grows inward`() {
        val m = box(9, 9, IntRect(0, 0, 9, 9))
        val d = MaskOps.distanceToOff(m)
        assertEquals(1f, d[0], 1e-4f)
        assertEquals(5f, d[4 * 9 + 4], 1e-4f)
        val off = Mask(3, 3)
        assertTrue(MaskOps.distanceToOff(off).all { it == 0f })
    }

    @Test
    fun `grow radius scales with size and is clamped`() {
        assertEquals(2, MaskOps.growRadiusFor(box(10, 10, IntRect(0, 0, 3, 3))))
        val big = Mask(2000, 2000, BooleanArray(4_000_000) { true })
        assertEquals(16, MaskOps.growRadiusFor(big))
    }

    @Test
    fun `bounds covers every on pixel`() {
        assertEquals(IntRect(1, 1, 13, 4), box(16, 8, left, right).bounds())
        assertEquals(null, Mask(4, 4).bounds())
    }
}
