package com.example.scanpro.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.data.ThemeMode

// ScanPro 2.1 "day & night" palette, taken from the approved design (refined Android concept).
private val ScanProLight = lightColorScheme(
    primary = Color(0xFF4D53D7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9ECFF),
    onPrimaryContainer = Color(0xFF4248BD),
    secondary = Color(0xFF087B64),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2F5ED),
    onSecondaryContainer = Color(0xFF12664F),
    tertiary = Color(0xFF94601D),
    tertiaryContainer = Color(0xFFFBF0DF),
    onTertiaryContainer = Color(0xFF94601D),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF19263B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF19263B),
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = Color(0xFF5E6D82),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF1F8),
    outline = Color(0xFFDFE6EF),
    outlineVariant = Color(0xFFDFE6EF)
)

private val ScanProDark = darkColorScheme(
    primary = Color(0xFFB1BDFF),
    onPrimary = Color(0xFF1C2856),
    primaryContainer = Color(0xFF2C3A61),
    onPrimaryContainer = Color(0xFFC8D2FF),
    secondary = Color(0xFF83DABB),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF1A3D35),
    onSecondaryContainer = Color(0xFF9BE4C9),
    tertiary = Color(0xFFE6BE7B),
    tertiaryContainer = Color(0xFF3F3427),
    onTertiaryContainer = Color(0xFFE6BE7B),
    background = Color(0xFF121A27),
    onBackground = Color(0xFFECF1FA),
    surface = Color(0xFF1D2838),
    onSurface = Color(0xFFECF1FA),
    surfaceVariant = Color(0xFF273448),
    onSurfaceVariant = Color(0xFFADBBCF),
    surfaceContainer = Color(0xFF1D2838),
    surfaceContainerHigh = Color(0xFF273448),
    outline = Color(0xFF344259),
    outlineVariant = Color(0xFF344259)
)

// Corner radii from the design: chips ~10dp, cards ~15dp, hero/sheets ~20dp.
private val ScanProShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

private val ScanProTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

/**
 * [dynamicColor] defaults to false so the app keeps the designed ScanPro palette. Turn it on to
 * follow the user's wallpaper colours on Android 12 and newer.
 */
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
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !dark -> dynamicLightColorScheme(context)
        dark -> ScanProDark
        else -> ScanProLight
    }
    MaterialTheme(
        colorScheme = colors,
        typography = ScanProTypography,
        shapes = ScanProShapes,
        content = content
    )
}
