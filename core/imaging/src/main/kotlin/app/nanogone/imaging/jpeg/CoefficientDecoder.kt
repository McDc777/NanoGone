package app.nanogone.imaging.jpeg

/** Decodes the entropy data into quantized coefficients (no IDCT). */
object CoefficientDecoder {

    fun decode(p: ParsedJpeg): Coefficients {
        val n = p.frame.components.size
        val bx = IntArray(n) { p.blocksX(it) }
        val by = IntArray(n) { p.blocksY(it) }
        val out = Coefficients(bx, by, Array(n) { ShortArray(bx[it] * by[it] * 64) })
        val reader = BitReader(p.entropy)
        val pred = IntArray(n)
        val ri = p.restartInterval
        var mcu = 0
        for (my in 0 until p.mcusY) {
            for (mx in 0 until p.mcusX) {
                if (ri > 0 && mcu > 0 && mcu % ri == 0) {
                    reader.restart()
                    pred.fill(0)
                }
                forEachBlockInMcu(p, mx, my) { sc, c, x, y ->
                    val dcT = p.dcTables[sc.td] ?: throw UnsupportedJpegException("missing DC table ${sc.td}")
                    val acT = p.acTables[sc.ta] ?: throw UnsupportedJpegException("missing AC table ${sc.ta}")
                    decodeBlock(reader, dcT, acT, pred, c, out.data[c], out.offset(c, x, y))
                }
                mcu++
            }
        }
        return out
    }

    /** Calls [block] for every block of MCU ([mx], [my]) in the order the scan stores them. */
    internal inline fun forEachBlockInMcu(p: ParsedJpeg, mx: Int, my: Int, block: (ScanComponent, Int, Int, Int) -> Unit) {
        if (!p.interleaved) {
            val sc = p.scan.components[0]
            block(sc, sc.index, mx, my)
            return
        }
        for (sc in p.scan.components) {
            val fc = p.frame.components[sc.index]
            for (v in 0 until fc.v) for (h in 0 until fc.h) {
                block(sc, sc.index, mx * fc.h + h, my * fc.v + v)
            }
        }
    }

    private fun decodeBlock(r: BitReader, dcT: HuffmanTable, acT: HuffmanTable, pred: IntArray, c: Int, dst: ShortArray, off: Int) {
        val t = dcT.decode(r)
        val diff = extend(r.bits(t), t)
        pred[c] += diff
        dst[off] = pred[c].toShort()
        var k = 1
        while (k < 64) {
            val rs = acT.decode(r)
            val run = rs shr 4
            val s = rs and 15
            if (s == 0) {
                if (run == 15) { k += 16; continue }
                break
            }
            k += run
            if (k > 63) throw UnsupportedJpegException("corrupt block")
            dst[off + k] = extend(r.bits(s), s).toShort()
            k++
        }
    }
}
