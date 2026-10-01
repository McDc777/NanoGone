package app.nanogone.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.Closeable

/**
 * One AI brain file from the app's assets, run by LiteRT's CompiledModel. Tries the graphics
 * chip first (with the main chip for any step the graphics chip cannot do), then the main
 * chip alone. [backend] says which one is running. Inputs and outputs are float arrays in the
 * model's own order.
 */
class TfliteModel(context: Context, asset: String) : Closeable {

    private val model: CompiledModel
    val backend: String
    private val inputs: List<TensorBuffer>
    private val outputs: List<TensorBuffer>

    init {
        var made: CompiledModel? = null
        var used = "CPU"
        try {
            made = CompiledModel.create(context.assets, asset, CompiledModel.Options(Accelerator.GPU, Accelerator.CPU))
            used = "GPU"
        } catch (t: Throwable) {
            Log.i("NanoGone", "$asset: graphics chip not available (${t.message}), using the main chip")
        }
        model = made ?: CompiledModel.create(context.assets, asset, CompiledModel.Options(Accelerator.CPU))
        backend = used
        inputs = model.createInputBuffers()
        outputs = model.createOutputBuffers()
        Log.i("NanoGone", "$asset loaded on $backend")
    }

    /** Run once. Each input array goes to the matching model input; returns every output. */
    @Synchronized
    fun run(vararg ins: FloatArray): List<FloatArray> {
        require(ins.size == inputs.size) { "model wants ${inputs.size} inputs, got ${ins.size}" }
        ins.forEachIndexed { i, a -> inputs[i].writeFloat(a) }
        model.run(inputs, outputs)
        return outputs.map { it.readFloat() }
    }

    override fun close() {
        inputs.forEach { runCatching { it.close() } }
        outputs.forEach { runCatching { it.close() } }
        runCatching { model.close() }
    }

    companion object {
        fun exists(context: Context, asset: String): Boolean =
            runCatching { context.assets.open(asset).close(); true }.getOrDefault(false)
    }
}
