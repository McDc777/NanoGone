package app.nanogone.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Something a helper found, in screen-copy pixels. */
data class Found(val box: RectF, val label: String, val score: Float)

/**
 * Find distractions: an object finder (EfficientDet-Lite2, 80 everyday kinds of things) plus a
 * simple rule for what is "in the way": everyone except the main people, and small clutter.
 */
class DistractionFinder(context: Context) : AutoCloseable {

    private val detector: ObjectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(30)
            .setScoreThreshold(0.3f)
            .build(),
    )

    fun find(bitmap: Bitmap): List<Found> {
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        val all = result.detections().map { d ->
            val c = d.categories().first()
            Found(d.boundingBox(), c.categoryName(), c.score())
        }
        Log.i("NanoGone", "objects: " + all.joinToString { "${it.label} ${"%.2f".format(it.score)}" })
        return pick(all, bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    override fun close() = detector.close()

    companion object {
        const val ASSET = "models/objects.tflite"
        private val CLUTTER = setOf(
            "bottle", "cup", "wine glass", "chair", "bench", "car", "truck", "bus", "bicycle", "motorcycle",
            "backpack", "handbag", "suitcase", "umbrella", "traffic light", "fire hydrant", "stop sign",
            "parking meter", "sports ball", "kite", "frisbee", "bird", "potted plant", "boat", "skateboard",
        )

        /** The rule, kept separate so it can be tested without the model. */
        fun pick(all: List<Found>, w: Float, h: Float): List<Found> {
            val area = w * h
            fun size(f: Found) = f.box.width() * f.box.height()
            fun central(f: Found): Float {
                val cx = f.box.centerX() / w - 0.5f
                val cy = f.box.centerY() / h - 0.5f
                return 1f - kotlin.math.sqrt(cx * cx + cy * cy)
            }
            val people = all.filter { it.label == "person" }
            val main = people.maxByOrNull { size(it) * central(it) }
            val out = ArrayList<Found>()
            if (main != null) {
                for (p in people) {
                    if (p === main) continue
                    val overlaps = RectF.intersects(p.box, main.box) && size(p) > 0.6f * size(main)
                    if (!overlaps && size(p) < 0.45f * size(main)) out.add(p)
                }
            }
            for (o in all) if (o.label in CLUTTER && size(o) < 0.25f * area) out.add(o)
            return out
        }
    }
}

/** Find text: writing, date stamps and watermarks (Google ML Kit, works offline). */
class TextFinder : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun find(bitmap: Bitmap): List<Found> {
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        return text.textBlocks.flatMap { b -> b.lines }.mapNotNull { l ->
            val r = l.boundingBox ?: return@mapNotNull null
            Found(RectF(r), l.text, 1f)
        }
    }

    override fun close() = recognizer.close()
}
