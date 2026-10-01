package app.nanogone.ai

import android.content.Context
import android.util.Log
import app.nanogone.editor.MaskShape
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.mask.Mask
import app.nanogone.imaging.mask.MaskOps

/**
 * Magic tap: MobileSAM (Qualcomm build). The encoder looks at the area you are viewing at the
 * best detail it can (zoom in and tiny things become big for it); the decoder turns one tap
 * into the outline of the thing under the finger. The answer is cut down to the one connected
 * piece under the tap, so it can never jump to a nearby object.
 */
class MagicTap(context: Context) : AutoCloseable {

    private val encoder = TfliteModel(context, ENCODER)
    private val decoder = TfliteModel(context, DECODER)
    val backend: String get() = encoder.backend

    private val image = TfliteModel.floatBuffer(E * E * 3)
    private val embeddings = TfliteModel.floatBuffer(64 * 64 * 256)
    private val coords = TfliteModel.floatBuffer(2)
    private val labels = TfliteModel.floatBuffer(1)
    private val masks = TfliteModel.floatBuffer(256 * 256)
    private val scores = TfliteModel.floatBuffer(1)

    private var cachedView: IntRect? = null
    private var cachedVersion = -1
    private var viewScale = 1f

    /**
     * @param view the part of the photo the person is looking at (image pixels).
     * @param pixels that area at any scale (a screen copy is fine), same aspect as [view].
     * @param version changes whenever the photo changes, so a stale view is re-encoded.
     * @return the object under ([tapX], [tapY]) (image pixels) as a [MaskShape], or null.
     */
    @Synchronized
    fun select(view: IntRect, pixels: Argb, version: Int, tapX: Float, tapY: Float): MaskShape? {
        if (view != cachedView || version != cachedVersion) {
            encode(view, pixels)
            cachedView = view
            cachedVersion = version
        }
        coords.rewind(); labels.rewind(); masks.rewind(); scores.rewind(); embeddings.rewind()
        coords.putFloat((tapX - view.left) * viewScale)
        coords.putFloat((tapY - view.top) * viewScale)
        labels.putFloat(1f)
        coords.rewind(); labels.rewind()
        val inputs = arrayOfNulls<Any>(3)
        inputs[decoder.inputIndex("image_embeddings", 0)] = embeddings
        inputs[decoder.inputIndex("point_coords", 1)] = coords
        inputs[decoder.inputIndex("point_labels", 2)] = labels
        val outMasks = decoder.outputIndex("masks", 0)
        val outScores = decoder.outputIndex("scores", 1)
        decoder.interpreter.runForMultipleInputsOutputs(inputs, mapOf(outMasks to masks, outScores to scores))
        masks.rewind(); scores.rewind()
        val logits = FloatArray(256 * 256) { masks.getFloat() }
        Log.i("NanoGone", "magic tap score ${scores.getFloat(0)}")
        // Build the outline at up to 2048 px on the long side; it is stretched over the view later.
        val q = minOf(1f, 2048f / maxOf(view.width, view.height))
        val mw = maxOf(1, (view.width * q).toInt())
        val mh = maxOf(1, (view.height * q).toInt())
        val m = Mask(mw, mh)
        val k = viewScale * 256f / E / q
        for (y in 0 until mh) for (x in 0 until mw) {
            val lx = ((x + 0.5f) * k - 0.5f).coerceIn(0f, 255f)
            val ly = ((y + 0.5f) * k - 0.5f).coerceIn(0f, 255f)
            m[x, y] = bilinear(logits, lx, ly) > 0f
        }
        val tx = ((tapX - view.left) * q).toInt().coerceIn(0, mw - 1)
        val ty = ((tapY - view.top) * q).toInt().coerceIn(0, mh - 1)
        val piece = MaskOps.componentAt(m, tx, ty)
        val b = piece.bounds() ?: return null
        // Keep only the piece's own box: small to store, exact to use.
        val sub = Mask(b.width, b.height)
        for (y in 0 until b.height) for (x in 0 until b.width) sub[x, y] = piece[b.left + x, b.top + y]
        val rect = IntRect(
            view.left + (b.left / q).toInt(), view.top + (b.top / q).toInt(),
            view.left + kotlin.math.ceil(b.right / q).toInt(), view.top + kotlin.math.ceil(b.bottom / q).toInt(),
        ).intersect(view)
        return MaskShape(rect, sub)
    }

    private fun encode(view: IntRect, pixels: Argb) {
        viewScale = E.toFloat() / maxOf(view.width, view.height)
        val sw = (view.width * viewScale).toInt().coerceIn(1, E)
        val sh = (view.height * viewScale).toInt().coerceIn(1, E)
        val px = pixels.width.toFloat() / view.width
        image.rewind()
        for (y in 0 until E) for (x in 0 until E) {
            if (x < sw && y < sh) {
                val u = ((x + 0.5f) / viewScale) * px - 0.5f
                val v = ((y + 0.5f) / viewScale) * px - 0.5f
                val p = Bilinear.sample(pixels, u.coerceIn(0f, pixels.width - 1f), v.coerceIn(0f, pixels.height - 1f))
                image.putFloat(((p shr 16) and 0xFF) / 255f)
                image.putFloat(((p shr 8) and 0xFF) / 255f)
                image.putFloat((p and 0xFF) / 255f)
            } else {
                image.putFloat(0f); image.putFloat(0f); image.putFloat(0f)
            }
        }
        image.rewind(); embeddings.rewind()
        encoder.interpreter.run(image, embeddings)
    }

    private fun bilinear(a: FloatArray, fx: Float, fy: Float): Float {
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, 255); val y1 = minOf(y0 + 1, 255)
        val ax = fx - x0; val ay = fy - y0
        val top = a[y0 * 256 + x0] * (1 - ax) + a[y0 * 256 + x1] * ax
        val bottom = a[y1 * 256 + x0] * (1 - ax) + a[y1 * 256 + x1] * ax
        return top * (1 - ay) + bottom * ay
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }

    companion object {
        const val ENCODER = "models/sam_encoder.tflite"
        const val DECODER = "models/sam_decoder.tflite"
        private const val E = 1024
    }
}
