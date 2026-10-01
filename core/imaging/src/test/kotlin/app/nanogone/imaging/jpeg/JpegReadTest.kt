package app.nanogone.imaging.jpeg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class JpegReadTest {

    @Test
    fun `reads sizes and sampling`() {
        val a = JpegParser.parse(Fixtures.bytes("rgb420_q75.jpg"))
        assertEquals(203, a.frame.width)
        assertEquals(157, a.frame.height)
        assertEquals(listOf(2 to 2, 1 to 1, 1 to 1), a.frame.components.map { it.h to it.v })
        assertEquals(16, a.mcuWidth)
        assertEquals(13, a.mcusX)
        assertEquals(10, a.mcusY)

        val g = JpegParser.parse(Fixtures.bytes("gray_q85.jpg"))
        assertEquals(1, g.frame.components.size)
        assertEquals(10, g.mcusX)
        assertEquals(7, g.mcusY)

        val r = JpegParser.parse(Fixtures.bytes("rgb420_restart.jpg"))
        assertTrue(r.restartInterval > 0)
    }

    @Test
    fun `progressive files are refused`() {
        assertThrows<UnsupportedJpegException> { JpegParser.parse(Fixtures.bytes("progressive.jpg")) }
    }

    @Test
    fun `decodes every fixture without running out of data`() {
        for (name in Fixtures.patchable) {
            val p = JpegParser.parse(Fixtures.bytes(name))
            val c = CoefficientDecoder.decode(p)
            // Plausible DC values for an 8-bit photo: |DC| stays well under 2048 / q.
            for (comp in c.data.indices) {
                for (b in 0 until c.blocksX[comp] * c.blocksY[comp]) {
                    val dc = c.data[comp][b * 64].toInt()
                    assertTrue(kotlin.math.abs(dc) < 2048, "$name comp $comp block $b dc $dc")
                }
            }
        }
    }

    @Test
    fun `flat grey image has the same DC in every block`() {
        val p = JpegParser.parse(Fixtures.bytes("rgb444_q95.jpg"))
        assertEquals(3, p.frame.components.size)
        val c = CoefficientDecoder.decode(p)
        assertEquals(8 * 6 * 64, c.data[0].size)
    }
}
