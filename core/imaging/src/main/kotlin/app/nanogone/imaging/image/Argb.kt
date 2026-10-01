package app.nanogone.imaging.image

import app.nanogone.imaging.geom.IntRect

/** A plain ARGB image (0xAARRGGBB per pixel, row by row). */
class Argb(val width: Int, val height: Int, val px: IntArray = IntArray(width * height)) {
    init {
        require(width > 0 && height > 0 && px.size == width * height) { "bad image ${width}x$height/${px.size}" }
    }

    operator fun get(x: Int, y: Int): Int = px[y * width + x]

    operator fun set(x: Int, y: Int, value: Int) {
        px[y * width + x] = value
    }

    fun crop(r: IntRect): Argb {
        require(IntRect(0, 0, width, height).contains(r) && !r.isEmpty) { "crop $r outside ${width}x$height" }
        val out = Argb(r.width, r.height)
        for (y in 0 until r.height) System.arraycopy(px, (r.top + y) * width + r.left, out.px, y * r.width, r.width)
        return out
    }

    fun copy(): Argb = Argb(width, height, px.copyOf())
}
