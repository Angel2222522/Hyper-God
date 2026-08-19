package com.angel.hypergod.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angel.hypergod.R

private val NotoSans = FontFamily(
    Font(R.font.noto_sans_variable, FontWeight.Normal),
    Font(R.font.noto_sans_variable, FontWeight.Medium),
    Font(R.font.noto_sans_variable, FontWeight.SemiBold),
    Font(R.font.noto_sans_variable, FontWeight.Bold)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF31559A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = Color(0xFF102E67),
    secondary = Color(0xFF006B60),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFA7F2E4),
    onSecondaryContainer = Color(0xFF003730),
    tertiary = Color(0xFF75566F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8F4),
    onTertiaryContainer = Color(0xFF43283F),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF171A21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A21),
    surfaceVariant = Color(0xFFE3E7F0),
    onSurfaceVariant = Color(0xFF444A57),
    outline = Color(0xFF747B89),
    outlineVariant = Color(0xFFC4C9D4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFEBEDF3),
    surfaceContainerHigh = Color(0xFFE5E8EF),
    surfaceContainerHighest = Color(0xFFDFE2E9)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB3C7FF),
    onPrimary = Color(0xFF002A69),
    primaryContainer = Color(0xFF173E7E),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFF86D5C8),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFA7F2E4),
    tertiary = Color(0xFFE4BADA),
    onTertiary = Color(0xFF42283F),
    tertiaryContainer = Color(0xFF5A3E56),
    onTertiaryContainer = Color(0xFFFFD8F4),
    error = Color(0xFFFFB4A9),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF0C1017),
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF11161F),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF444A57),
    onSurfaceVariant = Color(0xFFC4C9D4),
    outline = Color(0xFF8E94A2),
    outlineVariant = Color(0xFF444A57),
    surfaceContainerLowest = Color(0xFF080B11),
    surfaceContainerLow = Color(0xFF141922),
    surfaceContainer = Color(0xFF181D27),
    surfaceContainerHigh = Color(0xFF222832),
    surfaceContainerHighest = Color(0xFF2D333E)
)

private val HyperGodTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = NotoSans,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 43.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 39.sp, letterSpacing = (-0.35).sp),
    headlineMedium = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 35.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 31.sp, letterSpacing = (-0.15).sp),
    titleLarge = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.05.sp),
    titleSmall = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.12.sp),
    bodySmall = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.18.sp),
    labelLarge = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.08.sp),
    labelMedium = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = NotoSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp)
)

private val HyperGodShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

@Composable
fun HyperGodTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HyperGodTypography,
        shapes = HyperGodShapes,
        content = content
    )
}
