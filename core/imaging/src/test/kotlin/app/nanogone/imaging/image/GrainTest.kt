package app.nanogone.imaging.image

import app.nanogone.imaging.mask.Mask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt
import kotlin.random.Random

class GrainTest {

    private fun spread(img: Argb, pick: (Int) -> Boolean): Double {
        val v = img.px.indices.filter(pick).map { (img.px[it] and 0xFF).toDouble() }
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }

    @Test
    fun `a smooth fill gets the grain of its surroundings, outside stays the same`() {
        val rnd = Random(1)
        val w = 80
        val h = 80
        val img = Argb(w, h, IntArray(w * h) { val v = 128 + rnd.nextInt(-20, 21); (0xFF shl 24) or (v shl 16) or (v shl 8) or v })
        val mask = Mask(w, h).also { m -> for (y in 25 until 55) for (x in 25 until 55) m[x, y] = true }
        for (y in 25 until 55) for (x in 25 until 55) img[x, y] = 0xFF808080.toInt() // perfectly smooth fill
        val out = Grain.match(img, mask)
        val inside = spread(out) { mask.bits[it] && (it % w) in 28..51 && (it / w) in 28..51 }
        val outside = spread(img) { !mask.bits[it] }
        assertTrue(inside > outside * 0.5, "fill grain $inside should be close to photo grain $outside")
        for (i in img.px.indices) if (!mask.bits[i]) assertEquals(img.px[i], out.px[i])
    }
}
