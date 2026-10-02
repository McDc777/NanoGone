package app.nanogone.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nanogone.ai.BrainPack
import app.nanogone.ai.DeepEngine
import app.nanogone.ai.DistractionFinder
import app.nanogone.ai.EnhanceOptions
import app.nanogone.ai.Enhancer
import app.nanogone.ai.LamaEngine
import app.nanogone.ai.MagicTap
import app.nanogone.ai.TextFinder
import app.nanogone.ai.TfliteModel
import app.nanogone.imaging.geom.CropPlanner
import app.nanogone.imaging.image.Argb
import app.nanogone.imaging.image.Grain
import app.nanogone.imaging.image.ShadowFinder
import app.nanogone.imaging.geom.IntRect
import app.nanogone.imaging.image.Paste
import app.nanogone.imaging.mask.MaskOps
import app.nanogone.save.SaveFormat
import app.nanogone.save.Saver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tool { Tap, Brush, Loop, Spot, Eraser }

enum class FindWhat { Distractions, Text }

/** Logs how long each step takes (tag NanoGone), so speed can be checked on real phones. */
class StageClock(private val job: String) {
    private var t = android.os.SystemClock.elapsedRealtime()
    fun lap(stage: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        android.util.Log.i("NanoGone", "$job: $stage took ${now - t} ms")
        t = now
    }
}

data class EditorUi(
    val photo: Photo? = null,
    /** Screen copy in stored orientation, with removals applied. Bumped [version] on every change. */
    val display: Bitmap? = null,
    val original: Bitmap? = null,
    val version: Int = 0,
    val selection: List<Shape> = emptyList(),
    val tool: Tool = Tool.Tap,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: String? = null,
    /** Bumps each time a removal finishes, so the mist can lift. */
    val removals: Int = 0,
    val message: String? = null,
    val saved: Saver.Result? = null,
    val lastFormat: SaveFormat = SaveFormat.JPEG,
    /** Which brains are running and on which chip (shown on the welcome screen). */
    val brains: String = "waking the brains",
    /** Enhance has been applied to the whole photo (Save writes the enhanced picture). */
    val enhanced: Boolean = false,
    val enhancedSize: String? = null,
    val canUpscale: Boolean = false,
    val canFixFaces: Boolean = false,
    /** Shadow catcher: also remove the shadow attached to what you picked. */
    val shadowCatcher: Boolean = true,
    val lastShadowCaught: Boolean = false,
    /** True right after a removal, until the next action. */
    val justRemoved: Boolean = false,
    /** How many things the last Find added (0 hides the hint). */
    val foundCount: Int = 0,
    /** Deep brain pack, in plain words for the welcome screen; null hides it (phone too small). */
    val deepStatus: String? = null,
    /** True when the "add the deep brain" button should show. */
    val deepCanAdd: Boolean = false,
    /** The deep brain is improving the last removal right now. */
    val deepWorking: Boolean = false,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = PhotoReader(app.contentResolver)
    private val saver = Saver(app.contentResolver, reader, app.cacheDir)
    private val prefs = app.getSharedPreferences("nanogone", Context.MODE_PRIVATE)
    private val fallback: RepairEngine = SmoothFillEngine()
    @Volatile private var lama: RepairEngine? = null
    @Volatile private var tapper: MagicTap? = null
    @Volatile private var enhancer: Enhancer? = null
    private var enhanced: Argb? = null
    @Volatile private var shadowCaught = false
    @Volatile private var caughtForDeep = false
    @Volatile private var deepJob: Triple<Argb, app.nanogone.imaging.mask.Mask, Int>? = null
    private val engine: RepairEngine get() = lama ?: fallback
    private val pack = BrainPack(app)
    @Volatile private var deep: DeepEngine? = null

    private fun describeBrains(): String = buildString {
        append(lama?.name ?: "smooth fill (no AI brain)")
        tapper?.let { append(" · magic tap on ${it.backend}") }
        enhancer?.takeIf { it.canFixFaces }?.let { append(" · face fix on ${it.faceBackend}") }
    }

    /** Enhance the whole photo (after removals). Save then writes the enhanced picture. */
    fun enhance(o: EnhanceOptions) {
        val photo = _ui.value.photo ?: return
        val d = doc ?: return
        val e = enhancer
        val outPixels = photo.width.toLong() * photo.height * o.bigger * o.bigger
        if (outPixels > Enhancer.MAX_OUTPUT_PIXELS) {
            _ui.update { it.copy(message = "That would be ${outPixels / 1_000_000} MP. Bigger works up to ${Enhancer.MAX_OUTPUT_PIXELS / 1_000_000} MP for now. Try 2x or same size.") }
            return
        }
        if (e == null) {
            _ui.update { it.copy(message = "Enhance is still waking up. Try again in a moment.") }
            return
        }
        _ui.update { it.copy(busy = "Enhancing") }
        viewModelScope.launch {
            try {
                val (result, shown) = withContext(Dispatchers.Default) {
                    val clock = StageClock("enhance")
                    val full = saver.composite(photo, d.image, d.state.patches)
                    val screen = _ui.value.display ?: error("no screen copy")
                    val spx = IntArray(screen.width * screen.height)
                    screen.getPixels(spx, 0, screen.width, 0, 0, screen.width, screen.height)
                    val preview = Argb(screen.width, screen.height, spx)
                    clock.lap("read photo")
                    var last = -1
                    val out = e.enhance(full, preview, o) { f ->
                        val pct = (f * 100).toInt()
                        if (pct != last) { last = pct; _ui.update { it.copy(busy = "Enhancing $pct%") } }
                    }
                    clock.lap("enhance ${out.width}x${out.height} on ${e.backend}")
                    val bmp = Bitmap.createBitmap(out.px, out.width, out.height, Bitmap.Config.ARGB_8888)
                    val k = minOf(1f, 2560f / maxOf(out.width, out.height))
                    val small = if (k < 1f) Bitmap.createScaledBitmap(bmp, (out.width * k).toInt(), (out.height * k).toInt(), true).also { bmp.recycle() } else bmp
                    out to small.copy(Bitmap.Config.ARGB_8888, true)
                }
                enhanced = result
                _ui.update {
                    it.copy(busy = null, display = shown, version = it.version + 1, enhanced = true,
                        enhancedSize = "${result.width} x ${result.height}")
                }
            } catch (oom: OutOfMemoryError) {
                _ui.update { it.copy(busy = null, message = "Not enough memory for that size on this phone. Try a smaller Bigger.") }
            } catch (ex: Exception) {
                _ui.update { it.copy(busy = null, message = ex.message ?: "Enhance did not work. Try again.") }
            }
        }
    }

    /** Drop the enhanced version and go back to the edited photo. */
    fun clearEnhance() {
        if (enhanced == null) return
        enhanced = null
        _ui.update { it.copy(enhanced = false, enhancedSize = null) }
        refresh()
    }

    @Volatile private var distractions: DistractionFinder? = null
    @Volatile private var texts: TextFinder? = null

    /** Find distractions (people in the background, clutter) or text, and add them to the selection. */
    fun find(what: FindWhat) {
        val photo = _ui.value.photo ?: return
        val d = doc ?: return
        val screen = _ui.value.display ?: return
        _ui.update { it.copy(busy = if (what == FindWhat.Distractions) "Looking for distractions" else "Looking for text", justRemoved = false) }
        viewModelScope.launch {
            try {
                val shapes = withContext(Dispatchers.Default) {
                    val clock = StageClock("find $what")
                    val c = getApplication<Application>()
                    val upright = rotate(screen, photo.rotation)
                    val found = when (what) {
                        FindWhat.Distractions -> (distractions ?: DistractionFinder(c).also { distractions = it }).find(upright)
                        FindWhat.Text -> (texts ?: TextFinder().also { texts = it }).find(upright)
                    }
                    clock.lap("found ${found.size}")
                    val sx = photo.width.toFloat() / screen.width
                    val px = IntArray(screen.width * screen.height)
                    screen.getPixels(px, 0, screen.width, 0, 0, screen.width, screen.height)
                    val whole = Argb(screen.width, screen.height, px)
                    found.map { f ->
                        // Box corners back to stored screen-copy pixels, then to photo pixels.
                        val pts = listOf(f.box.left to f.box.top, f.box.right to f.box.top, f.box.right to f.box.bottom, f.box.left to f.box.bottom)
                            .map { (u, v) -> unrotate(u, v, photo.rotation, screen.width.toFloat(), screen.height.toFloat()) }
                        val xs = pts.map { it.first * sx }
                        val ys = pts.map { it.second * sx }
                        val box = IntRect(xs.min().toInt(), ys.min().toInt(), xs.max().toInt() + 1, ys.max().toInt() + 1)
                        val pad = if (what == FindWhat.Text) maxOf(4, (minOf(box.width, box.height) * 0.25f).toInt()) else 0
                        val padded = IntRect(box.left - pad, box.top - pad, box.right + pad, box.bottom + pad).intersect(d.image)
                        val outline = if (what == FindWhat.Distractions) {
                            tapper?.select(d.image, whole, _ui.value.version, (box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
                        } else null
                        // Trust the outline only if it stays near the box the finder saw.
                        val near = outline != null && outline.rect.width <= box.width * 1.6f && outline.rect.height <= box.height * 1.6f
                        if (near) outline!! else Loop(
                            floatArrayOf(padded.left.toFloat(), padded.right.toFloat(), padded.right.toFloat(), padded.left.toFloat()),
                            floatArrayOf(padded.top.toFloat(), padded.top.toFloat(), padded.bottom.toFloat(), padded.bottom.toFloat()),
                        )
                    }.also { clock.lap("outlines") }
                }
                d.addShapes(shapes)
                val msg = when {
                    shapes.isEmpty() && what == FindWhat.Distractions -> "No distractions found. Tap or brush anything you want gone."
                    shapes.isEmpty() -> "No text found."
                    else -> null
                }
                _ui.update { it.copy(busy = null, message = msg, foundCount = shapes.size) }
                publishSelection()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, message = e.message ?: "Finding did not work. Try again.") }
            }
        }
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        if (deg % 360 == 0) return b
        val m = android.graphics.Matrix().apply { postRotate(deg.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    /** Point in the upright picture back to the stored (unrotated) picture of size [w] x [h]. */
    private fun unrotate(u: Float, v: Float, rot: Int, w: Float, h: Float): Pair<Float, Float> = when (rot) {
        90 -> v to (h - u)
        180 -> (w - u) to (h - v)
        270 -> (w - v) to u
        else -> u to v
    }

    /** Magic tap at image point ([x], [y]) while the person views [view] (image pixels). */
    fun magicTap(view: IntRect, x: Float, y: Float) {
        val t = tapper
        val photo = _ui.value.photo ?: return
        val d = doc ?: return
        // Tapping a piece that is already picked unpicks it (untick a found thing).
        if (d.unpickAt(x, y)) {
            clearEnhance()
            _ui.update { it.copy(justRemoved = false, foundCount = 0) }
            publishSelection()
            return
        }
        if (t == null) {
            _ui.update { it.copy(message = "Magic tap is still waking up. Try again in a moment, or use the brush.") }
            return
        }
        _ui.update { it.copy(busy = "Finding its edges") }
        viewModelScope.launch {
            try {
                val shape = withContext(Dispatchers.Default) {
                    val clock = StageClock("magic tap")
                    val full = view.width >= photo.width * 0.8f && view.height >= photo.height * 0.8f
                    val pixels = if (full) {
                        val bmp = _ui.value.display ?: error("no screen copy")
                        val px = IntArray(bmp.width * bmp.height)
                        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                        val whole = Argb(bmp.width, bmp.height, px)
                        val sx = bmp.width.toFloat() / photo.width
                        val r = IntRect((view.left * sx).toInt(), (view.top * sx).toInt(), minOf(bmp.width, (view.right * sx).toInt()), minOf(bmp.height, (view.bottom * sx).toInt()))
                        whole.crop(r)
                    } else {
                        var sample = 1
                        while (maxOf(view.width, view.height) / (sample * 2) >= 1024) sample *= 2
                        reader.region(photo, view, sample)
                    }
                    clock.lap("view pixels ${pixels.width}x${pixels.height}")
                    t.select(view, pixels, _ui.value.version, x, y).also { clock.lap("outline") }
                }
                if (shape == null) {
                    _ui.update { it.copy(busy = null, message = "Nothing clear to pick there. Try the brush or a loop.") }
                } else {
                    d.addShape(shape)
                    _ui.update { it.copy(busy = null) }
                    publishSelection()
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, message = e.message ?: "Magic tap did not work. Try the brush.") }
            }
        }
    }

    private var doc: EditDocument? = null
    private var base: Bitmap? = null

    private val _ui = MutableStateFlow(
        EditorUi(lastFormat = runCatching { SaveFormat.valueOf(prefs.getString("format", "JPEG")!!) }.getOrDefault(SaveFormat.JPEG)),
    )
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

    init {
        // Wake the brains as soon as the app opens, so the first removal is quick.
        viewModelScope.launch(Dispatchers.Default) {
            val c = getApplication<Application>()
            c.getExternalFilesDir("brains-selftest") // made by the app, so a test pack copied in stays readable
            TfliteModel.prepareNpu(c)
            runCatching {
                if (TfliteModel.exists(c, LamaEngine.ASSET)) lama = LamaEngine(c)
            }.onFailure { android.util.Log.w("NanoGone", "fast brain failed to load", it) }
            runCatching {
                if (TfliteModel.exists(c, MagicTap.ENCODER)) tapper = MagicTap(c)
            }.onFailure { android.util.Log.w("NanoGone", "magic tap failed to load", it) }
            runCatching { enhancer = Enhancer(c) }
                .onFailure { android.util.Log.w("NanoGone", "enhance failed to load", it) }
            (lama as? LamaEngine)?.let { l ->
                enhancer?.takeIf { it.canUpscale }?.let { e -> l.detailer = { img, k -> e.detail(img, k) ?: error("no detail brain") } }
            }
            _ui.update { it.copy(brains = describeBrains(), canUpscale = enhancer?.canUpscale == true, canFixFaces = enhancer?.canFixFaces == true) }
            deepSelfTest(c)
            wakeDeep()
        }
    }

    /** A test pack (pushed by the emulator test) checks the deep brain's wiring against the PC's answer. */
    private fun deepSelfTest(c: Application) {
        // The app makes this (empty) folder itself, so a test pack copied in later stays readable.
        val dir = c.getExternalFilesDir("brains-selftest") ?: return
        if (dir.list().isNullOrEmpty()) return
        val m = BrainPack(c, dir).manifest()
        if (m == null) {
            android.util.Log.w("NanoGone", "deep selftest: no readable manifest in $dir (files: ${dir.list()?.joinToString()})")
            return
        }
        runCatching { DeepEngine(c, dir, m, allowCpu = true).selfTest() }
            .onSuccess { android.util.Log.i("NanoGone", "deep selftest: max diff $it") }
            .onFailure { android.util.Log.w("NanoGone", "deep selftest: failed", it) }
    }

    /** Load the deep brain if its pack is here and the phone has room for it; show its state. */
    private fun wakeDeep() {
        if (!pack.deviceCanRun()) {
            _ui.update { it.copy(deepStatus = null, deepCanAdd = false) }
            return
        }
        when (val s = pack.state()) {
            is BrainPack.State.Ready -> {
                deep = DeepEngine(getApplication(), pack.dir, s.manifest).also { d ->
                    enhancer?.takeIf { it.canUpscale }?.let { e -> d.detailer = { img, k -> e.detail(img, k) ?: error("no detail brain") } }
                }
                _ui.update { it.copy(deepStatus = "Deep brain ready: big removals get a second, deeper pass, shadows and reflections too.", deepCanAdd = false) }
            }
            else -> _ui.update { it.copy(deepStatus = "Deep brain not added yet. It makes big removals look real, shadows and reflections included.", deepCanAdd = true) }
        }
    }

    /** Fetch the deep brain pack (Wi-Fi only, about 5 GB, once). */
    fun addDeepBrain() {
        if (!_ui.value.deepCanAdd) return
        _ui.update { it.copy(deepCanAdd = false, deepStatus = "Getting the deep brain ready to download") }
        viewModelScope.launch(Dispatchers.IO) {
            val end = pack.download { s ->
                val text = when (s) {
                    is BrainPack.State.Downloading -> "Adding the deep brain: %.1f of %.1f GB (Wi-Fi only, you can keep using the app)".format(s.done / 1e9, s.total / 1e9)
                    BrainPack.State.Checking -> "Checking the deep brain files"
                    else -> null
                }
                if (text != null) _ui.update { it.copy(deepStatus = text) }
            }
            if (end is BrainPack.State.Failed) {
                _ui.update { it.copy(deepStatus = end.why, deepCanAdd = true) }
            } else {
                wakeDeep()
            }
        }
    }

    /** Import the deep brain pack from files the person picked (brains.json plus the brain files). */
    fun importDeepBrain(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(deepCanAdd = false, deepStatus = "Copying the deep brain") }
        viewModelScope.launch(Dispatchers.IO) {
            val end = pack.importFrom(getApplication<Application>().contentResolver, uris) { s ->
                val text = when (s) {
                    is BrainPack.State.Downloading -> "Copying the deep brain: %.1f of %.1f GB".format(s.done / 1e9, s.total / 1e9)
                    BrainPack.State.Checking -> "Checking the deep brain files"
                    else -> null
                }
                if (text != null) _ui.update { it.copy(deepStatus = text) }
            }
            if (end is BrainPack.State.Failed) _ui.update { it.copy(deepStatus = end.why, deepCanAdd = true) } else wakeDeep()
        }
    }

    /**
     * After the fast brain: on big jobs (or with a shadow) the deep brain redoes the removal and
     * quietly swaps its better result in, as long as nothing else was removed meanwhile.
     */
    private fun deepUpgrade(d: EditDocument, fast: Patch, crop: Argb, picked: app.nanogone.imaging.mask.Mask, r: Int, bigJob: Boolean) {
        val engine = deep ?: return
        if (!bigJob) return
        _ui.update { it.copy(deepWorking = true) }
        viewModelScope.launch {
            val better = withContext(Dispatchers.Default) {
                runCatching {
                    val clock = StageClock("deep")
                    val mask = MaskOps.grow(picked, r)
                    val res = engine.repair(crop, mask)
                    clock.lap("deep brain repair ${crop.width}x${crop.height}")
                    val filled = Grain.match(res.filled, res.changed)
                    val out = crop.copy()
                    Paste.feathered(out, filled, 0, 0, res.changed, feather = maxOf(1.5f, r / 2f))
                    clock.lap("deep paste")
                    Patch(fast.rect, out.px, res.changed)
                }.onFailure { android.util.Log.w("NanoGone", "deep brain skipped", it) }.getOrNull()
            }
            if (better != null && d === doc && d.upgradePatch(fast, better)) refresh()
            _ui.update { it.copy(deepWorking = false) }
        }
    }


    fun open(uri: Uri) {
        _ui.update { it.copy(busy = "Opening your photo", message = null, saved = null) }
        viewModelScope.launch {
            try {
                val (photo, bmp) = withContext(Dispatchers.IO) {
                    val p = reader.open(uri)
                    p to reader.displayBitmap(p)
                }
                doc = EditDocument(photo.width, photo.height)
                base = bmp
                _ui.update {
                    it.copy(
                        photo = photo, original = bmp, display = bmp.copy(Bitmap.Config.ARGB_8888, true),
                        version = it.version + 1, selection = emptyList(), canUndo = false, canRedo = false, busy = null,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, message = e.message ?: "This photo could not be opened.") }
            }
        }
    }

    fun close() {
        doc = null
        base = null
        enhanced = null
        _ui.update { EditorUi(lastFormat = it.lastFormat, brains = it.brains, canUpscale = it.canUpscale, canFixFaces = it.canFixFaces) }
    }

    fun setTool(t: Tool) = _ui.update { it.copy(tool = t, justRemoved = false) }

    fun toggleShadowCatcher() = _ui.update { it.copy(shadowCatcher = !it.shadowCatcher) }

    fun addShape(shape: Shape) {
        val d = doc ?: return
        clearEnhance()
        _ui.update { it.copy(justRemoved = false) }
        d.addShape(shape)
        publishSelection()
    }

    fun undo() { enhanced = null; _ui.update { it.copy(enhanced = false, enhancedSize = null) }; doc?.undo(); refresh() }

    fun redo() { enhanced = null; _ui.update { it.copy(enhanced = false, enhancedSize = null) }; doc?.redo(); refresh() }

    fun clearSelection() { doc?.clearSelection(); publishSelection() }

    fun dismissMessage() = _ui.update { it.copy(message = null, saved = null) }

    private fun publishSelection() {
        val d = doc ?: return
        _ui.update { it.copy(selection = d.state.selection, canUndo = d.canUndo, canRedo = d.canRedo) }
    }

    /** Rebuild the screen copy after undo or redo (removals may have changed). */
    private fun refresh() {
        val d = doc ?: return
        val photo = _ui.value.photo ?: return
        val b = base ?: return
        viewModelScope.launch {
            val shown = withContext(Dispatchers.Default) {
                val out = b.copy(Bitmap.Config.ARGB_8888, true)
                for (p in d.state.patches) drawPatch(out, p, photo)
                out
            }
            _ui.update { it.copy(display = shown, version = it.version + 1) }
            publishSelection()
        }
    }

    fun remove() {
        val d = doc ?: return
        val photo = _ui.value.photo ?: return
        val sel = d.selectionBounds() ?: return
        _ui.update { it.copy(busy = "Lifting the mist") }
        viewModelScope.launch {
            try {
                val patch = withContext(Dispatchers.Default) {
                    val clock = StageClock("remove")
                    val pre = d.selectionMask(sel)
                    clock.lap("selection mask ${sel.width}x${sel.height}")
                    val r = MaskOps.growRadiusFor(pre)
                    val grown = IntRect(sel.left - r, sel.top - r, sel.right + r, sel.bottom + r).intersect(d.image)
                    val ctx = CropPlanner.contextBox(grown, photo.width, photo.height, contextScale = 1.5f) // more view = better fill (tested: error 12.4 to 8.4)
                    val crop = saver.composite(photo, ctx, d.state.patches)
                    clock.lap("decode crop ${ctx.width}x${ctx.height}")
                    var picked = d.selectionMask(ctx)
                    var caught = false
                    if (_ui.value.shadowCatcher) {
                        val shadow = ShadowFinder.find(crop, picked)
                        if (!shadow.isEmpty()) { picked = MaskOps.union(picked, shadow); caught = true }
                        clock.lap("shadow catcher (caught=$caught)")
                    }
                    shadowCaught = caught
                    caughtForDeep = caught
                    val mask = MaskOps.grow(picked, r)
                    clock.lap("mask and grow r=$r")
                    val filled = Grain.match(engine.repair(crop, mask), mask)
                    clock.lap("repair (${engine.name}) and grain match")
                    val result = crop.copy()
                    Paste.feathered(result, filled, 0, 0, mask, feather = maxOf(1.5f, r / 2f))
                    clock.lap("paste")
                    deepJob = Triple(crop, picked, r)
                    Patch(ctx, result.px, mask)
                }
                d.addPatch(patch)
                deepJob?.also { deepJob = null }?.let { (crop, picked, r) ->
                    val big = caughtForDeep || picked.count() >= 0.04f * crop.width * crop.height
                    deepUpgrade(d, patch, crop, picked, r, big)
                }
                val display = _ui.value.display ?: return@launch
                val shown = withContext(Dispatchers.Default) {
                    val clock = StageClock("remove")
                    display.copy(Bitmap.Config.ARGB_8888, true).also { drawPatch(it, patch, photo) }.also { clock.lap("screen copy") }
                }
                _ui.update {
                    it.copy(display = shown, version = it.version + 1, busy = null, removals = it.removals + 1, lastShadowCaught = shadowCaught, justRemoved = true, foundCount = 0)
                }
                publishSelection()
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(busy = null, message = "That area is too big to repair in one go. Try a smaller selection.") }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, message = e.message ?: "The removal did not work. Try again.") }
            }
        }
    }

    /** Paint a removal onto the screen copy: each screen pixel takes the patch pixel under its centre. */
    private fun drawPatch(target: Bitmap, p: Patch, photo: Photo) {
        val sx = target.width.toFloat() / photo.width
        val sy = target.height.toFloat() / photo.height
        val l = kotlin.math.floor(p.rect.left * sx).toInt().coerceIn(0, target.width)
        val t = kotlin.math.floor(p.rect.top * sy).toInt().coerceIn(0, target.height)
        val r = kotlin.math.ceil(p.rect.right * sx).toInt().coerceIn(0, target.width)
        val b = kotlin.math.ceil(p.rect.bottom * sy).toInt().coerceIn(0, target.height)
        val w = r - l
        val h = b - t
        if (w <= 0 || h <= 0) return
        val px = IntArray(w * h)
        target.getPixels(px, 0, w, l, t, w, h)
        for (dy in 0 until h) for (dx in 0 until w) {
            val ix = ((l + dx + 0.5f) / sx).toInt() - p.rect.left
            val iy = ((t + dy + 0.5f) / sy).toInt() - p.rect.top
            if (ix in 0 until p.rect.width && iy in 0 until p.rect.height && p.changed[ix, iy]) {
                px[dy * w + dx] = p.pixels[iy * p.rect.width + ix]
            }
        }
        target.setPixels(px, 0, w, l, t, w, h)
    }

    fun estimate(format: SaveFormat): Long = _ui.value.photo?.let { saver.estimateBytes(it, format) } ?: 0

    fun save(format: SaveFormat) {
        val d = doc ?: return
        val photo = _ui.value.photo ?: return
        val shown = _ui.value.display ?: return
        prefs.edit().putString("format", format.name).apply()
        _ui.update { it.copy(busy = "Saving a perfect copy", lastFormat = format) }
        viewModelScope.launch {
            try {
                val e = enhanced
                val result = withContext(Dispatchers.IO) {
                    val clock = StageClock("save")
                    (if (e != null) saver.saveEnhanced(photo, e, format, d.state.patches) else saver.save(photo, d.state.patches, format, shown))
                        .also { clock.lap("${format.name} ${it.method}") }
                }
                _ui.update { it.copy(busy = null, saved = result) }
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(busy = null, message = "This photo is too big for that format on this phone. Try Top-quality JPEG.") }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, message = e.message ?: "Saving did not work. Try again.") }
            }
        }
    }
}
