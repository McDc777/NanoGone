package app.nanogone.imaging.jpeg

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class RoundTripTest {

    @Test
    fun `rewriting keeps every coefficient, table and size`() {
        val outDir = File("build/roundtrip").apply { mkdirs() }
        for (name in Fixtures.patchable) {
            val original = Fixtures.bytes(name)
            val p = JpegParser.parse(original)
            val c = CoefficientDecoder.decode(p)
            val rewritten = JpegWriter.write(p, c)
            File(outDir, name).writeBytes(rewritten)
            val p2 = JpegParser.parse(rewritten)
            val c2 = CoefficientDecoder.decode(p2)
            assertEquals(p.frame.width, p2.frame.width, name)
            assertEquals(p.frame.height, p2.frame.height, name)
            assertEquals(p.restartInterval, p2.restartInterval, name)
            for (t in 0 until 4) assertEquals(p.qTables[t]?.toList(), p2.qTables[t]?.toList(), "$name q$t")
            for (comp in c.data.indices) assertArrayEquals(c.data[comp], c2.data[comp], "$name component $comp")
        }
    }

    @Test
    fun `optimizer gives every used symbol a code of at most 16 bits`() {
        val freq = LongArray(256) { if (it % 3 == 0) (it * 7919L % 1000) + 1 else 0 }
        val t = HuffmanOptimizer.build(freq)
        for (s in 0 until 256) {
            if (freq[s] > 0) {
                assertEquals(true, t.sizeOf[s] in 1..16, "symbol $s")
            }
        }
    }
}
