package app.nanogone.imaging.jpeg

import app.nanogone.imaging.geom.CropPlanner
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.mask.Mask
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class BlockPatcherTest {

    /** Pillow's decode of a fixture, as ARGB. */
    private fun decoded(name: String, w: Int, h: Int): IntArray {
        val raw = Fixtures.bytes(name.replace(".jpg", ".rgb"))
        return IntArray(w * h) { i ->
            val r = raw[i * 3].toInt() and 0xFF
            val g = raw[i * 3 + 1].toInt() and 0xFF
            val b = raw[i * 3 + 2].toInt() and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    @Test
    fun `only touched squares change, size stays the same`() {
        val outDir = File("build/patch").apply { mkdirs() }
        for (name in Fixtures.patchable) {
            val original = Fixtures.bytes(name)
            val info = JpegProbe.info(original)
            assertTrue(info.patchable, name)
            val w = info.width
            val h = info.height
            val full = decoded(name, w, h)
            // The "edit": a red square of 13 x 11 pixels near the middle.
            val edit = IntRect(w / 2 - 6, h / 2 - 5, w / 2 + 7, h / 2 + 6)
            val region = CropPlanner.alignOut(edit, info.mcuWidth, info.mcuHeight, w, h)
            val px = IntArray(region.width * region.height)
            val changed = Mask(region.width, region.height)
            for (y in region.top until region.bottom) for (x in region.left until region.right) {
                val i = (y - region.top) * region.width + (x - region.left)
                if (edit.contains(x, y)) {
                    px[i] = 0xFFE0201A.toInt()
                    changed.bits[i] = true
                } else {
                    px[i] = full[y * w + x]
                }
            }
            val patched = BlockPatcher.patch(original, region, px, changed)
            File(outDir, name).writeBytes(patched)
            // Expected picture for the Pillow check: original with the red square.
            val expected = full.copyOf()
            for (y in edit.top until edit.bottom) for (x in edit.left until edit.right) expected[y * w + x] = 0xFFE0201A.toInt()
            File(outDir, name.replace(".jpg", ".expected.rgb")).writeBytes(
                ByteArray(w * h * 3) { k -> ((expected[k / 3] shr (16 - 8 * (k % 3))) and 0xFF).toByte() },
            )

            val a = JpegParser.parse(original)
            val b = JpegParser.parse(patched)
            assertEquals(a.frame.width, b.frame.width)
            assertEquals(a.frame.height, b.frame.height)
            val ca = CoefficientDecoder.decode(a)
            val cb = CoefficientDecoder.decode(b)
            var changedBlocks = 0
            for (my in 0 until a.mcusY) for (mx in 0 until a.mcusX) {
                val mcuRect = IntRect(mx * a.mcuWidth, my * a.mcuHeight, (mx + 1) * a.mcuWidth, (my + 1) * a.mcuHeight)
                val touched = mcuRect.intersect(edit).let { !it.isEmpty }
                CoefficientDecoder.forEachBlockInMcu(a, mx, my) { _, c, bx, by ->
                    val o = ca.offset(c, bx, by)
                    val same = (0 until 64).all { ca.data[c][o + it] == cb.data[c][o + it] }
                    if (!touched) assertTrue(same, "$name: untouched block $c ($bx,$by) changed")
                    if (!same) changedBlocks++
                }
            }
            assertTrue(changedBlocks > 0, "$name: nothing changed")
        }
    }

    @Test
    fun `a region that misses a touched square is refused`() {
        val original = Fixtures.bytes("rgb420_q75.jpg")
        val region = IntRect(20, 20, 30, 30) // not aligned to 16 x 16
        val changed = Mask(10, 10).also { it[5, 5] = true }
        assertThrows<IllegalArgumentException> { BlockPatcher.patch(original, region, IntArray(100), changed) }
    }

    @Test
    fun `full encoder makes a valid file of the right size`() {
        val w = 37
        val h = 21
        val px = IntArray(w * h) { (0xFF shl 24) or ((it * 7) and 0xFF shl 16) or ((it * 3) and 0xFF shl 8) or (it and 0xFF) }
        val jpg = JpegEncoder.encode(w, h, px)
        File("build/patch").mkdirs()
        File("build/patch/full_encode.jpg").writeBytes(jpg)
        File("build/patch/full_encode.expected.rgb").writeBytes(ByteArray(w * h * 3) { k -> ((px[k / 3] shr (16 - 8 * (k % 3))) and 0xFF).toByte() })
        val p = JpegParser.parse(jpg)
        assertEquals(w, p.frame.width)
        assertEquals(h, p.frame.height)
    }
}
