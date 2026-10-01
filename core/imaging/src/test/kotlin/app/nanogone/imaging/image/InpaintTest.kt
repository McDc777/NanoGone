package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class InpaintTest {

    private fun hole(w: Int, h: Int, l: Int, t: Int, r: Int, b: Int) =
        Mask(w, h).also { m -> for (y in t until b) for (x in l until r) m[x, y] = true }

    @Test
    fun `flat colour stays flat and outside pixels never change`() {
        val img = Argb(60, 40, IntArray(2400) { 0xFF336699.toInt() })
        val m = hole(60, 40, 20, 10, 40, 30)
        for (y in 10 until 30) for (x in 20 until 40) img[x, y] = 0xFFFF0000.toInt()
        val out = Inpaint.smoothFill(img, m)
        for (i in 0 until 2400) {
            if (m.bits[i]) assertEquals(0xFF336699.toInt(), out.px[i]) else assertEquals(img.px[i], out.px[i])
        }
    }

    @Test
    fun `a left to right ramp is continued through the hole`() {
        val w = 100
        val img = Argb(w, 50, IntArray(w * 50) { val v = (it % w) * 255 / (w - 1); (0xFF shl 24) or (v shl 16) or (v shl 8) or v })
        val m = hole(w, 50, 30, 15, 70, 35)
        val out = Inpaint.smoothFill(img, m)
        val mid = out[50, 25] and 0xFF
        assertTrue(abs(mid - 128) <= 6, "middle of hole should be about 128, was $mid")
    }
}
