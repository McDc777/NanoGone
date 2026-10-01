package app.nanogone.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.nanogone.ui.FrostedPane
import app.nanogone.ui.LocalDawn
import app.nanogone.ui.PillShape

/** Welcome: the one huge type moment, and the way in. */
@Composable
fun HomeScreen(busy: String?, message: String?, brains: String, onPick: () -> Unit) {
    val p = LocalDawn.current
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            "NanoGone",
            style = MaterialTheme.typography.displayLarge,
            color = p.text,
            modifier = Modifier.graphicsLayer {
                alpha = rise.value
                translationY = (1f - rise.value) * 40.dp.toPx()
            },
        )
        Text(
            "Tap it, loop it, brush it. It lifts away like morning mist, and every other pixel stays exactly as it was.",
            style = MaterialTheme.typography.bodyLarge,
            color = p.textSoft,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(PillShape)
                .background(Brush.horizontalGradient(listOf(p.accent, p.gold)))
                .clickable(enabled = busy == null, onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Text(busy ?: "Choose a photo", style = MaterialTheme.typography.titleLarge, color = p.text)
        }
        FrostedPane(Modifier.fillMaxWidth()) {
            Text(
                message ?: "Or open any photo in Samsung Gallery or Google Photos, tap Share, and pick NanoGone.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message != null) p.error else p.textSoft,
            )
        }
        Text(brains, style = MaterialTheme.typography.labelSmall, color = p.textSoft)
        Spacer(Modifier.height(16.dp))
    }
}
