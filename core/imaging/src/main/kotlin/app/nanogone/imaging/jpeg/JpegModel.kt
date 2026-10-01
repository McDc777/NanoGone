package app.nanogone.imaging.jpeg

/** Thrown for JPEG kinds we do not patch (progressive, arithmetic, 12-bit, multi-scan, RGB-coded). */
class UnsupportedJpegException(message: String) : Exception(message)

/** One marker segment: [marker] is the second marker byte (for example 0xE1 for APP1). [data] excludes the length. */
class Segment(val marker: Int, val data: ByteArray) {
    fun startsWith(text: String): Boolean {
        val b = text.toByteArray(Charsets.ISO_8859_1)
        if (data.size < b.size) return false
        for (i in b.indices) if (data[i] != b[i]) return false
        return true
    }

    fun contains(text: String): Boolean = String(data, Charsets.ISO_8859_1).contains(text)
}

class FrameComponent(val id: Int, val h: Int, val v: Int, val tq: Int)

class Frame(
    val marker: Int,
    val width: Int,
    val height: Int,
    val components: List<FrameComponent>,
) {
    val hMax: Int = components.maxOf { it.h }
    val vMax: Int = components.maxOf { it.v }
}

/** One component of the scan: [index] into the frame's component list, DC and AC table ids. */
class ScanComponent(val index: Int, val td: Int, val ta: Int)

class ScanHeader(val components: List<ScanComponent>, val ss: Int, val se: Int, val ah: Int, val al: Int)

class ParsedJpeg(
    /** Every segment before SOS, in file order (APPn, COM, DQT, DHT, SOF, DRI...). */
    val segments: List<Segment>,
    val frame: Frame,
    /** Quantization tables in zigzag order, indexed by table id 0..3. */
    val qTables: Array<IntArray?>,
    val dcTables: Array<HuffmanTable?>,
    val acTables: Array<HuffmanTable?>,
    val restartInterval: Int,
    val scan: ScanHeader,
    /** Entropy-coded bytes between the SOS header and EOI, with stuffing and RST markers. */
    val entropy: ByteArray,
    /** Bytes after EOI (Samsung trailers, MPF secondary images). Never written to a copy. */
    val trailing: ByteArray,
) {
    val interleaved: Boolean get() = scan.components.size > 1
    val mcuWidth: Int get() = if (interleaved) 8 * frame.hMax else 8
    val mcuHeight: Int get() = if (interleaved) 8 * frame.vMax else 8

    /** Blocks per row for component [c], padded to whole MCUs when interleaved. */
    fun blocksX(c: Int): Int {
        val fc = frame.components[c]
        return if (interleaved) mcusX * fc.h else ceilDiv(ceilDiv(frame.width * fc.h, frame.hMax), 8)
    }

    fun blocksY(c: Int): Int {
        val fc = frame.components[c]
        return if (interleaved) mcusY * fc.v else ceilDiv(ceilDiv(frame.height * fc.v, frame.vMax), 8)
    }

    val mcusX: Int get() = if (interleaved) ceilDiv(frame.width, mcuWidth) else blocksXSingle()
    val mcusY: Int get() = if (interleaved) ceilDiv(frame.height, mcuHeight) else blocksYSingle()

    private fun blocksXSingle(): Int {
        val fc = frame.components[scan.components[0].index]
        return ceilDiv(ceilDiv(frame.width * fc.h, frame.hMax), 8)
    }

    private fun blocksYSingle(): Int {
        val fc = frame.components[scan.components[0].index]
        return ceilDiv(ceilDiv(frame.height * fc.v, frame.vMax), 8)
    }
}

/** Quantized DCT coefficients per component, in zigzag order, 64 per block. */
class Coefficients(val blocksX: IntArray, val blocksY: IntArray, val data: Array<ShortArray>) {
    fun offset(c: Int, bx: Int, by: Int): Int = (by * blocksX[c] + bx) * 64

    fun copy(): Coefficients = Coefficients(blocksX.copyOf(), blocksY.copyOf(), Array(data.size) { data[it].copyOf() })
}

internal fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

/** Natural (row-major) index of each zigzag position. */
internal val ZIGZAG_TO_NATURAL = intArrayOf(
    0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
    12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63,
)
