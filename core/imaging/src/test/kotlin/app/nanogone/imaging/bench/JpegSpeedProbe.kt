package app.nanogone.imaging.bench

import app.nanogone.imaging.jpeg.CoefficientDecoder
import app.nanogone.imaging.jpeg.JpegParser
import app.nanogone.imaging.jpeg.JpegWriter
import org.junit.jupiter.api.Test
import java.io.File

class JpegSpeedProbe {
    @Test
    fun timings() {
        val f = File(System.getProperty("bigJpeg") ?: return)
        if (!f.exists()) return
        val bytes = f.readBytes()
        repeat(2) {
            var t = System.nanoTime()
            val p = JpegParser.parse(bytes)
            val c = CoefficientDecoder.decode(p)
            println("decode 12MP: ${(System.nanoTime() - t) / 1_000_000} ms")
            t = System.nanoTime()
            JpegWriter.write(p, c)
            println("encode 12MP: ${(System.nanoTime() - t) / 1_000_000} ms")
        }
    }
}
