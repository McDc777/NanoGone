package app.nanogone.imaging.jpeg

import java.io.ByteArrayOutputStream

/** Turns coefficients back into entropy-coded data, with optimal Huffman tables. */
object CoefficientEncoder {

    class Result(val dcTables: Map<Int, HuffmanTable>, val acTables: Map<Int, HuffmanTable>, val entropy: ByteArray)

    fun encode(p: ParsedJpeg, coeffs: Coefficients): Result {
        val dcIds = p.scan.components.map { it.td }.distinct()
        val acIds = p.scan.components.map { it.ta }.distinct()
        val dcFreq = dcIds.associateWith { LongArray(256) }
        val acFreq = acIds.associateWith { LongArray(256) }
        walk(p, coeffs, object : Sink {
            override fun dc(table: Int, symbol: Int, extra: Int, extraBits: Int) { dcFreq.getValue(table)[symbol]++ }
            override fun ac(table: Int, symbol: Int, extra: Int, extraBits: Int) { acFreq.getValue(table)[symbol]++ }
            override fun restart(n: Int) {}
        })
        val dcT = dcFreq.mapValues { HuffmanOptimizer.build(it.value) }
        val acT = acFreq.mapValues { HuffmanOptimizer.build(it.value) }
        val w = BitWriter()
        walk(p, coeffs, object : Sink {
            override fun dc(table: Int, symbol: Int, extra: Int, extraBits: Int) {
                val t = dcT.getValue(table)
                w.put(t.codeOf[symbol], t.sizeOf[symbol])
                if (extraBits > 0) w.put(extra, extraBits)
            }
            override fun ac(table: Int, symbol: Int, extra: Int, extraBits: Int) {
                val t = acT.getValue(table)
                w.put(t.codeOf[symbol], t.sizeOf[symbol])
                if (extraBits > 0) w.put(extra, extraBits)
            }
            override fun restart(n: Int) = w.restart(n)
        })
        return Result(dcT, acT, w.finish())
    }

    private interface Sink {
        fun dc(table: Int, symbol: Int, extra: Int, extraBits: Int)
        fun ac(table: Int, symbol: Int, extra: Int, extraBits: Int)
        fun restart(n: Int)
    }

    private fun walk(p: ParsedJpeg, coeffs: Coefficients, sink: Sink) {
        val pred = IntArray(p.frame.components.size)
        val ri = p.restartInterval
        var mcu = 0
        var rst = 0
        for (my in 0 until p.mcusY) {
            for (mx in 0 until p.mcusX) {
                if (ri > 0 && mcu > 0 && mcu % ri == 0) {
                    sink.restart(rst)
                    rst = (rst + 1) and 7
                    pred.fill(0)
                }
                CoefficientDecoder.forEachBlockInMcu(p, mx, my) { sc, c, x, y ->
                    block(coeffs.data[c], coeffs.offset(c, x, y), sc, c, pred, sink)
                }
                mcu++
            }
        }
    }

    private fun block(d: ShortArray, off: Int, sc: ScanComponent, c: Int, pred: IntArray, sink: Sink) {
        val dcv = d[off].toInt()
        val diff = dcv - pred[c]
        pred[c] = dcv
        val s = magnitude(diff)
        sink.dc(sc.td, s, if (diff < 0) (diff - 1) and ((1 shl s) - 1) else diff, s)
        var run = 0
        for (k in 1 until 64) {
            val v = d[off + k].toInt()
            if (v == 0) { run++; continue }
            while (run > 15) { sink.ac(sc.ta, 0xF0, 0, 0); run -= 16 }
            val sz = magnitude(v)
            if (sz > 10) throw UnsupportedJpegException("coefficient out of range")
            sink.ac(sc.ta, (run shl 4) or sz, if (v < 0) (v - 1) and ((1 shl sz) - 1) else v, sz)
            run = 0
        }
        if (run > 0) sink.ac(sc.ta, 0x00, 0, 0)
    }

    private fun magnitude(v: Int): Int {
        var a = if (v < 0) -v else v
        var n = 0
        while (a > 0) { n++; a = a shr 1 }
        return n
    }

    private class BitWriter {
        private val out = ByteArrayOutputStream()
        private var acc = 0L
        private var n = 0

        fun put(code: Int, size: Int) {
            acc = (acc shl size) or (code.toLong() and ((1L shl size) - 1))
            n += size
            while (n >= 8) {
                val b = ((acc ushr (n - 8)) and 0xFF).toInt()
                out.write(b)
                if (b == 0xFF) out.write(0)
                n -= 8
            }
            acc = acc and ((1L shl n) - 1)
        }

        private fun pad() {
            if (n > 0) put((1 shl (8 - n)) - 1, 8 - n)
        }

        fun restart(i: Int) {
            pad()
            out.write(0xFF)
            out.write(0xD0 + i)
        }

        fun finish(): ByteArray {
            pad()
            return out.toByteArray()
        }
    }
}
