package app.nanogone.imaging.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoToneTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test
    fun `a dull, flat photo gets a wider range`() {
        val img = Argb(100, 100, IntArray(10_000) { grey(90 + (it % 100) * 60 / 99) })
        val out = AutoTone.analyse(img).apply(img, 1f)
        val min = out.px.minOf { it and 0xFF }
        val max = out.px.maxOf { it and 0xFF }
        assertTrue(min < 70, "darkest should go darker, was $min")
        assertTrue(max > 180, "brightest should go brighter, was $max")
    }

    @Test
    fun `strength zero changes nothing`() {
        val img = Argb(50, 40, IntArray(2000) { (0xFF shl 24) or ((it * 37) and 0xFFFFFF) })
        val out = AutoTone.analyse(img).apply(img, 0f)
        for (i in img.px.indices) assertEquals(img.px[i], out.px[i])
    }

    @Test
    fun `a blue cast is pulled toward neutral`() {
        val img = Argb(60, 60, IntArray(3600) { i -> val v = 60 + (i % 60) * 2; (0xFF shl 24) or (v shl 16) or (v shl 8) or (v + 50) })
        val out = AutoTone.analyse(img).apply(img, 1f)
        val before = img.px[1830].let { (it and 0xFF).toFloat() / ((it shr 16) and 0xFF) }
        val after = out.px[1830].let { (it and 0xFF).toFloat() / ((it shr 16) and 0xFF).coerceAtLeast(1) }
        assertTrue(after < before, "blue to red ratio should shrink: before $before, after $after")
    }
}
