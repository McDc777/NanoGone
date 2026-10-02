package app.nanogone.ai

import android.content.Context
import android.util.Log
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The deep brain: OSOR (one-step, effect-aware remover on SDXL-Inpainting). It also finds and
 * removes the object's shadow and reflection: its alpha output marks where the photo should
 * change, which can reach past the selection. Runs on the AI chip or graphics chip only; each
 * part of the brain is loaded, run and let go in turn, so it fits in a phone's memory.
 */
class DeepEngine(private val context: Context, private val dir: File, private val m: BrainPack.Manifest, private val allowCpu: Boolean = false) {

    /** Makes a small image 2x or 4x bigger with real detail (shared with the fast brain). */
    @Volatile var detailer: ((Argb, Int) -> Argb)? = null

    class Result(val filled: Argb, val changed: Mask)

    /**
     * Repairs [mask] in [crop]. The returned mask is the selection plus whatever the brain judged
     * to be the object's effects (shadow, reflection) within reach of it.
     */
    fun repair(crop: Argb, mask: Mask, seed: Int = 7): Result {
        val s = m.size
        val scale = s.toFloat() / maxOf(crop.width, crop.height)
        val sw = minOf(s, Math.round(crop.width * scale).coerceAtLeast(1))
        val sh = minOf(s, Math.round(crop.height * scale).coerceAtLeast(1))
        val img = FloatArray(s * s * 3)
        val hole = FloatArray(s * s)
        for (y in 0 until s) for (x in 0 until s) {
            val cx = minOf(x, sw - 1)
            val cy = minOf(y, sh - 1)
            val fx = ((cx + 0.5f) / scale - 0.5f).coerceIn(0f, crop.width - 1f)
            val fy = ((cy + 0.5f) / scale - 0.5f).coerceIn(0f, crop.height - 1f)
            val p = Bilinear.sample(crop, fx, fy)
            val i = y * s + x
            img[i * 3] = ((p shr 16) and 0xFF) / 255f
            img[i * 3 + 1] = ((p shr 8) and 0xFF) / 255f
            img[i * 3 + 2] = (p and 0xFF) / 255f
            if (x < sw && y < sh && mask[fx.toInt(), fy.toInt()]) hole[i] = 1f
        }
        val rnd = Random(seed)
        val noise = FloatArray(m.latent * m.latent * 4) { gaussian(rnd) }
        val (painted, alpha) = run(img, hole, noise)

        // Where to change: the selection, plus strong alpha that stays near the object.
        val out = crop.copy()
        val changed = mask.copy()
        val reach = 1.5f * sqrt(mask.count().toFloat()) + 8f
        val dist = MaskOps.distanceFrom(mask)
        val l = m.latent
        for (y in 0 until crop.height) for (x in 0 until crop.width) {
            if (changed[x, y] || dist[y * crop.width + x] > reach) continue
            val ax = ((x + 0.5f) * scale / (s / l) - 0.5f).coerceIn(0f, l - 1f)
            val ay = ((y + 0.5f) * scale / (s / l) - 0.5f).coerceIn(0f, l - 1f)
            if (sampleGray(alpha, l, ax, ay) > 0.5f) changed[x, y] = true
        }
        val pic = Argb(s, s)
        for (i in 0 until s * s) {
            val r = (painted[i * 3] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = (painted[i * 3 + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (painted[i * 3 + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)
            pic.px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        // Big crops: give the 512 result real detail before stretching it back (like the fast brain).
        val d = detailer
        var src = pic
        var k = 1
        var area = IntRect(0, 0, sw, sh)
        if (scale < 0.67f && d != null) {
            val b = changed.bounds()
            if (b != null) {
                area = IntRect((b.left * scale).toInt() - 6, (b.top * scale).toInt() - 6, (b.right * scale).toInt() + 7, (b.bottom * scale).toInt() + 7)
                    .intersect(IntRect(0, 0, sw, sh))
                k = if (scale < 0.4f) 4 else 2
                src = runCatching { d(pic.crop(area), k) }.getOrElse { k = 1; area = IntRect(0, 0, sw, sh); pic }
            }
        }
        for (y in 0 until crop.height) for (x in 0 until crop.width) {
            if (!changed[x, y]) continue
            val mx = ((x + 0.5f) * scale - 0.5f).coerceIn(0f, sw - 1f)
            val my = ((y + 0.5f) * scale - 0.5f).coerceIn(0f, sh - 1f)
            out[x, y] = Bilinear.sample(src, (mx - area.left + 0.5f) * k - 0.5f, (my - area.top + 0.5f) * k - 0.5f)
        }
        return Result(out, changed)
    }

    /**
     * The raw brain: picture (size x size x 3, 0..1), hole (size x size, 1 = remove) and noise
     * (latent x latent x 4) in; repaired picture and alpha (latent x latent) out.
     */
    fun run(img: FloatArray, hole: FloatArray, noise: FloatArray): Pair<FloatArray, FloatArray> {
        val clock = System.currentTimeMillis()
        val s = m.size
        val l = m.latent
        val f = s / l
        val zLq = load(ENC).use { it.run(img)[0] }
        val maskLat = FloatArray(l * l)
        for (y in 0 until s) for (x in 0 until s) if (hole[y * s + x] > 0.5f) maskLat[(y / f) * l + x / f] = 1f
        var h: FloatArray? = null
        var z: FloatArray? = null
        val stack = ArrayList<FloatArray>()
        var result: List<FloatArray>? = null
        for (p in m.parts) {
            val args = ArrayList<FloatArray>()
            if (p.first) {
                args += zLq; args += maskLat; args += noise
            } else {
                args += h!!
                args.addAll(stack.subList(stack.size - p.pops, stack.size))
                if (p.last) { args += z!!; args += zLq }
            }
            val outs = load(p.file).use { it.run(*args.toTypedArray()) }.toMutableList()
            if (p.last) { result = outs; break }
            if (p.first) z = outs.removeAt(outs.size - 1)
            repeat(p.pops) { stack.removeAt(stack.size - 1) }
            val new = if (p.h_is_skip) outs else outs.subList(1, outs.size)
            h = if (p.h_is_skip) new.last() else outs[0]
            stack.addAll(new)
        }
        val (zOut, alpha) = requireNotNull(result) { "the brain pack has no last part" }.let { it[0] to it[1] }
        val pic = load(DEC).use { it.run(zOut)[0] }
        Log.i("NanoGone", "deep brain: ${m.parts.size} parts in ${System.currentTimeMillis() - clock} ms")
        return pic to alpha
    }

    private fun load(name: String): TfliteModel = TfliteModel.fromFile(context, File(dir, name), allowCpu)

    private fun sampleGray(a: FloatArray, n: Int, fx: Float, fy: Float): Float {
        val x0 = fx.toInt().coerceIn(0, n - 1); val y0 = fy.toInt().coerceIn(0, n - 1)
        val x1 = (x0 + 1).coerceAtMost(n - 1); val y1 = (y0 + 1).coerceAtMost(n - 1)
        val ax = fx - x0; val ay = fy - y0
        val top = a[y0 * n + x0] * (1 - ax) + a[y0 * n + x1] * ax
        val bot = a[y1 * n + x0] * (1 - ax) + a[y1 * n + x1] * ax
        return top * (1 - ay) + bot * ay
    }

    private fun gaussian(r: Random): Float {
        // Box-Muller
        val u = r.nextDouble().coerceAtLeast(1e-12)
        val v = r.nextDouble()
        return (sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)).toFloat()
    }

    /**
     * Compares this phone's run of the test pack with the PC's answer (tools/make_deep_selftest.py).
     * Returns the biggest difference, or null when there is no test pack.
     */
    fun selfTest(): Float? {
        val inF = File(dir, "selftest_in.bin")
        val outF = File(dir, "selftest_out.bin")
        if (!inF.exists() || !outF.exists()) return null
        val s = m.size
        val l = m.latent
        val a = floats(inF)
        val img = a.copyOfRange(0, s * s * 3)
        val hole = a.copyOfRange(s * s * 3, s * s * 4)
        val noise = a.copyOfRange(s * s * 4, s * s * 4 + l * l * 4)
        val want = floats(outF)
        val (pic, alpha) = run(img, hole, noise)
        var worst = 0f
        for (i in pic.indices) worst = maxOf(worst, kotlin.math.abs(pic[i] - want[i]))
        for (i in alpha.indices) worst = maxOf(worst, kotlin.math.abs(alpha[i] - want[pic.size + i]))
        return worst
    }

    private fun floats(f: File): FloatArray {
        val bb = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(bb.remaining()).also { bb.get(it) }
    }

    companion object {
        const val ENC = "osor_vae_enc.tflite"
        const val DEC = "osor_vae_dec.tflite"
    }
}
