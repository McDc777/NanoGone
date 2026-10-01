package app.nanogone.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.nanogone.R

/** Dawn Mist colours (see ART_DIRECTION.md). Light is canonical; dark is the blue hour before dawn. */
@Immutable
data class DawnPalette(
    val skyTop: Color,
    val rose: Color,
    val peach: Color,
    val gold: Color,
    val surface: Color,
    val text: Color,
    val textSoft: Color,
    val accent: Color,
    val accentDeep: Color,
    val glass: Color,
    val glassEdge: Color,
    val shadow: Color,
    val highlight: Color,
    val mist: Color,
    val error: Color,
    val isDark: Boolean,
)

val DawnLight = DawnPalette(
    skyTop = Color(0xFFD6E6F2),
    rose = Color(0xFFF4D3DC),
    peach = Color(0xFFFBE3D6),
    gold = Color(0xFFFFE6A6),
    surface = Color(0xFFF7F1EC),
    text = Color(0xFF2E2838),
    textSoft = Color(0xFF6E6478),
    accent = Color(0xFFFF8A5C),
    accentDeep = Color(0xFFE5683A),
    glass = Color(0x8CFFFAF6),
    glassEdge = Color(0xBFFFFFFF),
    shadow = Color(0x407A5668),
    highlight = Color(0xD9FFFFFF),
    mist = Color(0xFFFFF4EA),
    error = Color(0xFFD64545),
    isDark = false,
)

val DawnDark = DawnPalette(
    skyTop = Color(0xFF121726),
    rose = Color(0xFF231C2E),
    peach = Color(0xFF3A2A3A),
    gold = Color(0xFF4A3634),
    surface = Color(0xFF1A2032),
    text = Color(0xFFEEE8F0),
    textSoft = Color(0xFFB5ACC0),
    accent = Color(0xFFE98A66),
    accentDeep = Color(0xFFCC6F4E),
    glass = Color(0x732A3048),
    glassEdge = Color(0x40FFFFFF),
    shadow = Color(0x80060810),
    highlight = Color(0x26FFFFFF),
    mist = Color(0xFFF3E6EE),
    error = Color(0xFFFF7A7A),
    isDark = true,
)

val LocalDawn = staticCompositionLocalOf { DawnLight }

val Syne = FontFamily(
    Font(R.font.syne_semibold, FontWeight.SemiBold),
    Font(R.font.syne_bold, FontWeight.Bold),
    Font(R.font.syne_extrabold, FontWeight.ExtraBold),
)

val InstrumentSans = FontFamily(
    Font(R.font.instrument_sans_regular, FontWeight.Normal),
    Font(R.font.instrument_sans_medium, FontWeight.Medium),
    Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
)

private val DawnTypography = Typography(
    displayLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 66.sp, letterSpacing = (-0.03).em),
    displayMedium = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.025).em),
    headlineMedium = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.015).em),
    titleLarge = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.02.em),
    labelMedium = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.03.em),
    labelSmall = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em),
)

/** The one app spring (ART_DIRECTION A7). */
fun <T> dawnSpring() = spring<T>(dampingRatio = 0.78f, stiffness = 320f)

@Composable
fun DawnTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DawnDark else DawnLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color(0xFF1B1420), background = p.surface, onBackground = p.text,
            surface = p.surface, onSurface = p.text, onSurfaceVariant = p.textSoft, error = p.error,
            surfaceContainer = Color(0xFF222A3E), surfaceContainerHigh = Color(0xFF2A3248),
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.text, background = p.surface, onBackground = p.text,
            surface = p.surface, onSurface = p.text, onSurfaceVariant = p.textSoft, error = p.error,
            surfaceContainer = Color(0xFFF3E9E3), surfaceContainerHigh = Color(0xFFEFE2DB),
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalDawn provides p) {
        MaterialTheme(colorScheme = scheme, typography = DawnTypography, content = content)
    }
}
