package bfp.ui.theme

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Band colors carry meaning but are never the only signal: every pill also has a text label. */
@Immutable
data class BandColors(val great: Color, val good: Color, val mixed: Color, val avoid: Color)

val LocalBandColors = staticCompositionLocalOf {
    BandColors(Color(0xFF26734D), Color(0xFF3F6F8C), Color(0xFF9A6700), Color(0xFFA43B4A))
}

private val Accent = Color(0xFF0A6E75)
private val LightScheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White, primaryContainer = Color(0xFFD3ECEE), onPrimaryContainer = Color(0xFF00363A),
    background = Color(0xFFFBFCFC), surface = Color(0xFFFBFCFC), onSurface = Color(0xFF191C1D), onSurfaceVariant = Color(0xFF4B5557),
    surfaceVariant = Color(0xFFE9EEEF), secondaryContainer = Color(0xFFD3ECEE), onSecondaryContainer = Color(0xFF00363A), errorContainer = Color(0xFFF6DDE0), outline = Color(0xFF6F797B), error = Color(0xFFA43B4A),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6FD3DA), onPrimary = Color(0xFF00363A), primaryContainer = Color(0xFF00505A), onPrimaryContainer = Color(0xFFD3ECEE),
    background = Color(0xFF101415), surface = Color(0xFF101415), onSurface = Color(0xFFE0E3E4), onSurfaceVariant = Color(0xFFBFC8CA),
    surfaceVariant = Color(0xFF273032), secondaryContainer = Color(0xFF00505A), onSecondaryContainer = Color(0xFFD3ECEE), errorContainer = Color(0xFF5A2A31), outline = Color(0xFF899294), error = Color(0xFFFFB3BC),
)
private val DarkBands = BandColors(Color(0xFF7FD6A4), Color(0xFF8CC3E3), Color(0xFFE5B84A), Color(0xFFFF9AA8))

// System sans on every platform: crisp numerals, no font files to ship, scales with the OS font size.
private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

@Composable
fun BfpTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalBandColors provides if (dark) DarkBands else BandColors(Color(0xFF26734D), Color(0xFF3F6F8C), Color(0xFF9A6700), Color(0xFFA43B4A))) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = Type, content = content)
    }
}
