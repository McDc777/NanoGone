package app.nanogone.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.nanogone.imaging.geom.CropPlanner
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

enum class Tool { Brush, Loop, Spot, Eraser }

data class EditorUi(
    val photo: Photo? = null,
    /** Screen copy in stored orientation, with removals applied. Bumped [version] on every change. */
    val display: Bitmap? = null,
    val original: Bitmap? = null,
    val version: Int = 0,
    val selection: List<Shape> = emptyList(),
    val tool: Tool = Tool.Brush,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: String? = null,
    /** Bumps each time a removal finishes, so the mist can lift. */
    val removals: Int = 0,
    val message: String? = null,
    val saved: Saver.Result? = null,
    val lastFormat: SaveFormat = SaveFormat.JPEG,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val reader = PhotoReader(app.contentResolver)
    private val saver = Saver(app.contentResolver, reader, app.cacheDir)
    private val prefs = app.getSharedPreferences("nanogone", Context.MODE_PRIVATE)
    private val engine: RepairEngine = SmoothFillEngine()

    private var doc: EditDocument? = null
    private var base: Bitmap? = null

    private val _ui = MutableStateFlow(
        EditorUi(lastFormat = runCatching { SaveFormat.valueOf(prefs.getString("format", "JPEG")!!) }.getOrDefault(SaveFormat.JPEG)),
    )
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()

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
        _ui.update { EditorUi(lastFormat = it.lastFormat) }
    }

    fun setTool(t: Tool) = _ui.update { it.copy(tool = t) }

    fun addShape(shape: Shape) {
        val d = doc ?: return
        d.addShape(shape)
        publishSelection()
    }

    fun undo() { doc?.undo(); refresh() }

    fun redo() { doc?.redo(); refresh() }

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
                    val pre = d.selectionMask(sel)
                    val r = MaskOps.growRadiusFor(pre)
                    val grown = IntRect(sel.left - r, sel.top - r, sel.right + r, sel.bottom + r).intersect(d.image)
                    val ctx = CropPlanner.contextBox(grown, photo.width, photo.height)
                    val crop = saver.composite(photo, ctx, d.state.patches)
                    val mask = MaskOps.grow(d.selectionMask(ctx), r)
                    val filled = engine.repair(crop, mask)
                    val result = crop.copy()
                    Paste.feathered(result, filled, 0, 0, mask, feather = maxOf(1.5f, r / 2f))
                    Patch(ctx, result.px, mask)
                }
                d.addPatch(patch)
                val display = _ui.value.display ?: return@launch
                val shown = withContext(Dispatchers.Default) {
                    display.copy(Bitmap.Config.ARGB_8888, true).also { drawPatch(it, patch, photo) }
                }
                _ui.update {
                    it.copy(display = shown, version = it.version + 1, busy = null, removals = it.removals + 1)
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
                val result = withContext(Dispatchers.IO) { saver.save(photo, d.state.patches, format, shown) }
                _ui.update { it.copy(busy = null, saved = result) }
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(busy = null, message = "This photo is too big for that format on this phone. Try Top-quality JPEG.") }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = null, message = e.message ?: "Saving did not work. Try again.") }
            }
        }
    }
}
