package app.nanogone.imaging.mask

import app.nanogone.imaging.geom.IntRect

/** An on/off mask the size of an image (or a crop of one), stored row by row. */
class Mask(val width: Int, val height: Int, val bits: BooleanArray = BooleanArray(width * height)) {
    init {
        require(width > 0 && height > 0) { "mask must not be empty: ${width}x$height" }
        require(bits.size == width * height) { "bits size ${bits.size} != ${width * height}" }
    }

    operator fun get(x: Int, y: Int): Boolean = bits[y * width + x]

    operator fun set(x: Int, y: Int, value: Boolean) {
        bits[y * width + x] = value
    }

    fun count(): Int = bits.count { it }

    fun isEmpty(): Boolean = bits.none { it }

    fun copy(): Mask = Mask(width, height, bits.copyOf())

    /** Smallest rectangle holding every on pixel, or null when the mask is empty. */
    fun bounds(): IntRect? {
        var l = width; var t = height; var r = -1; var b = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (bits[row + x]) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        return if (r < 0) null else IntRect(l, t, r + 1, b + 1)
    }

    override fun equals(other: Any?): Boolean =
        other is Mask && other.width == width && other.height == height && other.bits.contentEquals(bits)

    override fun hashCode(): Int = 31 * (31 * width + height) + bits.contentHashCode()

    override fun toString(): String = "Mask(${width}x$height, on=${count()})"
}
