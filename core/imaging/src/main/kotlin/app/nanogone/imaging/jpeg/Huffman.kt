package app.nanogone.imaging.jpeg

/** A Huffman table as stored in DHT: [bits][i] = number of codes of length i+1, [values] in code order. */
class HuffmanTable(val bits: IntArray, val values: IntArray) {
    private val minCode = IntArray(17)
    private val maxCode = IntArray(18) { -1 }
    private val valPtr = IntArray(17)

    /** Code and length per symbol value, for writing. Length 0 means "no code". */
    val codeOf = IntArray(256)
    val sizeOf = IntArray(256)

    init {
        require(bits.size == 16 && values.size == bits.sum()) { "bad Huffman table" }
        var code = 0
        var k = 0
        for (len in 1..16) {
            val n = bits[len - 1]
            if (n > 0) {
                valPtr[len] = k
                minCode[len] = code
                for (i in 0 until n) {
                    val v = values[k]
                    codeOf[v] = code
                    sizeOf[v] = len
                    code++
                    k++
                }
                maxCode[len] = code - 1
            } else {
                maxCode[len] = -1
            }
            code = code shl 1
        }
        maxCode[17] = Int.MAX_VALUE
    }

    fun decode(r: BitReader): Int {
        var code = r.bit()
        var len = 1
        while (len <= 16 && code > maxCode[len]) {
            code = (code shl 1) or r.bit()
            len++
        }
        if (len > 16) throw UnsupportedJpegException("corrupt Huffman data")
        return values[valPtr[len] + code - minCode[len]]
    }
}

/** Reads entropy-coded bits, undoing 0xFF00 stuffing. Stops at markers (feeds zeros). */
class BitReader(private val data: ByteArray) {
    private var pos = 0
    private var acc = 0
    private var left = 0
    private var hitMarker = false

    fun bit(): Int {
        if (left == 0) fill()
        left--
        return (acc ushr left) and 1
    }

    fun bits(n: Int): Int {
        var v = 0
        repeat(n) { v = (v shl 1) or bit() }
        return v
    }

    private fun fill() {
        if (hitMarker || pos >= data.size) {
            acc = 0; left = 8; return
        }
        val b = data[pos].toInt() and 0xFF
        if (b == 0xFF) {
            val next = if (pos + 1 < data.size) data[pos + 1].toInt() and 0xFF else 0xD9
            if (next == 0x00) {
                pos += 2
            } else {
                hitMarker = true
                acc = 0; left = 8; return
            }
        } else {
            pos++
        }
        acc = b
        left = 8
    }

    /** Drop leftover bits and step over the next RSTn marker. */
    fun restart() {
        left = 0
        hitMarker = false
        while (pos + 1 < data.size) {
            val b = data[pos].toInt() and 0xFF
            val n = data[pos + 1].toInt() and 0xFF
            if (b == 0xFF && n in 0xD0..0xD7) {
                pos += 2
                return
            }
            pos++
        }
    }
}

/** Sign-extend an [s]-bit magnitude [v] (JPEG "EXTEND"). */
internal fun extend(v: Int, s: Int): Int = if (s == 0) 0 else if (v < (1 shl (s - 1))) v - (1 shl s) + 1 else v
