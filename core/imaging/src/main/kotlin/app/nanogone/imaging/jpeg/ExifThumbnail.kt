package app.nanogone.imaging.jpeg

/**
 * Replaces the small preview picture stored inside EXIF (IFD1), so the Gallery grid never shows
 * the removed thing. Works on the APP1 payload ("Exif\0\0" + TIFF).
 */
object ExifThumbnail {

    private const val TAG_OFFSET = 0x0201
    private const val TAG_LENGTH = 0x0202
    private const val HEADER = 6

    /**
     * @param newThumb a small JPEG, or null to just remove the old preview.
     * @return the new APP1 payload. If the old preview is not at the end of the EXIF block (so it
     * cannot be swapped safely) or the result would be too big, the preview link is removed instead.
     */
    fun replace(app1: ByteArray, newThumb: ByteArray?): ByteArray {
        if (app1.size < HEADER + 8) return app1
        val t = Tiff(app1, HEADER)
        val ifd0 = t.u32(4)
        if (ifd0 <= 0 || ifd0 + 2 > t.size) return app1
        val n0 = t.u16(ifd0)
        val nextPtrPos = ifd0 + 2 + 12 * n0
        if (nextPtrPos + 4 > t.size) return app1
        val ifd1 = t.u32(nextPtrPos)
        if (ifd1 <= 0 || ifd1 + 2 > t.size) return app1

        val n1 = t.u16(ifd1)
        var offEntry = -1
        var lenEntry = -1
        for (i in 0 until n1) {
            val e = ifd1 + 2 + 12 * i
            if (e + 12 > t.size) break
            when (t.u16(e)) {
                TAG_OFFSET -> offEntry = e
                TAG_LENGTH -> lenEntry = e
            }
        }
        val dropped = { app1.copyOf().also { Tiff(it, HEADER).put32(nextPtrPos, 0) } }
        if (offEntry < 0 || lenEntry < 0 || newThumb == null) return dropped()
        val thumbOff = t.u32(offEntry + 8)
        val thumbLen = t.u32(lenEntry + 8)
        val endsAtEnd = thumbOff + thumbLen == t.size || thumbOff + thumbLen == t.size - 1
        if (!endsAtEnd || thumbOff <= 0 || HEADER + thumbOff + newThumb.size + 2 > 0xFFFF) return dropped()

        val out = ByteArray(HEADER + thumbOff + newThumb.size)
        System.arraycopy(app1, 0, out, 0, HEADER + thumbOff)
        System.arraycopy(newThumb, 0, out, HEADER + thumbOff, newThumb.size)
        Tiff(out, HEADER).put32(lenEntry + 8, newThumb.size)
        return out
    }

    /** TIFF reader and writer that honours the II (little) or MM (big) byte order. */
    private class Tiff(val b: ByteArray, val base: Int) {
        val little = b[base] == 'I'.code.toByte()
        val size = b.size - base

        fun u16(o: Int): Int {
            val x = b[base + o].toInt() and 0xFF
            val y = b[base + o + 1].toInt() and 0xFF
            return if (little) x or (y shl 8) else (x shl 8) or y
        }

        fun u32(o: Int): Int = if (little) u16(o) or (u16(o + 2) shl 16) else (u16(o) shl 16) or u16(o + 2)

        fun put32(o: Int, v: Int) {
            val bytes = if (little) intArrayOf(v, v shr 8, v shr 16, v shr 24) else intArrayOf(v shr 24, v shr 16, v shr 8, v)
            for (i in 0 until 4) b[base + o + i] = bytes[i].toByte()
        }
    }
}
