package app.nanogone.imaging.jpeg

import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.image.Inpaint
import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps
import java.io.ByteArrayOutputStream

/**
 * Ultra HDR (JPEG with a gain map): the extra brightness layer that makes a photo glow on HDR
 * screens. The file is a normal JPEG followed by a second, small JPEG (the gain map), found
 * through the MPF index. A removal must be repaired in the gain map too, or the removed thing
 * would still glow there. Steps for a copy: [find] the gain map, [repair] the same hole in it,
 * then [assemble] the edited picture and the gain map into a new Ultra HDR file.
 */
object UltraHdr {

    private const val MPF = "MPF\u0000"
    private const val XMP = "http://ns.adobe.com/xap/1.0/\u0000"
    private const val ISO = "urn:iso:std:iso:ts:21496:-1\u0000"

    /** A gain map found in a photo. [jpeg] is the whole gain map file. */
    class GainMap(val jpeg: ByteArray, val isoVersion: Boolean)

    /** The gain map of an Ultra HDR photo, or null when the photo has none. */
    fun find(file: ByteArray): GainMap? {
        val header = headerSegments(file) ?: return null
        val mpf = header.firstOrNull { it.marker == 0xE2 && startsWith(file, it.dataStart, MPF) } ?: return null
        val tiff = mpf.dataStart + 4
        val entries = mpEntries(file, tiff, mpf.dataEnd) ?: return null
        val iso = header.any { it.marker == 0xE2 && startsWith(file, it.dataStart, ISO) }
        for ((i, e) in entries.withIndex()) {
            if (i == 0) continue
            val start = tiff.toLong() + e.second
            val end = start + e.first
            if (e.first <= 4 || start < tiff || end > file.size) continue
            val sub = file.copyOfRange(start.toInt(), end.toInt())
            if (isGainMap(sub)) return GainMap(sub, iso)
        }
        return null
    }

    /** True if [jpeg] carries gain map metadata (Adobe/Google XMP or ISO 21496-1). */
    fun isGainMap(jpeg: ByteArray): Boolean {
        val segs = headerSegments(jpeg) ?: return false
        return segs.any { s ->
            (s.marker == 0xE1 && startsWith(jpeg, s.dataStart, XMP) && String(jpeg, s.dataStart, s.dataEnd - s.dataStart, Charsets.ISO_8859_1).contains("hdrgm:")) ||
                (s.marker == 0xE2 && startsWith(jpeg, s.dataStart, ISO))
        }
    }

    /**
     * The hole in gain map pixels: every gain map pixel whose area touches a changed photo pixel,
     * grown by one pixel so the seam is covered too.
     * @param edits changed photo pixels: (where the mask sits in the photo, which pixels changed).
     */
    fun hole(gainW: Int, gainH: Int, photoW: Int, photoH: Int, edits: List<Pair<IntRect, Mask>>): Mask {
        val m = Mask(gainW, gainH)
        for ((r, changed) in edits) {
            for (y in 0 until changed.height) for (x in 0 until changed.width) {
                if (!changed[x, y]) continue
                val gx = ((r.left + x).toLong() * gainW / photoW).toInt().coerceIn(0, gainW - 1)
                val gy = ((r.top + y).toLong() * gainH / photoH).toInt().coerceIn(0, gainH - 1)
                m[gx, gy] = true
            }
        }
        return if (m.isEmpty()) m else MaskOps.grow(m, 1)
    }

    /**
     * Fills [hole] in the gain map from the gain around it. Only the gain map's changed squares are
     * rewritten (the rest stays bit-exact); unusual gain maps get a full top-quality encode.
     * @param pixels the decoded gain map (grey or colour), same size as the gain map.
     */
    fun repair(gainMap: ByteArray, pixels: Argb, hole: Mask): ByteArray {
        if (hole.isEmpty()) return gainMap
        val filled = Inpaint.smoothFill(pixels, hole)
        return try {
            BlockPatcher.patch(gainMap, IntRect(0, 0, pixels.width, pixels.height), filled.px, hole)
        } catch (_: UnsupportedJpegException) {
            val segs = headerSegments(gainMap).orEmpty()
                .filter { it.marker in 0xE0..0xEF }
                .map { Segment(it.marker, gainMap.copyOfRange(it.dataStart, it.dataEnd)) }
            val gray = runCatching { JpegParser.parse(gainMap).frame.components.size == 1 }.getOrDefault(true)
            JpegEncoder.encode(pixels.width, pixels.height, filled.px, segs, gray)
        }
    }

    /**
     * Joins an edited picture (a plain JPEG, any old HDR or MPF data already dropped) and a gain
     * map into one Ultra HDR file: adds the HDR XMP, the ISO version marker and a fresh MPF index.
     */
    fun assemble(primary: ByteArray, gainMap: ByteArray, isoVersion: Boolean = true): ByteArray {
        val segs = headerSegments(primary) ?: throw UnsupportedJpegException("not a JPEG")
        // Leading APP0 / APP1 EXIF stay first; any other XMP is replaced by ours.
        val keep = segs.filter { !(it.marker == 0xE1 && startsWith(primary, it.dataStart, XMP)) && !(it.marker == 0xE2 && (startsWith(primary, it.dataStart, MPF) || startsWith(primary, it.dataStart, ISO))) }
        val lead = keep.takeWhile { it.marker == 0xE0 || (it.marker == 0xE1 && !startsWith(primary, it.dataStart, XMP)) }
        val rest = keep.drop(lead.size)

        val out = ByteArrayOutputStream(primary.size + gainMap.size + 1024)
        out.write(0xFF); out.write(0xD8)
        for (s in lead) out.write(primary, s.segStart, s.segEnd - s.segStart)
        JpegWriter.segment(out, 0xE1, (XMP + xmp(gainMap.size)).toByteArray(Charsets.UTF_8))
        if (isoVersion) JpegWriter.segment(out, 0xE2, ISO.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0, 0, 0))
        val mpfAt = out.size()
        JpegWriter.segment(out, 0xE2, mpf(0, 0))
        // Everything after the header (tables, frame, scan, EOI) is copied as it is.
        for (s in rest) out.write(primary, s.segStart, s.segEnd - s.segStart)
        val tail = segs.lastOrNull()?.segEnd ?: 2
        out.write(primary, tail, primary.size - tail)
        val head = out.toByteArray()
        val tiff = mpfAt + 4 + 4
        val fixed = mpf(head.size, head.size - tiff, gainMap.size)
        System.arraycopy(fixed, 0, head, mpfAt + 4, fixed.size)
        return head + gainMap
    }

    /** Ultra HDR v1 XMP for the main picture: hdrgm version and the container directory. */
    private fun xmp(gainMapLength: Int): String =
        """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="NanoGone">""" +
            """<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""" +
            """<rdf:Description xmlns:Container="http://ns.google.com/photos/1.0/container/" """ +
            """xmlns:Item="http://ns.google.com/photos/1.0/container/item/" """ +
            """xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/" hdrgm:Version="1.0">""" +
            """<Container:Directory><rdf:Seq>""" +
            """<rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="Primary" Item:Mime="image/jpeg"/></rdf:li>""" +
            """<rdf:li rdf:parseType="Resource"><Container:Item Item:Semantic="GainMap" Item:Mime="image/jpeg" Item:Length="$gainMapLength"/></rdf:li>""" +
            """</rdf:Seq></Container:Directory></rdf:Description></rdf:RDF></x:xmpmeta>"""

    /**
     * MPF APP2 body (big-endian), two images: the main picture (whole file part before the gain
     * map) and the gain map at [gainOffset] from the MPF TIFF header. Always 86 bytes.
     */
    internal fun mpf(primarySize: Int, gainOffset: Int, gainSize: Int = 0): ByteArray {
        val b = ByteArrayOutputStream()
        fun u16(v: Int) { b.write(v shr 8 and 0xFF); b.write(v and 0xFF) }
        fun u32(v: Int) { u16(v ushr 16); u16(v and 0xFFFF) }
        b.write(MPF.toByteArray(Charsets.ISO_8859_1))
        b.write('M'.code); b.write('M'.code); u16(0x2A); u32(8)
        u16(3)
        u16(0xB000); u16(7); u32(4); b.write("0100".toByteArray(Charsets.ISO_8859_1))
        u16(0xB001); u16(4); u32(1); u32(2)
        u16(0xB002); u16(7); u32(32); u32(8 + 2 + 3 * 12 + 4)
        u32(0)
        u32(0x00030000); u32(primarySize); u32(0); u16(0); u16(0)  // baseline MP primary, as libultrahdr writes it
        u32(0); u32(gainSize); u32(gainOffset); u16(0); u16(0)
        return b.toByteArray()
    }

    /** MP entries as (size, offset from the TIFF header) pairs, or null if the index is broken. */
    private fun mpEntries(b: ByteArray, tiff: Int, end: Int): List<Pair<Long, Long>>? {
        if (tiff + 8 > end) return null
        val little = b[tiff] == 'I'.code.toByte()
        fun u16(o: Int): Int {
            val x = b[tiff + o].toInt() and 0xFF
            val y = b[tiff + o + 1].toInt() and 0xFF
            return if (little) x or (y shl 8) else (x shl 8) or y
        }
        fun u32(o: Int): Long {
            val lo = u16(if (little) o else o + 2).toLong()
            val hi = u16(if (little) o + 2 else o).toLong()
            return (hi shl 16) or lo
        }
        val ifd = u32(4).toInt()
        if (tiff + ifd + 2 > end) return null
        val n = u16(ifd)
        for (k in 0 until n) {
            val e = ifd + 2 + 12 * k
            if (tiff + e + 12 > end) return null
            if (u16(e) == 0xB002) {
                val count = u32(e + 4).toInt()
                val at = u32(e + 8).toInt()
                if (count % 16 != 0 || tiff + at + count > end) return null
                return (0 until count / 16).map { i -> u32(at + 16 * i + 4) to u32(at + 16 * i + 8) }
            }
        }
        return null
    }

    /** A marker segment's place in the file. */
    private class Seg(val marker: Int, val segStart: Int, val dataStart: Int, val dataEnd: Int) {
        val segEnd: Int get() = dataEnd
    }

    /** Segments from SOI up to (not including) SOS. Null if [b] is not a JPEG. */
    private fun headerSegments(b: ByteArray): List<Seg>? {
        if (b.size < 4 || (b[0].toInt() and 0xFF) != 0xFF || (b[1].toInt() and 0xFF) != 0xD8) return null
        val out = ArrayList<Seg>()
        var pos = 2
        while (pos + 4 <= b.size) {
            if ((b[pos].toInt() and 0xFF) != 0xFF) return out
            var p = pos
            while (p + 1 < b.size && (b[p + 1].toInt() and 0xFF) == 0xFF) p++
            val marker = b[p + 1].toInt() and 0xFF
            if (marker == 0xDA || marker == 0xD9) return out
            val len = ((b[p + 2].toInt() and 0xFF) shl 8) or (b[p + 3].toInt() and 0xFF)
            if (len < 2 || p + 2 + len > b.size) return out
            out.add(Seg(marker, pos, p + 4, p + 2 + len))
            pos = p + 2 + len
        }
        return out
    }

    private fun startsWith(b: ByteArray, at: Int, text: String): Boolean {
        val t = text.toByteArray(Charsets.ISO_8859_1)
        if (at + t.size > b.size) return false
        for (i in t.indices) if (b[at + i] != t[i]) return false
        return true
    }
}
