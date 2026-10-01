package app.nanogone.imaging.jpeg

/** Splits a JPEG into segments, tables, frame, scan and entropy data. Baseline Huffman only. */
object JpegParser {

    fun parse(bytes: ByteArray): ParsedJpeg {
        if (bytes.size < 4 || u8(bytes, 0) != 0xFF || u8(bytes, 1) != 0xD8) throw UnsupportedJpegException("not a JPEG")
        val segments = ArrayList<Segment>()
        val q = arrayOfNulls<IntArray>(4)
        val dc = arrayOfNulls<HuffmanTable>(4)
        val ac = arrayOfNulls<HuffmanTable>(4)
        var frame: Frame? = null
        var restart = 0
        var adobeRgb = false
        var pos = 2
        while (true) {
            if (pos + 4 > bytes.size) throw UnsupportedJpegException("no scan found")
            if (u8(bytes, pos) != 0xFF) throw UnsupportedJpegException("bad marker at $pos")
            var marker = u8(bytes, pos + 1)
            while (marker == 0xFF) { pos++; marker = u8(bytes, pos + 1) }
            val len = (u8(bytes, pos + 2) shl 8) or u8(bytes, pos + 3)
            val data = bytes.copyOfRange(pos + 4, pos + 2 + len)
            pos += 2 + len
            when (marker) {
                0xC0, 0xC1 -> frame = readFrame(marker, data)
                0xC2, 0xC3, in 0xC5..0xC7, in 0xC9..0xCB, in 0xCD..0xCF ->
                    throw UnsupportedJpegException("unsupported frame type 0x${marker.toString(16)}")
                0xC4 -> readDht(data, dc, ac)
                0xDB -> readDqt(data, q)
                0xDD -> restart = (u8(data, 0) shl 8) or u8(data, 1)
                0xEE -> if (data.size >= 12 && String(data, 0, 5, Charsets.ISO_8859_1) == "Adobe" && u8(data, 11) == 0) adobeRgb = true
                0xDA -> {
                    val f = frame ?: throw UnsupportedJpegException("scan before frame")
                    val scan = readScan(data, f)
                    if (scan.ss != 0 || scan.se != 63 || scan.ah != 0 || scan.al != 0) throw UnsupportedJpegException("not a sequential scan")
                    if (scan.components.size != f.components.size) throw UnsupportedJpegException("multi-scan JPEG")
                    if (adobeRgb && f.components.size == 3) throw UnsupportedJpegException("RGB-coded JPEG")
                    val end = findScanEnd(bytes, pos)
                    if (end + 1 >= bytes.size || u8(bytes, end + 1) != 0xD9) throw UnsupportedJpegException("more than one scan")
                    segments.add(Segment(marker, data))
                    return ParsedJpeg(
                        segments = segments.subList(0, segments.size - 1).toList(),
                        frame = f, qTables = q, dcTables = dc, acTables = ac,
                        restartInterval = restart, scan = scan,
                        entropy = bytes.copyOfRange(pos, end),
                        trailing = bytes.copyOfRange(end + 2, bytes.size),
                    )
                }
            }
            if (marker != 0xDA) segments.add(Segment(marker, data))
        }
    }

    /** Index of the 0xFF of the first real marker after the entropy data (skips stuffing and RSTn). */
    private fun findScanEnd(b: ByteArray, from: Int): Int {
        var i = from
        while (i + 1 < b.size) {
            if (u8(b, i) == 0xFF) {
                val n = u8(b, i + 1)
                if (n != 0x00 && n !in 0xD0..0xD7 && n != 0xFF) return i
            }
            i++
        }
        throw UnsupportedJpegException("missing EOI")
    }

    private fun readFrame(marker: Int, d: ByteArray): Frame {
        if (u8(d, 0) != 8) throw UnsupportedJpegException("not 8-bit")
        val h = (u8(d, 1) shl 8) or u8(d, 2)
        val w = (u8(d, 3) shl 8) or u8(d, 4)
        val n = u8(d, 5)
        if (h == 0 || w == 0) throw UnsupportedJpegException("missing size")
        if (n != 1 && n != 3) throw UnsupportedJpegException("$n components")
        val comps = (0 until n).map { i ->
            val o = 6 + i * 3
            FrameComponent(u8(d, o), u8(d, o + 1) shr 4, u8(d, o + 1) and 15, u8(d, o + 2))
        }
        return Frame(marker, w, h, comps)
    }

    private fun readDqt(d: ByteArray, q: Array<IntArray?>) {
        var p = 0
        while (p < d.size) {
            val pq = u8(d, p) shr 4
            val tq = u8(d, p) and 15
            p++
            q[tq] = IntArray(64) { i -> if (pq == 0) u8(d, p + i) else (u8(d, p + 2 * i) shl 8) or u8(d, p + 2 * i + 1) }
            p += if (pq == 0) 64 else 128
        }
    }

    private fun readDht(d: ByteArray, dc: Array<HuffmanTable?>, ac: Array<HuffmanTable?>) {
        var p = 0
        while (p < d.size) {
            val tc = u8(d, p) shr 4
            val th = u8(d, p) and 15
            val bits = IntArray(16) { u8(d, p + 1 + it) }
            val n = bits.sum()
            val values = IntArray(n) { u8(d, p + 17 + it) }
            p += 17 + n
            val t = HuffmanTable(bits, values)
            if (tc == 0) dc[th] = t else ac[th] = t
        }
    }

    private fun readScan(d: ByteArray, f: Frame): ScanHeader {
        val n = u8(d, 0)
        val comps = (0 until n).map { i ->
            val id = u8(d, 1 + 2 * i)
            val t = u8(d, 2 + 2 * i)
            val index = f.components.indexOfFirst { it.id == id }
            if (index < 0) throw UnsupportedJpegException("scan names unknown component $id")
            ScanComponent(index, t shr 4, t and 15)
        }
        val o = 1 + 2 * n
        return ScanHeader(comps, u8(d, o), u8(d, o + 1), u8(d, o + 2) shr 4, u8(d, o + 2) and 15)
    }

    internal fun u8(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
}
