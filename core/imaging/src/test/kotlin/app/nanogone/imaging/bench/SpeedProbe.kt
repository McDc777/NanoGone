package app.nanogone.imaging.bench

import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.image.Inpaint
import app.nanogone.imaging.image.Paste
import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps
import org.junit.jupiter.api.Test

class SpeedProbe {
    @Test
    fun timings() {
        val w = 1300; val h = 1600
        val img = Argb(w, h, IntArray(w * h) { 0xFF808080.toInt() + (it % 97) })
        val m = Mask(w, h).also { mm -> for (y in 400 until 1200) for (x in 350 until 950) mm[x, y] = true }
        fun t(name: String, f: () -> Unit) { val s = System.nanoTime(); f(); println("$name: ${(System.nanoTime() - s) / 1_000_000} ms") }
        t("grow r=16") { MaskOps.grow(m, 16) }
        t("distanceToOff") { MaskOps.distanceToOff(m) }
        val sw = 434; val sh = 534
        val small = Argb(sw, sh, IntArray(sw * sh) { 0xFF808080.toInt() })
        val sm = Mask(sw, sh).also { mm -> for (y in 133 until 400) for (x in 117 until 317) mm[x, y] = true }
        t("smoothFill 434x534") { Inpaint.smoothFill(small, sm) }
        t("smoothFill 434x534 again") { Inpaint.smoothFill(small, sm) }
        t("paste feathered") { Paste.feathered(img.copy(), img, 0, 0, m, 8f) }
    }
}
