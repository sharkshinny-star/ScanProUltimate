package com.example.scanpro.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.data.ThemeMode

/*
 * ScanPro design system.
 *
 * Colour: a Material 3 scheme with every surface-container tier filled in, so elevation is expressed
 * through tone rather than shadows, plus a small set of brand colours (the hero gradient and the mint
 * "scan line" accent) that Material has no slot for. Type: Manrope, a variable font, for everything.
 */

// ---------------------------------------------------------------------------------------------
// Brand colours (outside the Material scheme)
// ---------------------------------------------------------------------------------------------

/** Colours Material 3 has no role for: the hero gradient, the scan-line accent and paper tones. */
@Immutable
data class ScanProColors(
    /** Start and end of the brand gradient used on the hero card, the scan button and the brand mark. */
    val gradientStart: Color,
    val gradientEnd: Color,
    /** Text and icons drawn on top of the brand gradient. */
    val onGradient: Color,
    /** Muted text on top of the brand gradient. */
    val onGradientMuted: Color,
    /** The mint scan-line accent; also the "success" colour of a captured page. */
    val scanLine: Color,
    /** The colour of a sheet of paper in illustrations and thumbnails. */
    val paper: Color,
    /** Ink lines drawn on illustrated paper. */
    val ink: Color
)

private val LightBrand = ScanProColors(
    gradientStart = Color(0xFF3346D3),
    gradientEnd = Color(0xFF6E5BE6),
    onGradient = Color(0xFFFFFFFF),
    onGradientMuted = Color(0xFFD9DDFF),
    scanLine = Color(0xFF2FD6A6),
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFFB9C0FF)
)

private val DarkBrand = ScanProColors(
    gradientStart = Color(0xFF4457E6),
    gradientEnd = Color(0xFF7D6CF2),
    onGradient = Color(0xFFFFFFFF),
    onGradientMuted = Color(0xFFD9DDFF),
    scanLine = Color(0xFF3DE3B2),
    paper = Color(0xFFF3F4FA),
    ink = Color(0xFF8E97E8)
)

val LocalScanProColors = staticCompositionLocalOf { LightBrand }

/** Shortcut to the brand colours next to [MaterialTheme.colorScheme]. */
object ScanProTheme {
    val colors: ScanProColors
        @Composable @ReadOnlyComposable get() = LocalScanProColors.current
}

// ---------------------------------------------------------------------------------------------
// Material colour schemes
// ---------------------------------------------------------------------------------------------

private val ScanProLight = lightColorScheme(
    primary = Color(0xFF3346D3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDFE2FF),
    onPrimaryContainer = Color(0xFF0A1472),
    inversePrimary = Color(0xFFB9C0FF),
    secondary = Color(0xFF0B7A68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6F4E7),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFF8F5300),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2E1800),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF171A26),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF171A26),
    surfaceVariant = Color(0xFFE3E6F0),
    onSurfaceVariant = Color(0xFF4B5162),
    surfaceTint = Color(0xFF3346D3),
    inverseSurface = Color(0xFF2C2F3B),
    inverseOnSurface = Color(0xFFEEF0F7),
    outline = Color(0xFF767C8E),
    outlineVariant = Color(0xFFD3D7E2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF7F8FC),
    surfaceDim = Color(0xFFD7D9E2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFEBEDF5),
    surfaceContainerHigh = Color(0xFFE5E8F0),
    surfaceContainerHighest = Color(0xFFDFE2EB)
)

private val ScanProDark = darkColorScheme(
    primary = Color(0xFFB9C0FF),
    onPrimary = Color(0xFF0E1A8C),
    primaryContainer = Color(0xFF2B3CBB),
    onPrimaryContainer = Color(0xFFDFE2FF),
    inversePrimary = Color(0xFF3346D3),
    secondary = Color(0xFF5CDDBF),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFF7EF4D6),
    tertiary = Color(0xFFFFB95C),
    onTertiary = Color(0xFF462A00),
    tertiaryContainer = Color(0xFF653E00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1118),
    onBackground = Color(0xFFE3E5EF),
    surface = Color(0xFF0F1118),
    onSurface = Color(0xFFE3E5EF),
    surfaceVariant = Color(0xFF2A2E3A),
    onSurfaceVariant = Color(0xFFC0C5D3),
    surfaceTint = Color(0xFFB9C0FF),
    inverseSurface = Color(0xFFE3E5EF),
    inverseOnSurface = Color(0xFF2C2F3B),
    outline = Color(0xFF8A8F9E),
    outlineVariant = Color(0xFF3E4354),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF363945),
    surfaceDim = Color(0xFF0F1118),
    surfaceContainerLowest = Color(0xFF0A0C12),
    surfaceContainerLow = Color(0xFF171A22),
    surfaceContainer = Color(0xFF1C1F28),
    surfaceContainerHigh = Color(0xFF262933),
    surfaceContainerHighest = Color(0xFF31343E)
)

// ---------------------------------------------------------------------------------------------
// Shape and type
// ---------------------------------------------------------------------------------------------

private val ScanProShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Manrope, one variable file covering every weight. Android 8 and newer honour the weight axis; older
 * versions get the file's default instance, which is still a clean geometric sans.
 */
private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal)
)

val ManropeFamily = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold)
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = ManropeFamily,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp
)

private val ScanProTypography = Typography(
    displayLarge = style(56, 62, FontWeight.ExtraBold, -1.5f),
    displayMedium = style(44, 50, FontWeight.ExtraBold, -1.2f),
    displaySmall = style(36, 42, FontWeight.ExtraBold, -0.9f),
    headlineLarge = style(32, 38, FontWeight.ExtraBold, -0.8f),
    headlineMedium = style(28, 34, FontWeight.ExtraBold, -0.6f),
    headlineSmall = style(24, 30, FontWeight.Bold, -0.4f),
    titleLarge = style(22, 28, FontWeight.Bold, -0.3f),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0f),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0f),
    bodyLarge = style(16, 24, FontWeight.Normal, 0f),
    bodyMedium = style(14, 20, FontWeight.Normal, 0f),
    bodySmall = style(12, 16, FontWeight.Normal, 0.1f),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.3f),
    labelSmall = style(11, 14, FontWeight.SemiBold, 0.4f)
)

// ---------------------------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------------------------

/** Brand colours for a wallpaper-derived scheme: the gradient runs from its primary to its tertiary. */
private fun dynamicBrand(scheme: ColorScheme, dark: Boolean): ScanProColors {
    val base = if (dark) DarkBrand else LightBrand
    return base.copy(
        gradientStart = if (dark) scheme.primaryContainer else scheme.primary,
        gradientEnd = if (dark) scheme.tertiaryContainer else scheme.tertiary,
        onGradient = if (dark) scheme.onPrimaryContainer else scheme.onPrimary,
        onGradientMuted = (if (dark) scheme.onPrimaryContainer else scheme.onPrimary).copy(alpha = 0.78f)
    )
}

@Composable
fun ScanProTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkSystem = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkSystem
    }
    val dynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> ScanProDark
        else -> ScanProLight
    }
    val brand = when {
        dynamic -> dynamicBrand(colors, dark)
        dark -> DarkBrand
        else -> LightBrand
    }
    CompositionLocalProvider(LocalScanProColors provides brand) {
        MaterialTheme(
            colorScheme = colors,
            typography = ScanProTypography,
            shapes = ScanProShapes,
            content = content
        )
    }
}
