package app.aimode.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Solar = Color(0xFFFF5B35)
val Acid = Color(0xFFC8FF63)
val Iris = Color(0xFF606CFF)
val Ink = Color(0xFF151411)
val Paper = Color(0xFFF5F1E8)
val Night = Color(0xFF0C0D0F)

private val LightColors = lightColorScheme(
    primary = Solar,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDFD6),
    onPrimaryContainer = Color(0xFF481207),
    secondary = Iris,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E3FF),
    onSecondaryContainer = Color(0xFF151C6B),
    tertiary = Color(0xFF4E6A13),
    onTertiary = Color.White,
    tertiaryContainer = Acid,
    onTertiaryContainer = Color(0xFF182600),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFBF8F1),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE5DB),
    onSurfaceVariant = Color(0xFF625F58),
    outline = Color(0xFF8A857C),
    outlineVariant = Color(0xFFD3CDC2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8264),
    onPrimary = Color(0xFF4B1003),
    primaryContainer = Color(0xFF6B210F),
    onPrimaryContainer = Color(0xFFFFDBD1),
    secondary = Color(0xFFBEC2FF),
    onSecondary = Color(0xFF202A80),
    secondaryContainer = Color(0xFF343E96),
    onSecondaryContainer = Color(0xFFE1E3FF),
    tertiary = Acid,
    onTertiary = Color(0xFF233600),
    tertiaryContainer = Color(0xFF374F08),
    onTertiaryContainer = Color(0xFFD7FF8D),
    background = Night,
    onBackground = Color(0xFFF2EFE8),
    surface = Color(0xFF131518),
    onSurface = Color(0xFFF2EFE8),
    surfaceVariant = Color(0xFF25282D),
    onSurfaceVariant = Color(0xFFC6C5C1),
    outline = Color(0xFF918F8A),
    outlineVariant = Color(0xFF414348),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun AIModeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
