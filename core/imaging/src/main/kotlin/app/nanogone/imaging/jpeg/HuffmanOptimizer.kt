package app.nanogone.imaging.jpeg

/** Builds an optimal Huffman table from symbol counts (JPEG Annex K.2, lengths limited to 16). */
object HuffmanOptimizer {

    fun build(freqIn: LongArray): HuffmanTable {
        require(freqIn.size == 256)
        val freq = LongArray(257)
        for (i in 0 until 256) freq[i] = freqIn[i]
        freq[256] = 1 // reserved so no real code is all ones
        val codeSize = IntArray(257)
        val others = IntArray(257) { -1 }
        while (true) {
            var c1 = -1
            var v = Long.MAX_VALUE
            for (i in 0..256) if (freq[i] != 0L && freq[i] <= v) { v = freq[i]; c1 = i }
            var c2 = -1
            v = Long.MAX_VALUE
            for (i in 0..256) if (freq[i] != 0L && freq[i] <= v && i != c1) { v = freq[i]; c2 = i }
            if (c2 < 0) break
            freq[c1] += freq[c2]
            freq[c2] = 0
            codeSize[c1]++
            while (others[c1] >= 0) { c1 = others[c1]; codeSize[c1]++ }
            others[c1] = c2
            codeSize[c2]++
            while (others[c2] >= 0) { c2 = others[c2]; codeSize[c2]++ }
        }
        val bits = IntArray(33)
        for (i in 0..256) if (codeSize[i] > 0) bits[codeSize[i]]++
        var i = 32
        while (i > 16) {
            while (bits[i] > 0) {
                var j = i - 2
                while (bits[j] == 0) j--
                bits[i] -= 2
                bits[i - 1]++
                bits[j + 1] += 2
                bits[j]--
            }
            i--
        }
        while (bits[i] == 0) i--
        bits[i]-- // drop the reserved code
        val values = ArrayList<Int>()
        for (len in 1..32) for (sym in 0 until 256) if (codeSize[sym] == len) values.add(sym)
        return HuffmanTable(IntArray(16) { bits[it + 1] }, values.toIntArray())
    }
}
