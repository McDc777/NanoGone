package app.nanogone.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * One AI brain file from the app's assets. Uses the graphics chip first, then every main-chip
 * core (no speed caps). [backend] says which one is running.
 */
class TfliteModel(context: Context, asset: String) : Closeable {

    val interpreter: Interpreter
    val backend: String
    private var gpu: Delegate? = null

    init {
        val model = map(context, asset)
        var made: Interpreter? = null
        var used = "CPU x${Runtime.getRuntime().availableProcessors()}"
        try {
            val d = GpuDelegate()
            gpu = d
            made = Interpreter(model, Interpreter.Options().addDelegate(d))
            used = "GPU"
        } catch (t: Throwable) {
            Log.i("NanoGone", "$asset: GPU not available (${t.message}), using all CPU cores")
            runCatching { gpu?.close() }
            gpu = null
        }
        interpreter = made ?: Interpreter(
            model,
            Interpreter.Options().setNumThreads(Runtime.getRuntime().availableProcessors()).setUseXNNPACK(true),
        )
        backend = used
        Log.i("NanoGone", "$asset loaded on $backend")
    }

    fun inputIndex(name: String, fallback: Int): Int = runCatching { interpreter.getInputIndex(name) }.getOrDefault(fallback)

    fun outputIndex(name: String, fallback: Int): Int = runCatching { interpreter.getOutputIndex(name) }.getOrDefault(fallback)

    override fun close() {
        interpreter.close()
        runCatching { gpu?.close() }
    }

    companion object {
        fun exists(context: Context, asset: String): Boolean =
            runCatching { context.assets.openFd(asset).close(); true }.getOrDefault(false)

        private fun map(context: Context, asset: String): MappedByteBuffer {
            context.assets.openFd(asset).use { fd ->
                FileInputStream(fd.fileDescriptor).use { input ->
                    return input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }

        fun floatBuffer(count: Int): ByteBuffer = ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder())
    }
}
