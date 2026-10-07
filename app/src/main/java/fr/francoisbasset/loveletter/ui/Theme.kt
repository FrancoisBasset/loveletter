package fr.francoisbasset.loveletter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF15151D)
internal val Wine = Color(0xFF632F42)
internal val Ruby = Color(0xFFAD6577)
internal val Gold = Color(0xFFE3C58D)
internal val Ivory = Color(0xFFF6ECD9)
internal val Muted = Color(0xFFB7AAB0)
internal val Panel = Color(0xFF26232D)
internal val Teal = Color(0xFF91C8BC)

@Composable
fun LoveLetterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Ink,
            secondary = Ruby, onSecondary = Ink,
            tertiary = Teal, onTertiary = Ink,
            background = Ink, onBackground = Ivory,
            surface = Panel, onSurface = Ivory,
            surfaceVariant = Color(0xFF36303B), onSurfaceVariant = Muted,
            outline = Color(0xFF64545C), error = Color(0xFFFFB4AB),
        ),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 54.sp, lineHeight = 58.sp),
            displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 40.sp, lineHeight = 44.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 32.sp, lineHeight = 37.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 27.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
            labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
            labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, lineHeight = 16.sp),
        ),
        content = content,
    )
}
