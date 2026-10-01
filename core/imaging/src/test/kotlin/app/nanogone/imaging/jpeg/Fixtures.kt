package app.nanogone.imaging.jpeg

object Fixtures {
    val patchable = listOf("rgb444_q95.jpg", "rgb420_q75.jpg", "rgb422_q90.jpg", "gray_q85.jpg", "rgb420_restart.jpg")

    fun bytes(name: String): ByteArray =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/jpeg/$name")) { "missing fixture $name" }.readBytes()
}
