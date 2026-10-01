package app.nanogone.editor

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

/**
 * The signature move (ART_DIRECTION A3): whatever is selected sits under warm, glowing morning
 * mist. [lift] goes 0 to 1 as the mist rises and evaporates after a removal. Coordinates are
 * stored-image pixels; [pxPerScreenPx] converts screen sizes into image sizes.
 */
fun DrawScope.drawMist(
    shapes: List<Shape>,
    mist: Color,
    glow: Color,
    pxPerScreenPx: Float,
    breath: Float,
    lift: Float,
) {
    if (shapes.isEmpty()) return
    val fade = (1f - lift).coerceIn(0f, 1f)
    if (fade <= 0f) return
    val rise = -lift * 46f * pxPerScreenPx
    val swell = 1f + 0.45f * lift
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        nc.translate(0f, rise)
        // Outer haze, then a brighter core.
        pass(nc, shapes, glow, (0.42f + 0.08f * breath) * fade, 1.7f * swell, 26f * pxPerScreenPx * swell)
        pass(nc, shapes, mist, (0.62f + 0.1f * breath) * fade, 1.0f * swell, 9f * pxPerScreenPx * swell)
        nc.restore()
    }
}

private fun pass(nc: android.graphics.Canvas, shapes: List<Shape>, color: Color, alpha: Float, widthScale: Float, blur: Float) {
    val layer = nc.saveLayer(null, null)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
        if (blur >= 0.5f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val eraser = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (s in shapes) {
        when (s) {
            is BrushStroke -> {
                val path = Path().apply {
                    moveTo(s.xs[0], s.ys[0])
                    if (s.xs.size == 1) lineTo(s.xs[0] + 0.01f, s.ys[0])
                    for (i in 1 until s.xs.size) lineTo(s.xs[i], s.ys[i])
                }
                if (s.erase) {
                    eraser.strokeWidth = 2 * s.radius
                    nc.drawPath(path, eraser)
                } else {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2 * s.radius * widthScale
                    nc.drawPath(path, paint)
                }
            }
            is Loop -> {
                val path = Path().apply {
                    moveTo(s.xs[0], s.ys[0])
                    for (i in 1 until s.xs.size) lineTo(s.xs[i], s.ys[i])
                    close()
                }
                paint.style = Paint.Style.FILL_AND_STROKE
                paint.strokeWidth = 4f * widthScale
                nc.drawPath(path, paint)
            }
            is Spot -> {
                paint.style = Paint.Style.FILL
                nc.drawCircle(s.cx, s.cy, s.radius * widthScale, paint)
            }
        }
    }
    nc.restoreToCount(layer)
}
