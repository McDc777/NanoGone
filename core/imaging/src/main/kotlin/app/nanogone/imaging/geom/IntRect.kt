package app.nanogone.imaging.geom

/** Pixel rectangle. [right] and [bottom] are exclusive. */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    fun contains(other: IntRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun intersect(other: IntRect): IntRect = IntRect(
        maxOf(left, other.left), maxOf(top, other.top),
        minOf(right, other.right), minOf(bottom, other.bottom),
    )

    fun offset(dx: Int, dy: Int): IntRect = IntRect(left + dx, top + dy, right + dx, bottom + dy)
}
