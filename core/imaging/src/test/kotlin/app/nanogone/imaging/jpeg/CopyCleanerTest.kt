package app.nanogone.imaging.jpeg

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CopyCleanerTest {

    private fun seg(marker: Int, text: String, extra: ByteArray = ByteArray(0)) =
        Segment(marker, text.toByteArray(Charsets.ISO_8859_1) + extra)

    @Test
    fun `keeps what describes the photo, drops what still shows the removed thing`() {
        assertTrue(CopyCleaner.keepSegment(seg(0xE1, "Exif\u0000\u0000II*\u0000")))
        assertTrue(CopyCleaner.keepSegment(seg(0xE2, "ICC_PROFILE\u0000\u0001\u0001")))
        assertTrue(CopyCleaner.keepSegment(seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>plain</x:xmpmeta>")))
        assertFalse(CopyCleaner.keepSegment(seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x hdrgm:Version=\"1.0\"/>")))
        assertFalse(CopyCleaner.keepSegment(seg(0xE1, "http://ns.adobe.com/xap/1.0/\u0000<x GCamera:MotionPhoto=\"1\"/>")))
        assertFalse(CopyCleaner.keepSegment(seg(0xE2, "MPF\u0000MM")))
        assertFalse(CopyCleaner.keepSegment(seg(0xEB, "JP")))
        assertTrue(CopyCleaner.keepSegment(seg(0xEE, "Adobe")))
    }

    @Test
    fun `trailing Samsung data is never written and dropped segments disappear`() {
        val original = Fixtures.bytes("rgb420_q75.jpg")
        val p = JpegParser.parse(original)
        val withExtras = ByteArray(0) + original.copyOfRange(0, 2) +
            byteArrayOf(0xFF.toByte(), 0xE2.toByte(), 0, 8) + "MPF\u0000MM".toByteArray(Charsets.ISO_8859_1) +
            original.copyOfRange(2, original.size) + "SEFT motion video".toByteArray()
        val p2 = JpegParser.parse(withExtras)
        assertTrue(p2.trailing.isNotEmpty())
        val out = JpegWriter.write(p2, CoefficientDecoder.decode(p2), CopyCleaner.forCopy(null))
        val p3 = JpegParser.parse(out)
        assertEquals(0, p3.trailing.size)
        assertTrue(p3.segments.none { it.marker == 0xE2 })
        assertEquals(p.frame.width, p3.frame.width)
    }

    /** Builds an Exif APP1 payload with one IFD0 entry and an IFD1 whose thumbnail sits at the end. */
    private fun exif(little: Boolean, thumb: ByteArray): ByteArray {
        val tiff = ArrayList<Byte>()
        fun u16(v: Int) { if (little) { tiff += v.toByte(); tiff += (v shr 8).toByte() } else { tiff += (v shr 8).toByte(); tiff += v.toByte() } }
        fun u32(v: Int) { if (little) { u16(v and 0xFFFF); u16(v ushr 16) } else { u16(v ushr 16); u16(v and 0xFFFF) } }
        if (little) { tiff += 'I'.code.toByte(); tiff += 'I'.code.toByte() } else { tiff += 'M'.code.toByte(); tiff += 'M'.code.toByte() }
        u16(42); u32(8)
        // IFD0 at 8: 1 entry (Orientation = 1), next IFD at 26
        u16(1); u16(0x0112); u16(3); u32(1); u16(1); u16(0); u32(26)
        // IFD1 at 26: 2 entries, next 0; thumbnail at 26 + 2 + 24 + 4 = 56
        u16(2)
        u16(0x0201); u16(4); u32(1); u32(56)
        u16(0x0202); u16(4); u32(1); u32(thumb.size)
        u32(0)
        thumb.forEach { tiff += it }
        return "Exif\u0000\u0000".toByteArray(Charsets.ISO_8859_1) + tiff.toByteArray()
    }

    @Test
    fun `preview is swapped in both byte orders`() {
        for (little in listOf(true, false)) {
            val old = ByteArray(40) { 1 }
            val new = ByteArray(25) { 7 }
            val out = ExifThumbnail.replace(exif(little, old), new)
            assertEquals(6 + 56 + 25, out.size)
            assertArrayEquals(new, out.copyOfRange(out.size - 25, out.size))
            val expectedLen = if (little) byteArrayOf(25, 0, 0, 0) else byteArrayOf(0, 0, 0, 25)
            // Length entry value sits at IFD1 + 2 + 12 + 8 = 26 + 22 = 48 in the TIFF.
            assertArrayEquals(expectedLen, out.copyOfRange(6 + 48, 6 + 52))
        }
    }

    @Test
    fun `no new preview means the old link is removed`() {
        val out = ExifThumbnail.replace(exif(true, ByteArray(10)), null)
        // IFD0 next-pointer at 8 + 2 + 12 = 22 is now zero.
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), out.copyOfRange(6 + 22, 6 + 26))
    }
}
