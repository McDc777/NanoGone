package app.nanogone.imaging.jpeg

import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.mask.Mask

class JpegInfo(val width: Int, val height: Int, val mcuWidth: Int, val mcuHeight: Int, val patchable: Boolean)

object JpegProbe {
    /** Size and MCU grid of a JPEG. `patchable` is false for kinds the patcher refuses. */
    fun info(bytes: ByteArray): JpegInfo = try {
        val p = JpegParser.parse(bytes)
        val f = p.frame
        val evenSampling = f.components.all { f.hMax % it.h == 0 && f.vMax % it.v == 0 }
        JpegInfo(f.width, f.height, p.mcuWidth, p.mcuHeight, evenSampling)
    } catch (e: UnsupportedJpegException) {
        val (w, h) = sizeOnly(bytes) ?: throw e
        JpegInfo(w, h, 16, 16, false)
    }

    private fun sizeOnly(b: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 9 < b.size) {
            if (JpegParser.u8(b, i) != 0xFF) return null
            val m = JpegParser.u8(b, i + 1)
            val len = (JpegParser.u8(b, i + 2) shl 8) or JpegParser.u8(b, i + 3)
            if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                val h = (JpegParser.u8(b, i + 5) shl 8) or JpegParser.u8(b, i + 6)
                val w = (JpegParser.u8(b, i + 7) shl 8) or JpegParser.u8(b, i + 8)
                return w to h
            }
            i += 2 + len
        }
        return null
    }
}

/**
 * Saves an edit into a JPEG by rewriting only the MCUs (8x8 or 16x16 squares) that contain a
 * changed pixel. Every other block keeps its exact coefficients, so it decodes to exactly the
 * same pixels as the original in any decoder.
 */
object BlockPatcher {

    /**
     * @param region image area covered by [pixels]; must contain every touched MCU (clipped to the image).
     * @param pixels ARGB of [region]: the edited result (original pixels where nothing changed).
     * @param changed which pixels of [region] were edited.
     * @param keep which pre-frame segments to keep (see CopyCleaner).
     */
    fun patch(
        original: ByteArray,
        region: IntRect,
        pixels: IntArray,
        changed: Mask,
        keep: (Segment) -> Boolean = { true },
    ): ByteArray {
        require(pixels.size == region.width * region.height) { "pixels do not match region" }
        require(changed.width == region.width && changed.height == region.height) { "mask does not match region" }
        val p = JpegParser.parse(original)
        val f = p.frame
        if (f.components.any { f.hMax % it.h != 0 || f.vMax % it.v != 0 }) throw UnsupportedJpegException("odd sampling")
        val image = IntRect(0, 0, f.width, f.height)
        require(image.contains(region)) { "region $region outside image" }
        val coeffs = CoefficientDecoder.decode(p)

        val touched = HashSet<Long>()
        for (y in 0 until region.height) for (x in 0 until region.width) {
            if (changed.bits[y * region.width + x]) {
                val mx = (region.left + x) / p.mcuWidth
                val my = (region.top + y) / p.mcuHeight
                touched.add(mx.toLong() shl 32 or my.toLong())
            }
        }
        val pixel = { x: Int, y: Int -> pixels[(y - region.top) * region.width + (x - region.left)] }
        for (key in touched) {
            val mx = (key shr 32).toInt()
            val my = key.toInt()
            val mcuRect = IntRect(mx * p.mcuWidth, my * p.mcuHeight, (mx + 1) * p.mcuWidth, (my + 1) * p.mcuHeight).intersect(image)
            require(region.contains(mcuRect)) { "region $region does not cover touched MCU $mcuRect" }
            ForwardDct.encodeMcu(p, coeffs, mx, my, pixel)
        }
        return JpegWriter.write(p, coeffs, keep)
    }
}
