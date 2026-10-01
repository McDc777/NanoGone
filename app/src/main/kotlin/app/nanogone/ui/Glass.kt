package app.nanogone.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val PaneShape = RoundedCornerShape(28.dp)
val ButtonShape = RoundedCornerShape(18.dp)
val PhotoShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(50)

/** The dawn sky behind everything: lake blue at the top, first gold at the horizon, a faint sun glow. */
@Composable
fun DawnBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = LocalDawn.current
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to p.skyTop, 0.42f to p.rose, 0.72f to p.peach, 1f to p.gold))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(p.gold.copy(alpha = if (p.isDark) 0.18f else 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.92f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.18f, size.height * 0.92f),
                )
            },
        content = content,
    )
}

/**
 * A floating pane of frosted acrylic. Light comes from the sunrise, top left: a bright inner
 * edge at the top, a soft plum shadow below.
 */
@Composable
fun FrostedPane(
    modifier: Modifier = Modifier,
    shape: Shape = PaneShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalDawn.current
    Box(
        modifier
            .drawBehind {
                val r = CornerRadius(28.dp.toPx())
                drawRoundRect(p.shadow.copy(alpha = p.shadow.alpha * 0.6f), topLeft = Offset(0f, 10.dp.toPx()), size = size, cornerRadius = r)
            }
            .background(
                Brush.verticalGradient(listOf(p.glass.copy(alpha = (p.glass.alpha + 0.12f).coerceAtMost(1f)), p.glass)),
                shape,
            )
            .border(1.dp, Brush.verticalGradient(listOf(p.glassEdge, p.glassEdge.copy(alpha = 0.1f))), shape),
        content = content,
    )
}

/** A soft, pillowy button. Pressed, it sinks into the glass. */
@Composable
fun SoftButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    shape: Shape = ButtonShape,
    corner: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalDawn.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sunk = pressed || selected
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, dawnSpring(), label = "press")
    Box(
        modifier
            .scale(scale)
            .drawBehind {
                val r = CornerRadius(corner.toPx())
                if (!sunk) {
                    drawRoundRect(p.highlight, topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()), size = size, cornerRadius = r)
                    drawRoundRect(p.shadow, topLeft = Offset(4.dp.toPx(), 5.dp.toPx()), size = size, cornerRadius = r)
                }
            }
            .background(
                if (sunk) Brush.linearGradient(listOf(p.shadow.copy(alpha = 0.18f), p.highlight.copy(alpha = 0.35f)))
                else Brush.linearGradient(listOf(p.highlight, p.glass)),
                shape,
            )
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
