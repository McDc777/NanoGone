package app.nanogone.imaging.image

import app.nanogone.imaging.geom.CropPlanner
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.mask.Mask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PasteAndCropTest {

    @Test
    fun `context box grows around the mask and clamps at the edges`() {
        val box = CropPlanner.contextBox(IntRect(10, 10, 30, 20), 100, 100)
        assertEquals(IntRect(0, 0, 62, 52), box)
        val middle = CropPlanner.contextBox(IntRect(400, 400, 500, 440), 2000, 2000)
        assertEquals(IntRect(325, 325, 575, 515), middle)
    }

    @Test
    fun `align out snaps to the grid and clamps`() {
        assertEquals(IntRect(0, 16, 48, 40), CropPlanner.alignOut(IntRect(5, 17, 33, 47), 16, 16, 100, 40))
    }

    @Test
    fun `paste never changes a pixel outside the mask`() {
        val dst = Argb(40, 30, IntArray(1200) { 0xFF102030.toInt() + it })
        val before = dst.copy()
        val src = Argb(10, 10, IntArray(100) { 0xFFFFFFFF.toInt() })
        val mask = Mask(10, 10).also { m -> for (y in 2 until 8) for (x in 2 until 8) m[x, y] = true }
        Paste.feathered(dst, src, 5, 6, mask, feather = 2f)
        for (y in 0 until 30) for (x in 0 until 40) {
            val inMask = x - 5 in 0 until 10 && y - 6 in 0 until 10 && mask[x - 5, y - 6]
            if (!inMask) assertEquals(before[x, y], dst[x, y], "pixel $x,$y changed")
        }
        assertEquals(0xFFFFFFFF.toInt(), dst[5 + 4, 6 + 4])
        assertNotEquals(0xFFFFFFFF.toInt(), dst[5 + 2, 6 + 2])
    }

    @Test
    fun `crop copies the right pixels`() {
        val img = Argb(4, 3, IntArray(12) { it })
        val c = img.crop(IntRect(1, 1, 3, 3))
        assertEquals(listOf(5, 6, 9, 10), c.px.toList())
    }
}
