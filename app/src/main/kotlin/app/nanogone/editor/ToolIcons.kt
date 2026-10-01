package app.nanogone.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class Glyph { Brush, Loop, Spot, Eraser, Find, Enhance, Back, Undo, Redo, Eye }

/** One family of hand-drawn line icons on a 24-unit grid, all 1.8 dp rounded strokes. */
@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val st = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(block: Path.() -> Unit) = drawPath(Path().apply(block), color, style = st)
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        when (glyph) {
            Glyph.Brush -> {
                p { moveTo(15.5f * u, 3.5f * u); lineTo(20.5f * u, 8.5f * u); lineTo(12f * u, 17f * u); lineTo(7f * u, 12f * u); close() }
                p { moveTo(7f * u, 12f * u); cubicTo(4.5f * u, 12f * u, 3f * u, 14f * u, 3f * u, 16.5f * u); lineTo(3f * u, 21f * u); lineTo(7.5f * u, 21f * u); cubicTo(10f * u, 21f * u, 12f * u, 19.5f * u, 12f * u, 17f * u) }
            }
            Glyph.Loop -> p {
                moveTo(6f * u, 15f * u)
                cubicTo(2.5f * u, 10f * u, 6.5f * u, 4.5f * u, 12.5f * u, 4.5f * u)
                cubicTo(18.5f * u, 4.5f * u, 22f * u, 8.5f * u, 19.5f * u, 12.5f * u)
                cubicTo(17f * u, 16.5f * u, 11f * u, 16.5f * u, 8.5f * u, 15f * u)
                cubicTo(6.5f * u, 14.5f * u, 6f * u, 17.5f * u, 8f * u, 20.5f * u)
            }
            Glyph.Spot -> {
                drawCircle(color, 1.8f * u, o(12f, 12f))
                p { moveTo(12f * u, 3f * u); lineTo(12f * u, 7f * u); moveTo(12f * u, 17f * u); lineTo(12f * u, 21f * u); moveTo(3f * u, 12f * u); lineTo(7f * u, 12f * u); moveTo(17f * u, 12f * u); lineTo(21f * u, 12f * u) }
            }
            Glyph.Eraser -> {
                p { moveTo(9f * u, 20f * u); lineTo(3.8f * u, 14.8f * u); lineTo(13.5f * u, 5f * u); lineTo(20f * u, 11.5f * u); lineTo(11.5f * u, 20f * u); close() }
                p { moveTo(8.5f * u, 10f * u); lineTo(15f * u, 16.5f * u); moveTo(9f * u, 20f * u); lineTo(20.5f * u, 20f * u) }
            }
            Glyph.Find -> {
                p { moveTo(4f * u, 9f * u); lineTo(4f * u, 4f * u); lineTo(9f * u, 4f * u); moveTo(15f * u, 4f * u); lineTo(20f * u, 4f * u); lineTo(20f * u, 9f * u); moveTo(20f * u, 15f * u); lineTo(20f * u, 20f * u); lineTo(15f * u, 20f * u); moveTo(9f * u, 20f * u); lineTo(4f * u, 20f * u); lineTo(4f * u, 15f * u) }
                drawCircle(color, 3f * u, o(12f, 12f), style = st)
            }
            Glyph.Enhance -> {
                drawCircle(color, 3.6f * u, o(12f, 12f), style = st)
                p {
                    moveTo(12f * u, 3f * u); lineTo(12f * u, 5.6f * u); moveTo(12f * u, 18.4f * u); lineTo(12f * u, 21f * u)
                    moveTo(3f * u, 12f * u); lineTo(5.6f * u, 12f * u); moveTo(18.4f * u, 12f * u); lineTo(21f * u, 12f * u)
                    moveTo(5.6f * u, 5.6f * u); lineTo(7.5f * u, 7.5f * u); moveTo(16.5f * u, 16.5f * u); lineTo(18.4f * u, 18.4f * u)
                    moveTo(5.6f * u, 18.4f * u); lineTo(7.5f * u, 16.5f * u); moveTo(16.5f * u, 7.5f * u); lineTo(18.4f * u, 5.6f * u)
                }
            }
            Glyph.Back -> p { moveTo(15f * u, 5f * u); lineTo(8f * u, 12f * u); lineTo(15f * u, 19f * u) }
            Glyph.Undo -> p { moveTo(9f * u, 7f * u); lineTo(5f * u, 7f * u); lineTo(5f * u, 3f * u); moveTo(5.5f * u, 7f * u); cubicTo(8f * u, 3.5f * u, 14f * u, 3f * u, 17.5f * u, 6.5f * u); cubicTo(21f * u, 10f * u, 20f * u, 17f * u, 14f * u, 19.5f * u) }
            Glyph.Redo -> p { moveTo(15f * u, 7f * u); lineTo(19f * u, 7f * u); lineTo(19f * u, 3f * u); moveTo(18.5f * u, 7f * u); cubicTo(16f * u, 3.5f * u, 10f * u, 3f * u, 6.5f * u, 6.5f * u); cubicTo(3f * u, 10f * u, 4f * u, 17f * u, 10f * u, 19.5f * u) }
            Glyph.Eye -> {
                p { moveTo(2.5f * u, 12f * u); cubicTo(5f * u, 7f * u, 8.5f * u, 5f * u, 12f * u, 5f * u); cubicTo(15.5f * u, 5f * u, 19f * u, 7f * u, 21.5f * u, 12f * u); cubicTo(19f * u, 17f * u, 15.5f * u, 19f * u, 12f * u, 19f * u); cubicTo(8.5f * u, 19f * u, 5f * u, 17f * u, 2.5f * u, 12f * u); close() }
                drawCircle(color, 3f * u, o(12f, 12f), style = st)
            }
        }
    }
}
