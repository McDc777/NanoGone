package app.nanogone.editor

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.magnifier
import app.nanogone.save.SaveFormat
import app.nanogone.ui.FrostedPane
import app.nanogone.ui.LocalDawn
import app.nanogone.ui.PhotoShape
import app.nanogone.ui.PillShape
import app.nanogone.ui.SoftButton
import kotlin.math.max
import kotlin.math.min

@Composable
fun EditorScreen(ui: EditorUi, vm: EditorViewModel, onBack: () -> Unit) {
    val p = LocalDawn.current
    val photo = ui.photo ?: return
    var showSave by remember { mutableStateOf(false) }
    val beforeSource = remember { MutableInteractionSource() }
    val showBefore by beforeSource.collectIsPressedAsState()

    // The mist that lifts after a removal keeps the shapes it was covering.
    var lifting by remember { mutableStateOf<List<Shape>>(emptyList()) }
    val lift = remember { Animatable(0f) }
    LaunchedEffect(ui.removals) {
        if (lifting.isNotEmpty()) {
            lift.snapTo(0f)
            lift.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
            lifting = emptyList()
            lift.snapTo(0f)
        }
    }

    LaunchedEffect(ui.message) { if (ui.message != null) lifting = emptyList() }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Top pane: back, wordmark, undo, redo, before, save.
        FrostedPane(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(Glyph.Back, "Back", onClick = onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("NanoGone", style = MaterialTheme.typography.titleLarge, color = p.text)
                    Text(
                        "${photo.displayName} · ${"%.0f".format(photo.megapixels)} MP",
                        style = MaterialTheme.typography.labelSmall, color = p.textSoft, maxLines = 1,
                    )
                }
                IconTap(Glyph.Undo, "Undo", enabled = ui.canUndo && ui.busy == null) { vm.undo() }
                IconTap(Glyph.Redo, "Redo", enabled = ui.canRedo && ui.busy == null) { vm.redo() }
                Box(
                    Modifier.size(40.dp).clip(PillShape).clickable(interactionSource = beforeSource, indication = null) {},
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Eye, if (showBefore) p.accentDeep else p.text, size = 22.dp) }
                Spacer(Modifier.size(4.dp))
                SoftButton(onClick = { showSave = true }, enabled = ui.busy == null, shape = PillShape, corner = 20.dp) {
                    Text("Save", Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = p.text)
                }
            }
        }

        PhotoCanvas(
            ui = ui,
            showBefore = showBefore,
            lifting = lifting,
            lift = lift.value,
            onShape = vm::addShape,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        // Bottom pane: hint, tools, Remove.
        FrostedPane(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    ui.busy ?: ui.message ?: hintFor(ui),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ui.message != null && ui.busy == null) p.error else p.textSoft,
                    modifier = Modifier.fillMaxWidth().clickable(enabled = ui.message != null) { vm.dismissMessage() },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton(Glyph.Brush, "Brush", ui.tool == Tool.Brush, Modifier.weight(1f)) { vm.setTool(Tool.Brush) }
                    ToolButton(Glyph.Loop, "Loop", ui.tool == Tool.Loop, Modifier.weight(1f)) { vm.setTool(Tool.Loop) }
                    ToolButton(Glyph.Spot, "Spot", ui.tool == Tool.Spot, Modifier.weight(1f)) { vm.setTool(Tool.Spot) }
                    ToolButton(Glyph.Eraser, "Unpick", ui.tool == Tool.Eraser, Modifier.weight(1f)) { vm.setTool(Tool.Eraser) }
                    ToolButton(Glyph.Find, "Find", false, Modifier.weight(1f), enabled = false) {}
                    ToolButton(Glyph.Enhance, "Enhance", false, Modifier.weight(1f), enabled = false) {}
                }
                val canRemove = ui.selection.any { !(it is BrushStroke && it.erase) } && ui.busy == null
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(PillShape)
                        .background(
                            if (canRemove) Brush.horizontalGradient(listOf(p.accent, p.gold))
                            else Brush.horizontalGradient(listOf(p.textSoft.copy(alpha = 0.18f), p.textSoft.copy(alpha = 0.12f))),
                        )
                        .clickable(enabled = canRemove) {
                            lifting = ui.selection
                            vm.remove()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Remove", style = MaterialTheme.typography.titleLarge, color = if (canRemove) p.text else p.textSoft)
                }
            }
        }
    }

    if (showSave || ui.saved != null) {
        SaveSheet(ui, vm, onDismiss = { showSave = false; vm.dismissMessage() })
    }
}

private fun hintFor(ui: EditorUi): String = when {
    ui.selection.any { !(it is BrushStroke && it.erase) } -> "Wrapped in morning mist. Tap Remove, or keep adding."
    ui.tool == Tool.Brush -> "Paint over what should go. Pinch to zoom in for tiny things."
    ui.tool == Tool.Loop -> "Draw a loop around what should go."
    ui.tool == Tool.Spot -> "Tap a speck, a spot or a bit of dust."
    else -> "Paint over the mist to unpick it."
}

@Composable
private fun IconTap(glyph: Glyph, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalDawn.current
    Box(
        Modifier.size(40.dp).clip(PillShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (enabled) p.text else p.textSoft.copy(alpha = 0.4f), size = 22.dp) }
}

@Composable
private fun ToolButton(glyph: Glyph, label: String, selected: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalDawn.current
    SoftButton(onClick = onClick, modifier = modifier.height(60.dp), enabled = enabled, selected = selected) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val tint = when {
                !enabled -> p.textSoft.copy(alpha = 0.35f)
                selected -> p.accentDeep
                else -> p.text
            }
            GlyphIcon(glyph, tint, size = 22.dp)
            Text(if (enabled) label else "Soon", style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
        }
    }
}

/** The photo with zoom, pan, drawing and the mist. All edit coordinates are stored-image pixels. */
@Composable
private fun PhotoCanvas(
    ui: EditorUi,
    showBefore: Boolean,
    lifting: List<Shape>,
    lift: Float,
    onShape: (Shape) -> Unit,
    modifier: Modifier,
) {
    val p = LocalDawn.current
    val photo = ui.photo ?: return
    val density = LocalDensity.current
    val shown = if (showBefore) ui.original else ui.display
    val image = remember(shown, ui.version, showBefore) { shown?.asImageBitmap() }
    val w = photo.width.toFloat()
    val h = photo.height.toFloat()
    val rot = photo.rotation
    val rw = if (rot % 180 == 0) w else h
    val rh = if (rot % 180 == 0) h else w

    var box by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(photo) { mutableFloatStateOf(1f) }
    var pan by remember(photo) { mutableStateOf(Offset.Zero) }
    val live = remember { mutableStateListOf<Offset>() }
    var finger by remember { mutableStateOf(Offset.Unspecified) }

    // Read fresh each time: the gesture handler outlives a single composition.
    fun currentScale(): Float = (if (box.width == 0) 1f else min(box.width / rw, box.height / rh)) * zoom
    val brushScreenPx = with(density) { 18.dp.toPx() }
    val spotScreenPx = with(density) { 10.dp.toPx() }

    fun toImage(s: Offset): Offset {
        val scale = currentScale()
        val u = (s.x - box.width / 2f - pan.x) / scale + rw / 2f
        val v = (s.y - box.height / 2f - pan.y) / scale + rh / 2f
        return when (rot) {
            90 -> Offset(v, h - u)
            180 -> Offset(w - u, h - v)
            270 -> Offset(w - v, u)
            else -> Offset(u, v)
        }
    }

    val breathing = rememberInfiniteTransition(label = "mist")
    val breath by breathing.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse), label = "breath")

    Box(
        modifier
            .clip(PhotoShape)
            .background(p.surface.copy(alpha = 0.35f))
            .onSizeChanged { box = it }
            .magnifier(
                sourceCenter = { finger },
                magnifierCenter = { if (finger.isSpecified) finger - Offset(0f, 120.dp.toPx()) else Offset.Unspecified },
                zoom = 2.5f,
                size = DpSize(112.dp, 112.dp),
                cornerRadius = 56.dp,
            )
            .pointerInput(photo, ui.tool) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var multi = false
                    live.clear()
                    live.add(toImage(down.position))
                    finger = if (ui.tool == Tool.Brush || ui.tool == Tool.Eraser) down.position else Offset.Unspecified
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (!multi) { multi = true; live.clear(); finger = Offset.Unspecified }
                            val zc = event.calculateZoom()
                            val pc = event.calculatePan()
                            val c = event.calculateCentroid() - Offset(box.width / 2f, box.height / 2f)
                            val nz = (zoom * zc).coerceIn(1f, 40f)
                            val f = nz / zoom
                            pan = (pan - c) * f + c + pc
                            zoom = nz
                            event.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val pos = pressed[0].position
                            live.add(toImage(pos))
                            if (finger.isSpecified) finger = pos
                            pressed[0].consume()
                        }
                    }
                    finger = Offset.Unspecified
                    if (!multi && live.isNotEmpty()) {
                        val scale = currentScale()
                        val xs = FloatArray(live.size) { live[it].x }
                        val ys = FloatArray(live.size) { live[it].y }
                        when (ui.tool) {
                            Tool.Brush -> onShape(BrushStroke(xs, ys, brushScreenPx / scale, erase = false))
                            Tool.Eraser -> onShape(BrushStroke(xs, ys, brushScreenPx / scale, erase = true))
                            Tool.Spot -> onShape(Spot(xs.last(), ys.last(), spotScreenPx / scale))
                            Tool.Loop -> if (live.size >= 3) onShape(Loop(xs, ys))
                        }
                    }
                    live.clear()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val img = image ?: return@Canvas
            val sc = currentScale()
            withTransform({
                translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                scale(sc, sc, pivot = Offset.Zero)
                rotate(rot.toFloat(), pivot = Offset.Zero)
                translate(-w / 2f, -h / 2f)
            }) {
                drawImage(
                    img,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(photo.width, photo.height),
                    filterQuality = FilterQuality.Medium,
                )
                if (!showBefore) {
                    val pxPerScreen = 1f / sc
                    if (lifting.isEmpty()) drawMist(ui.selection, p.mist, p.gold, pxPerScreen, breath, 0f)
                    if (lifting.isNotEmpty()) drawMist(lifting, p.mist, p.gold, pxPerScreen, breath, lift)
                    if (live.size > 0) {
                        val preview: Shape = when (ui.tool) {
                            Tool.Loop -> if (live.size >= 3) Loop(FloatArray(live.size) { live[it].x }, FloatArray(live.size) { live[it].y })
                            else BrushStroke(floatArrayOf(live[0].x), floatArrayOf(live[0].y), 2f * pxPerScreen, false)
                            Tool.Spot -> Spot(live.last().x, live.last().y, spotScreenPx * pxPerScreen)
                            else -> BrushStroke(FloatArray(live.size) { live[it].x }, FloatArray(live.size) { live[it].y }, brushScreenPx * pxPerScreen, ui.tool == Tool.Eraser)
                        }
                        drawMist(ui.selection + preview, p.mist, p.gold, pxPerScreen, breath, 0f)
                    }
                }
            }
        }
        AnimatedVisibility(showBefore, Modifier.align(Alignment.TopCenter).padding(10.dp), enter = fadeIn(), exit = fadeOut()) {
            FrostedPane(shape = PillShape) {
                Text("Before", Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = p.text)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(ui: EditorUi, vm: EditorViewModel, onDismiss: () -> Unit) {
    val p = LocalDawn.current
    val context = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = p.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val saved = ui.saved
            if (saved != null) {
                Text("Saved", style = MaterialTheme.typography.displayMedium, color = p.text)
                Text(
                    "${saved.fileName} is in the NanoGone album. ${mb(saved.bytes)}, ${saved.method}. Your original is untouched.",
                    style = MaterialTheme.typography.bodyLarge, color = p.textSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType(if (saved.fileName.endsWith("png")) "image/png" else "image/jpeg")
                            .putExtra(Intent.EXTRA_STREAM, saved.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, "Share"))
                    }, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Share", style = MaterialTheme.typography.titleMedium, color = p.text)
                    }
                    SoftButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(52.dp)) {
                        Text("Keep editing", style = MaterialTheme.typography.titleMedium, color = p.text)
                    }
                }
            } else {
                Text("Save a copy", style = MaterialTheme.typography.headlineMedium, color = p.text)
                Text(
                    "Same size and shape as the original. No stamp. Date and place kept. The original stays as it is.",
                    style = MaterialTheme.typography.bodyMedium, color = p.textSoft,
                )
                FormatChoice(
                    title = "Top-quality JPEG",
                    detail = "About ${mb(vm.estimate(SaveFormat.JPEG))}. Opens and shares everywhere.",
                    selected = ui.lastFormat == SaveFormat.JPEG,
                    enabled = ui.busy == null,
                ) { vm.save(SaveFormat.JPEG) }
                FormatChoice(
                    title = "Lossless PNG",
                    detail = "About ${mb(vm.estimate(SaveFormat.PNG))}. Not one dot of quality lost.",
                    selected = ui.lastFormat == SaveFormat.PNG,
                    enabled = ui.busy == null,
                ) { vm.save(SaveFormat.PNG) }
                if (ui.busy != null) Text(ui.busy, style = MaterialTheme.typography.bodyMedium, color = p.textSoft)
                if (ui.message != null) Text(ui.message, style = MaterialTheme.typography.bodyMedium, color = p.error)
            }
        }
    }
}

@Composable
private fun FormatChoice(title: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalDawn.current
    SoftButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = p.textSoft)
            }
            if (selected) {
                Box(Modifier.size(10.dp).clip(PillShape).background(p.accent))
            }
        }
    }
}

private fun mb(bytes: Long): String = if (bytes >= 1_000_000) "%.1f MB".format(bytes / 1_000_000f) else "${max(1, bytes / 1000)} KB"

