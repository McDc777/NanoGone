package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShadowFinderTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `finds the shadow that touches the object, not dark things far away`() {
        val w = 200
        val h = 120
        val img = Argb(w, h, IntArray(w * h) { rgb(200, 180, 140) }) // sand
        val obj = Mask(w, h)
        for (y in 30 until 80) for (x in 60 until 90) { img[x, y] = rgb(200, 40, 30); obj[x, y] = true } // red bin
        for (y in 65 until 85) for (x in 90 until 150) img[x, y] = rgb(110, 99, 77) // its shadow, same sand colour, darker
        for (y in 10 until 25) for (x in 170 until 190) img[x, y] = rgb(100, 90, 70) // a dark patch far away
        val s = ShadowFinder.find(img, obj)
        assertTrue(s[120, 75], "middle of the shadow should be caught")
        assertTrue(s[95, 70], "shadow next to the bin should be caught")
        assertFalse(s[180, 15], "far dark patch must not be caught")
        assertFalse(s[30, 50], "lit sand must not be caught")
        assertFalse(s[70, 50], "the bin itself is not part of the shadow")
    }

    @Test
    fun `no shadow gives an empty result`() {
        val img = Argb(100, 100, IntArray(10_000) { rgb(150, 150, 150) })
        val obj = Mask(100, 100).also { m -> for (y in 40 until 60) for (x in 40 until 60) m[x, y] = true }
        for (y in 40 until 60) for (x in 40 until 60) img[x, y] = rgb(20, 200, 20)
        assertTrue(ShadowFinder.find(img, obj).isEmpty())
    }
}
