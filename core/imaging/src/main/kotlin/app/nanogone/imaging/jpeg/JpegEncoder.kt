package app.nanogone.imaging.jpeg

/** Full JPEG encode at top quality (all quantizers 1, 4:4:4 chroma). Used when patching is not possible. */
object JpegEncoder {

    /**
     * @param keepSegments metadata segments to carry over (APPn, COM), written before the image tables.
     * @param gray write one grey channel (the brightness), for grey images such as HDR gain maps.
     */
    fun encode(width: Int, height: Int, argb: IntArray, keepSegments: List<Segment> = emptyList(), gray: Boolean = false): ByteArray {
        require(width in 1..65535 && height in 1..65535 && argb.size == width * height) { "bad image" }
        val sof = if (gray) {
            byteArrayOf(8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 1, 1, 0x11, 0)
        } else {
            byteArrayOf(
                8, (height shr 8).toByte(), height.toByte(), (width shr 8).toByte(), width.toByte(), 3,
                1, 0x11, 0, 2, 0x11, 1, 3, 0x11, 1,
            )
        }
        val dqt = ByteArray(if (gray) 65 else 2 * 65).also { d ->
            d[0] = 0
            for (i in 1..64) d[i] = 1
            if (!gray) {
                d[65] = 1
                for (i in 66 until 130) d[i] = 1
            }
        }
        val segs = ArrayList<Segment>()
        val meta = keepSegments.filter { it.marker in 0xE0..0xEF || it.marker == 0xFE }
        if (meta.none { it.marker == 0xE0 || it.marker == 0xE1 }) {
            segs.add(Segment(0xE0, byteArrayOf(0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0)))
        }
        segs.addAll(meta)
        segs.add(Segment(0xDB, dqt))
        segs.add(Segment(0xC0, sof))
        val frame = if (gray) {
            Frame(0xC0, width, height, listOf(FrameComponent(1, 1, 1, 0)))
        } else {
            Frame(0xC0, width, height, listOf(FrameComponent(1, 1, 1, 0), FrameComponent(2, 1, 1, 1), FrameComponent(3, 1, 1, 1)))
        }
        val ones = IntArray(64) { 1 }
        val p = ParsedJpeg(
            segments = segs, frame = frame,
            qTables = arrayOf(ones, ones, null, null),
            dcTables = arrayOfNulls(4), acTables = arrayOfNulls(4),
            restartInterval = 0,
            scan = if (gray) ScanHeader(listOf(ScanComponent(0, 0, 0)), 0, 63, 0, 0)
            else ScanHeader(listOf(ScanComponent(0, 0, 0), ScanComponent(1, 1, 1), ScanComponent(2, 1, 1)), 0, 63, 0, 0),
            entropy = ByteArray(0), trailing = ByteArray(0),
        )
        val n = if (gray) 1 else 3
        val bx = IntArray(n) { p.blocksX(it) }
        val by = IntArray(n) { p.blocksY(it) }
        val coeffs = Coefficients(bx, by, Array(n) { ShortArray(bx[it] * by[it] * 64) })
        val pixel = { x: Int, y: Int -> argb[y * width + x] }
        for (my in 0 until p.mcusY) for (mx in 0 until p.mcusX) ForwardDct.encodeMcu(p, coeffs, mx, my, pixel)
        return JpegWriter.write(p, coeffs)
    }
}
