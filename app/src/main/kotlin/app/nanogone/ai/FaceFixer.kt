package app.nanogone.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.nanogone.imaging.image.Argb
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Face fix: finds every face (MediaPipe face landmarker, also on overlapping tiles so small
 * faces in group photos are found), lines each one up to GFPGAN's 512 x 512 face template,
 * restores it with GFPGAN v1.4, and blends it back with a soft edge. Only face squares change.
 */
class FaceFixer(context: Context) : AutoCloseable {

    private val gfpgan: TfliteModel? =
        if (TfliteModel.exists(context, ASSET)) runCatching { TfliteModel(context, ASSET) }
            .onFailure { Log.w("NanoGone", "face fix brain failed to load", it) }.getOrNull() else null

    private val landmarker: FaceLandmarker? = runCatching {
        FaceLandmarker.createFromOptions(
            context,
            FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(FINDER).build())
                .setRunningMode(RunningMode.IMAGE)
                .setNumFaces(MAX_FACES)
                .setMinFaceDetectionConfidence(0.5f)
                .build(),
        )
    }.onFailure { Log.w("NanoGone", "face finder failed to load", it) }.getOrNull()

    val available: Boolean get() = gfpgan != null && landmarker != null
    val backend: String get() = gfpgan?.backend ?: "none"

    /** Five points per face, in image pixels: left eye, right eye, nose tip, left and right mouth corner. */
    fun findFaces(img: Argb): List<FloatArray> {
        val lm = landmarker ?: return emptyList()
        val found = ArrayList<FloatArray>()
        val side = max(img.width, img.height)
        // The whole photo, then a 3 x 3 grid of overlapping tiles for small faces.
        val views = ArrayList<IntArray>()
        views.add(intArrayOf(0, 0, img.width, img.height))
        if (side > VIEW * 1.5f) {
            val tw = img.width / 2
            val th = img.height / 2
            for (j in 0..2) for (i in 0..2) {
                val x0 = i * img.width / 4
                val y0 = j * img.height / 4
                views.add(intArrayOf(x0, y0, min(img.width, x0 + tw), min(img.height, y0 + th)))
            }
        }
        for (v in views) {
            val w = v[2] - v[0]
            val h = v[3] - v[1]
            val k = min(1f, VIEW.toFloat() / max(w, h))
            val bw = max(1, (w * k).toInt())
            val bh = max(1, (h * k).toInt())
            val px = IntArray(bw * bh)
            for (y in 0 until bh) for (x in 0 until bw) {
                px[y * bw + x] = img[min(img.width - 1, v[0] + ((x + 0.5f) / k).toInt()), min(img.height - 1, v[1] + ((y + 0.5f) / k).toInt())]
            }
            val bmp = Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888)
            val result = runCatching { lm.detect(BitmapImageBuilder(bmp).build()) }.getOrNull()
            bmp.recycle()
            for (face in result?.faceLandmarks().orEmpty()) {
                if (face.size < 478) continue
                fun pt(ids: IntRange): FloatArray {
                    var sx = 0f
                    var sy = 0f
                    for (i in ids) { sx += face[i].x(); sy += face[i].y() }
                    val n = ids.count()
                    return floatArrayOf(v[0] + sx / n * w, v[1] + sy / n * h)
                }
                val e1 = pt(468..472)
                val e2 = pt(473..477)
                val m1 = pt(61..61)
                val m2 = pt(291..291)
                val nose = pt(1..1)
                val (le, re) = if (e1[0] <= e2[0]) e1 to e2 else e2 to e1
                val (lm2, rm) = if (m1[0] <= m2[0]) m1 to m2 else m2 to m1
                val five = floatArrayOf(le[0], le[1], re[0], re[1], nose[0], nose[1], lm2[0], lm2[1], rm[0], rm[1])
                val eyes = hypot(re[0] - le[0], re[1] - le[1])
                if (eyes < MIN_EYE_GAP) continue
                // Same face seen in two views: keep the first.
                val dup = found.any { f ->
                    hypot((f[0] + f[2]) / 2 - (le[0] + re[0]) / 2, (f[1] + f[3]) / 2 - (le[1] + re[1]) / 2) < 0.5f * eyes
                }
                if (!dup) found.add(five)
            }
        }
        return found
    }

    /** Restore every face. [strength] 0..1 blends between the photo and the restored faces. */
    fun fix(img: Argb, strength: Float, progress: (Float) -> Unit = {}): Argb {
        val model = gfpgan ?: return img
        val faces = findFaces(img)
        Log.i("NanoGone", "face fix: ${faces.size} faces")
        if (faces.isEmpty()) return img
        val out = img.copy()
        val input = FloatArray(S * S * 3)
        faces.forEachIndexed { n, five ->
            val m = similarity(five) // photo -> template
            val inv = invert(m)
            for (y in 0 until S) for (x in 0 until S) {
                val sx = inv[0] * x + inv[1] * y + inv[2]
                val sy = inv[3] * x + inv[4] * y + inv[5]
                val p = Bilinear.sample(img, sx.coerceIn(0f, img.width - 1f), sy.coerceIn(0f, img.height - 1f))
                val i = (y * S + x) * 3
                input[i] = ((p shr 16) and 0xFF) / 255f
                input[i + 1] = ((p shr 8) and 0xFF) / 255f
                input[i + 2] = (p and 0xFF) / 255f
            }
            val o = model.run(input)[0]
            val restored = Argb(S, S)
            for (i in 0 until S * S) {
                val r = (o[i * 3] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val g = (o[i * 3 + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val b = (o[i * 3 + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                restored.px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            // Faces bigger than the template would come back softer than they are: blend less.
            val scale = hypot(m[0], m[3])
            val weight = strength * min(1f, scale / 0.77f)
            paste(out, restored, m, inv, weight)
            progress((n + 1f) / faces.size)
        }
        return out
    }

    private fun paste(out: Argb, face: Argb, m: FloatArray, inv: FloatArray, weight: Float) {
        // The template square's corners in the photo give the area to visit.
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for ((u, v) in listOf(0f to 0f, S.toFloat() to 0f, 0f to S.toFloat(), S.toFloat() to S.toFloat())) {
            val x = inv[0] * u + inv[1] * v + inv[2]
            val y = inv[3] * u + inv[4] * v + inv[5]
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        val left = max(0, x0.toInt()); val top = max(0, y0.toInt())
        val right = min(out.width, x1.toInt() + 2); val bottom = min(out.height, y1.toInt() + 2)
        for (y in top until bottom) for (x in left until right) {
            val u = m[0] * x + m[1] * y + m[2]
            val v = m[3] * x + m[4] * y + m[5]
            if (u < 0f || v < 0f || u > S - 1f || v > S - 1f) continue
            val edge = min(min(u, v), min(S - 1f - u, S - 1f - v))
            val a = weight * (edge / FEATHER).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val q = Bilinear.sample(face, u, v)
            val p = out[x, y]
            fun mix(s: Int): Int {
                val o = (p shr s) and 0xFF
                val n = (q shr s) and 0xFF
                return (o + (n - o) * a + 0.5f).toInt().coerceIn(0, 255)
            }
            out[x, y] = (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }

    /** Least-squares similarity (scale, turn, shift) taking the five points to the template. Row-major 2 x 3. */
    private fun similarity(five: FloatArray): FloatArray {
        // Unknowns a, b, tx, ty: u = a x - b y + tx, v = b x + a y + ty. Normal equations, 4 x 4.
        val ata = Array(4) { DoubleArray(4) }
        val atb = DoubleArray(4)
        for (k in 0 until 5) {
            val x = five[2 * k].toDouble(); val y = five[2 * k + 1].toDouble()
            val rows = arrayOf(doubleArrayOf(x, -y, 1.0, 0.0) to TEMPLATE[2 * k].toDouble(), doubleArrayOf(y, x, 0.0, 1.0) to TEMPLATE[2 * k + 1].toDouble())
            for ((r, t) in rows) {
                for (i in 0 until 4) { atb[i] += r[i] * t; for (j in 0 until 4) ata[i][j] += r[i] * r[j] }
            }
        }
        val s = solve4(ata, atb)
        return floatArrayOf(s[0].toFloat(), (-s[1]).toFloat(), s[2].toFloat(), s[1].toFloat(), s[0].toFloat(), s[3].toFloat())
    }

    private fun invert(m: FloatArray): FloatArray {
        val det = m[0] * m[4] - m[1] * m[3]
        val a = m[4] / det; val b = -m[1] / det; val c = -m[3] / det; val d = m[0] / det
        return floatArrayOf(a, b, -(a * m[2] + b * m[5]), c, d, -(c * m[2] + d * m[5]))
    }

    private fun solve4(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = 4
        val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (c in 0 until n) {
            val p = (c until n).maxBy { kotlin.math.abs(m[it][c]) }
            val t = m[c]; m[c] = m[p]; m[p] = t
            for (r in 0 until n) if (r != c) {
                val f = m[r][c] / m[c][c]
                for (j in c..n) m[r][j] -= f * m[c][j]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    override fun close() {
        gfpgan?.close()
        runCatching { landmarker?.close() }
    }

    companion object {
        const val ASSET = "models/gfpgan.tflite"
        const val FINDER = "models/face_landmarker.task"
        private const val S = 512
        private const val VIEW = 1280
        private const val MAX_FACES = 20
        private const val MIN_EYE_GAP = 10f
        private const val FEATHER = 40f

        /** GFPGAN's FFHQ face template at 512: eyes, nose, mouth corners (from facexlib). */
        private val TEMPLATE = floatArrayOf(
            192.98138f, 239.94708f, 318.90277f, 240.1936f, 256.63416f, 314.01935f,
            201.26117f, 371.41043f, 313.08905f, 371.15118f,
        )
    }
}
