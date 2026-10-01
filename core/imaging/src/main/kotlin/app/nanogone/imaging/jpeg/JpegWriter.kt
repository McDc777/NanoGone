package app.nanogone.imaging.jpeg

import java.io.ByteArrayOutputStream

/**
 * Writes a JPEG from a parsed file and (possibly changed) coefficients. Trailing bytes are never written.
 * [rewrite] can change or drop (null) each metadata segment; see [CopyCleaner.forCopy].
 */
object JpegWriter {

    fun write(p: ParsedJpeg, coeffs: Coefficients, rewrite: (Segment) -> Segment? = { it }): ByteArray {
        val enc = CoefficientEncoder.encode(p, coeffs)
        val out = ByteArrayOutputStream(p.entropy.size + 4096)
        out.write(0xFF); out.write(0xD8)
        val sof = p.segments.last { it.marker == 0xC0 || it.marker == 0xC1 }
        for (s in p.segments) {
            if (s.marker == 0xC0 || s.marker == 0xC1 || s.marker == 0xC4 || s.marker == 0xDD) continue
            val r = rewrite(s) ?: continue
            segment(out, r.marker, r.data)
        }
        segment(out, sof.marker, sof.data)
        segment(out, 0xC4, dht(enc))
        if (p.restartInterval > 0) segment(out, 0xDD, byteArrayOf((p.restartInterval shr 8).toByte(), p.restartInterval.toByte()))
        segment(out, 0xDA, sos(p))
        out.write(enc.entropy)
        out.write(0xFF); out.write(0xD9)
        return out.toByteArray()
    }

    internal fun segment(out: ByteArrayOutputStream, marker: Int, data: ByteArray) {
        val len = data.size + 2
        require(len <= 0xFFFF) { "segment too long" }
        out.write(0xFF); out.write(marker)
        out.write(len shr 8); out.write(len and 0xFF)
        out.write(data)
    }

    private fun dht(enc: CoefficientEncoder.Result): ByteArray {
        val b = ByteArrayOutputStream()
        fun table(tc: Int, id: Int, t: HuffmanTable) {
            b.write((tc shl 4) or id)
            t.bits.forEach { b.write(it) }
            t.values.forEach { b.write(it) }
        }
        enc.dcTables.toSortedMap().forEach { (id, t) -> table(0, id, t) }
        enc.acTables.toSortedMap().forEach { (id, t) -> table(1, id, t) }
        return b.toByteArray()
    }

    private fun sos(p: ParsedJpeg): ByteArray {
        val b = ByteArrayOutputStream()
        b.write(p.scan.components.size)
        for (sc in p.scan.components) {
            b.write(p.frame.components[sc.index].id)
            b.write((sc.td shl 4) or sc.ta)
        }
        b.write(p.scan.ss); b.write(p.scan.se); b.write((p.scan.ah shl 4) or p.scan.al)
        return b.toByteArray()
    }
}
