package app.nanogone.imaging.jpeg

/**
 * Decides what metadata a NanoGone copy keeps. Keeps: JFIF, EXIF (date, place, camera,
 * orientation), plain XMP, ICC colour profile, IPTC, Adobe colour info, comments.
 * Drops anything that still holds the removed thing or describes data we do not write:
 * MPF (Samsung secondary images such as the HDR gain map), Ultra HDR and motion-photo XMP,
 * extended XMP (depth maps), JUMBF/C2PA and other app segments. Bytes after EOI (Samsung
 * motion video and SEFT data) are never written by [JpegWriter].
 */
object CopyCleaner {

    private const val EXIF = "Exif\u0000\u0000"
    private const val XMP = "http://ns.adobe.com/xap/1.0/\u0000"
    private val xmpDropWords = listOf("hdrgm", "MotionPhoto", "MicroVideo", "GContainer", "Container:Directory")

    fun keepSegment(s: Segment): Boolean = when (s.marker) {
        0xE0, 0xED, 0xEE, 0xFE, 0xDB -> true
        0xE1 -> s.startsWith(EXIF) || (s.startsWith(XMP) && xmpDropWords.none { s.contains(it) })
        0xE2 -> s.startsWith("ICC_PROFILE\u0000")
        in 0xE3..0xEF -> false
        else -> true
    }

    /**
     * Segment rewrite for a copy: drops what [keepSegment] refuses and, when [newThumbnail]
     * is given, swaps the EXIF preview for it (or removes the preview if it cannot be swapped).
     */
    fun forCopy(newThumbnail: ByteArray?): (Segment) -> Segment? = { s ->
        when {
            !keepSegment(s) -> null
            s.marker == 0xE1 && s.startsWith(EXIF) -> Segment(0xE1, ExifThumbnail.replace(s.data, newThumbnail))
            else -> s
        }
    }
}
