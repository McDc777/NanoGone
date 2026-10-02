package app.nanogone.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import java.io.Closeable
import java.io.File

/**
 * One AI brain file from the app's assets, run by LiteRT's CompiledModel at full power:
 * the AI chip (NPU) when the phone supports it, else the graphics chip, else every main-chip
 * core. [backend] says which one is running. Inputs and outputs are float arrays in the
 * model's own order.
 */
class TfliteModel private constructor(context: Context, asset: String?, file: File?, allowCpu: Boolean) : Closeable {

    constructor(context: Context, asset: String) : this(context, asset, null, true)

    private val model: CompiledModel
    val backend: String
    private val inputs: List<TensorBuffer>
    private val outputs: List<TensorBuffer>

    init {
        val name = asset?.substringAfterLast('/') ?: file!!.name
        // Files of the same name but different content (test packs) must not share compiled programs.
        val cacheKey = if (file != null) "$name-${file.length()}" else name
        val cores = Runtime.getRuntime().availableProcessors()
        fun create(o: CompiledModel.Options, env: Environment? = null): CompiledModel = when {
            asset != null && env != null -> CompiledModel.create(context.assets, asset, o, env)
            asset != null -> CompiledModel.create(context.assets, asset, o)
            env != null -> CompiledModel.create(file!!.absolutePath, o, env)
            else -> CompiledModel.create(file!!.absolutePath, o)
        }
        val cache = File(context.cacheDir, "gpu-programs").apply { mkdirs() }
        fun options(vararg a: Accelerator) = CompiledModel.Options(*a).apply {
            cpuOptions = CompiledModel.CpuOptions(cores, null, null)
            gpuOptions = CompiledModel.GpuOptions(
                precision = CompiledModel.GpuOptions.Precision.FP16_WITH_FP32_ACCUM,
                serializationDir = cache.absolutePath,
                modelCacheKey = cacheKey,
                serializeProgramCache = true,
            )
        }
        var made: CompiledModel? = null
        var used = "CPU x$cores"
        val env = npuEnvironment
        if (env != null) {
            try {
                made = create(if (allowCpu) options(Accelerator.NPU, Accelerator.GPU, Accelerator.CPU) else options(Accelerator.NPU, Accelerator.GPU), env)
                used = "NPU"
            } catch (t: Throwable) {
                Log.i("NanoGone", "$name: AI chip not usable (${t.message})")
            }
        }
        if (made == null) {
            try {
                made = create(if (allowCpu) options(Accelerator.GPU, Accelerator.CPU) else options(Accelerator.GPU))
                used = "GPU"
            } catch (t: Throwable) {
                Log.i("NanoGone", "$name: graphics chip not usable (${t.message})" + if (allowCpu) ", using all $cores main-chip cores" else "")
            }
        }
        model = made ?: if (allowCpu) create(options(Accelerator.CPU)) else throw IllegalStateException("$name needs the AI chip or graphics chip")
        backend = used
        inputs = model.createInputBuffers()
        outputs = model.createOutputBuffers()
        Log.i("NanoGone", "$name loaded on $backend")
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
        @Volatile private var npuEnvironment: Environment? = null

        /**
         * Get the AI chip ready on supported phones (Snapdragon 8 Gen 2 and newer, Dimensity
         * 7300 and others). The chip's runtime library is fetched once if missing; after that
         * everything is offline. Safe to call on any phone: unsupported phones just skip it.
         */
        suspend fun prepareNpu(context: Context) {
            try {
                val provider = BuiltinNpuAcceleratorProvider(context)
                if (!provider.isDeviceSupported()) {
                    Log.i("NanoGone", "AI chip: this phone is not on LiteRT's list")
                    return
                }
                if (!provider.isLibraryReady()) provider.downloadLibrary()
                if (provider.isLibraryReady()) {
                    npuEnvironment = Environment.create(context, provider)
                    Log.i("NanoGone", "AI chip: ready")
                }
            } catch (t: Throwable) {
                Log.i("NanoGone", "AI chip: not available (${t.message})")
            }
        }

        /**
         * A brain file outside the APK (the brain pack). With [allowCpu] false it refuses to run
         * on the main chip, for brains too big for that.
         */
        fun fromFile(context: Context, file: File, allowCpu: Boolean = false): TfliteModel = TfliteModel(context, null, file, allowCpu)

        fun exists(context: Context, asset: String): Boolean =
            runCatching { context.assets.open(asset).close(); true }.getOrDefault(false)
    }
}
