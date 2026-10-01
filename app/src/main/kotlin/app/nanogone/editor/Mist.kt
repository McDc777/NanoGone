package app.nanogone.editor

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * The signature move (ART_DIRECTION A3): whatever is selected sits under warm, glowing morning
 * mist. The soft mist is painted once into a small image ([renderMist]); each frame only fades,
 * breathes or lifts that image, so it stays smooth even on slow graphics.
 */
fun renderMist(shapes: List<Shape>, imageW: Int, imageH: Int, mist: Color, glow: Color, maxSide: Int = 1024): ImageBitmap? {
    if (shapes.none { !(it is BrushStroke && it.erase) }) return null
    val k = maxSide.toFloat() / maxOf(imageW, imageH)
    val bw = maxOf(1, (imageW * k).toInt())
    val bh = maxOf(1, (imageH * k).toInt())
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(k, k)
    val longSide = maxOf(imageW, imageH).toFloat()
    pass(c, shapes, glow, 0.5f, 1.7f, longSide * 0.012f)
    pass(c, shapes, mist, 0.72f, 1.0f, longSide * 0.004f)
    return bmp.asImageBitmap()
}

/** Draw a rendered mist over the photo (already in stored-image coordinates). */
fun DrawScope.drawMistImage(img: ImageBitmap, imageW: Int, imageH: Int, breath: Float, lift: Float) {
    val fade = (1f - lift).coerceIn(0f, 1f)
    if (fade <= 0f) return
    val rise = -lift * imageH * 0.05f
    val swell = 1f + 0.12f * lift
    val w = (imageW * swell).toInt()
    val h = (imageH * swell).toInt()
    drawImage(
        img,
        dstOffset = IntOffset(((imageW - w) / 2f).toInt(), ((imageH - h) / 2f + rise).toInt()),
        dstSize = IntSize(w, h),
        alpha = (0.82f + 0.18f * breath) * fade,
    )
}

/** The stroke being drawn right now: a quick, unblurred trail of mist under the finger. */
fun DrawScope.drawLiveTrail(points: List<Offset>, radius: Float, mist: Color, loop: Boolean) {
    if (points.isEmpty()) return
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        if (loop) close()
    }
    drawPath(
        path,
        mist.copy(alpha = 0.55f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = if (loop) radius else 2 * radius,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        ),
    )
}

private fun pass(nc: Canvas, shapes: List<Shape>, color: Color, alpha: Float, widthScale: Float, blur: Float) {
    val layer = nc.saveLayer(null, null)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.copy(alpha = alpha).toArgb()
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
                paint.strokeWidth = blur * widthScale
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
