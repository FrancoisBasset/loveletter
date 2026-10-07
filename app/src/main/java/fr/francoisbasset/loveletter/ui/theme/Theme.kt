package fr.francoisbasset.loveletter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Burgundy = Color(0xFF421B2C)
val Wine = Color(0xFF7C3049)
val Ivory = Color(0xFFF8F0DF)
val Gold = Color(0xFFC69C54)
val Ink = Color(0xFF302127)
val Muted = Color(0xFF776C64)

private val Scheme = lightColorScheme(
    primary = Wine, onPrimary = Color.White, primaryContainer = Color(0xFFF1D6DB),
    onPrimaryContainer = Burgundy, secondary = Color(0xFF806333),
    onSecondary = Color.White, secondaryContainer = Color(0xFFF1E2BD),
    tertiary = Color(0xFF41685E), background = Ivory, onBackground = Ink,
    surface = Color(0xFFFFF9EF), onSurface = Ink,
    surfaceVariant = Color(0xFFEDE3D3), onSurfaceVariant = Muted,
    outline = Color(0xFFB6A99A), error = Color(0xFFB3261E)
)
private val LetterTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.7.sp)
)

@Composable
fun LoveLetterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LetterTypography, content = content)
}
