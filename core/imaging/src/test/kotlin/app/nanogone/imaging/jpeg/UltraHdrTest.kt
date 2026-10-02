package app.nanogone.imaging.jpeg

import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.mask.Mask
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/** uhdr.jpg was made by Google's libultrahdr (tools/make_uhdr.py) from rgb420_q75.jpg. */
class UltraHdrTest {

    private val out = File("build/uhdr").apply { mkdirs() }

    private fun pixels(jpeg: ByteArray): Argb {
        val img = ImageIO.read(ByteArrayInputStream(jpeg))
        val a = Argb(img.width, img.height)
        val gray = img.raster.numBands == 1  // getRGB would colour-convert grey; read the raw value
        for (y in 0 until img.height) for (x in 0 until img.width) {
            a[x, y] = if (gray) img.raster.getSample(x, y, 0).let { (0xFF shl 24) or (it shl 16) or (it shl 8) or it } else img.getRGB(x, y)
        }
        return a
    }

    @Test
    fun `finds the gain map of an Ultra HDR photo and nothing in a plain one`() {
        val gm = requireNotNull(UltraHdr.find(Fixtures.bytes("uhdr.jpg")))
        assertTrue(UltraHdr.isGainMap(gm.jpeg))
        val info = JpegProbe.info(gm.jpeg)
        assertEquals(50, info.width)
        assertEquals(39, info.height)
        assertNull(UltraHdr.find(Fixtures.bytes("rgb420_q75.jpg")))
    }

    @Test
    fun `a cleaned copy plus the gain map is again an Ultra HDR photo`() {
        val src = Fixtures.bytes("uhdr.jpg")
        val gm = UltraHdr.find(src)!!
        val plain = JpegWriter.copy(src, CopyCleaner.forCopy(null))
        assertNull(UltraHdr.find(plain), "the plain copy has no HDR index")
        val joined = UltraHdr.assemble(plain, gm.jpeg, gm.isoVersion)
        File(out, "assembled.jpg").writeBytes(joined)
        val again = requireNotNull(UltraHdr.find(joined)) { "no gain map after assemble" }
        assertArrayEquals(gm.jpeg, again.jpeg)
        // The picture part decodes exactly like the original's.
        assertArrayEquals(pixels(src).px, pixels(joined).px)
    }

    @Test
    fun `repair changes only the squares around the hole and fills it from around`() {
        val gm = UltraHdr.find(Fixtures.bytes("uhdr.jpg"))!!
        val before = pixels(gm.jpeg)
        val hole = Mask(before.width, before.height)
        for (y in 15 until 22) for (x in 20 until 28) hole[x, y] = true
        // Make the hole content obviously wrong first, so we can see it was refilled.
        val dirty = before.copy()
        for (y in 15 until 22) for (x in 20 until 28) dirty[x, y] = 0xFFFFFFFF.toInt()
        val fixed = UltraHdr.repair(gm.jpeg, dirty, hole)
        assertTrue(UltraHdr.isGainMap(fixed), "gain map metadata kept")
        val after = pixels(fixed)
        var changedOutside = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            val inBlocks = x / 8 in 20 / 8..27 / 8 && y / 8 in 15 / 8..21 / 8
            if (!inBlocks && before[x, y] != after[x, y]) changedOutside++
        }
        assertEquals(0, changedOutside, "squares away from the hole are bit-exact")
        for (y in 15 until 22) for (x in 20 until 28) {
            assertTrue((after[x, y] and 0xFF) < 250, "hole was refilled from around, not left white")
        }
        val src = Fixtures.bytes("uhdr.jpg")
        File(out, "repaired.jpg").writeBytes(UltraHdr.assemble(JpegWriter.copy(src, CopyCleaner.forCopy(null)), fixed))
    }

    @Test
    fun `hole maps changed photo pixels onto the smaller gain map`() {
        val changed = Mask(10, 10)
        changed[0, 0] = true
        val m = UltraHdr.hole(50, 40, 200, 160, listOf(IntRect(100, 80, 110, 90) to changed))
        assertTrue(m[25, 20], "the matching gain pixel")
        assertTrue(m[24, 20] && m[26, 20] && m[25, 19] && m[25, 21], "grown by one")
        assertEquals(false, m[0, 0])
    }

    @Test
    fun `grey full encode works for gain maps`() {
        val px = IntArray(16 * 8) { val v = it * 2; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val jpg = JpegEncoder.encode(16, 8, px, gray = true)
        val back = pixels(jpg)
        assertEquals(1, JpegParser.parse(jpg).frame.components.size)
        for (i in px.indices) assertTrue(kotlin.math.abs((back.px[i] and 0xFF) - (px[i] and 0xFF)) <= 1)
    }
}
