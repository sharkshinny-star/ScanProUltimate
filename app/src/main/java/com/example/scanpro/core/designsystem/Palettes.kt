package com.example.scanpro.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Colour themes besides the original Indigo (which lives in Theme.kt).
 *
 * The Material schemes below are generated, not hand-tuned: Material Color Utilities' "fidelity" scheme
 * (2021 spec, standard contrast) from one seed colour each, so every role pairing keeps Material's
 * contrast guarantees. Seeds: Ocean #1A63D6, Emerald #05785C, Sunset #D24A1C, Berry #B02A86.
 * To change a theme, regenerate its scheme from a new seed rather than editing single roles.
 *
 * The brand gradients were picked by hand so white text on either end keeps a contrast of at least
 * 4.5:1 (WCAG AA), as on the Indigo gradient. The mint scan line is the same in every theme: the scanner
 * uses it to mean "page found", and that meaning should not change with the colour theme.
 */

internal val OceanLight = lightColorScheme(
    primary = Color(0xFF004BAE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1A63D6),
    onPrimaryContainer = Color(0xFFE2E8FF),
    inversePrimary = Color(0xFFB0C6FF),
    secondary = Color(0xFF4B5E8A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB8CBFE),
    onSecondaryContainer = Color(0xFF425581),
    tertiary = Color(0xFF8A3700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB14800),
    onTertiaryContainer = Color(0xFFFFE3D8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF191B22),
    surface = Color(0xFFFAF8FF),
    onSurface = Color(0xFF191B22),
    surfaceVariant = Color(0xFFDEE2F2),
    onSurfaceVariant = Color(0xFF424653),
    surfaceTint = Color(0xFF0058C9),
    inverseSurface = Color(0xFF2E3038),
    inverseOnSurface = Color(0xFFEFF0FA),
    outline = Color(0xFF737785),
    outlineVariant = Color(0xFFC2C6D6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAF8FF),
    surfaceDim = Color(0xFFD9D9E3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3FD),
    surfaceContainer = Color(0xFFEDEDF7),
    surfaceContainerHigh = Color(0xFFE7E7F2),
    surfaceContainerHighest = Color(0xFFE1E2EC)
)

internal val OceanDark = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    onPrimary = Color(0xFF002D6E),
    primaryContainer = Color(0xFF1A63D6),
    onPrimaryContainer = Color(0xFFE2E8FF),
    inversePrimary = Color(0xFF0058C9),
    secondary = Color(0xFFB3C6F9),
    onSecondary = Color(0xFF1B2F59),
    secondaryContainer = Color(0xFF334671),
    onSecondaryContainer = Color(0xFFA1B4E6),
    tertiary = Color(0xFFFFB693),
    onTertiary = Color(0xFF561F00),
    tertiaryContainer = Color(0xFFB14800),
    onTertiaryContainer = Color(0xFFFFE3D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF11131A),
    onBackground = Color(0xFFE1E2EC),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE1E2EC),
    surfaceVariant = Color(0xFF424653),
    onSurfaceVariant = Color(0xFFC2C6D6),
    surfaceTint = Color(0xFFB0C6FF),
    inverseSurface = Color(0xFFE1E2EC),
    inverseOnSurface = Color(0xFF2E3038),
    outline = Color(0xFF8C909F),
    outlineVariant = Color(0xFF424653),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF373941),
    surfaceDim = Color(0xFF11131A),
    surfaceContainerLowest = Color(0xFF0C0E15),
    surfaceContainerLow = Color(0xFF191B22),
    surfaceContainer = Color(0xFF1D1F27),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C)
)

internal val EmeraldLight = lightColorScheme(
    primary = Color(0xFF005D46),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF05785C),
    onPrimaryContainer = Color(0xFFA0FDD9),
    inversePrimary = Color(0xFF7CD8B6),
    secondary = Color(0xFF446558),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6EBDA),
    onSecondaryContainer = Color(0xFF4A6B5E),
    tertiary = Color(0xFF853B32),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA35248),
    onTertiaryContainer = Color(0xFFFFE5E1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6FAF6),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF6FAF6),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFD9E5DE),
    onSurfaceVariant = Color(0xFF3E4944),
    surfaceTint = Color(0xFF006C52),
    inverseSurface = Color(0xFF2D312F),
    inverseOnSurface = Color(0xFFEEF2ED),
    outline = Color(0xFF6E7A74),
    outlineVariant = Color(0xFFBDC9C2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF6FAF6),
    surfaceDim = Color(0xFFD7DBD7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F0),
    surfaceContainer = Color(0xFFEBEFEB),
    surfaceContainerHigh = Color(0xFFE5E9E5),
    surfaceContainerHighest = Color(0xFFDFE4DF)
)

internal val EmeraldDark = darkColorScheme(
    primary = Color(0xFF7CD8B6),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF05785C),
    onPrimaryContainer = Color(0xFFA0FDD9),
    inversePrimary = Color(0xFF006C52),
    secondary = Color(0xFFAACEBE),
    onSecondary = Color(0xFF15362B),
    secondaryContainer = Color(0xFF2C4D41),
    onSecondaryContainer = Color(0xFF99BDAD),
    tertiary = Color(0xFFFFB4AA),
    onTertiary = Color(0xFF5A1B14),
    tertiaryContainer = Color(0xFFA35248),
    onTertiaryContainer = Color(0xFFFFE5E1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101412),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF3E4944),
    onSurfaceVariant = Color(0xFFBDC9C2),
    surfaceTint = Color(0xFF7CD8B6),
    inverseSurface = Color(0xFFDFE4DF),
    inverseOnSurface = Color(0xFF2D312F),
    outline = Color(0xFF88938D),
    outlineVariant = Color(0xFF3E4944),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353A38),
    surfaceDim = Color(0xFF101412),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF181D1A),
    surfaceContainer = Color(0xFF1C211E),
    surfaceContainerHigh = Color(0xFF262B28),
    surfaceContainerHighest = Color(0xFF313633)
)

internal val SunsetLight = lightColorScheme(
    primary = Color(0xFFAA3000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCD4719),
    onPrimaryContainer = Color(0xFFFFFBFF),
    inversePrimary = Color(0xFFFFB59F),
    secondary = Color(0xFF934932),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFC9E81),
    onSecondaryContainer = Color(0xFF76331E),
    tertiary = Color(0xFF005EA4),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF0077CE),
    onTertiaryContainer = Color(0xFFFDFCFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF261814),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF261814),
    surfaceVariant = Color(0xFFFFDBD1),
    onSurfaceVariant = Color(0xFF59413A),
    surfaceTint = Color(0xFFAE3100),
    inverseSurface = Color(0xFF3C2D28),
    inverseOnSurface = Color(0xFFFFEDE8),
    outline = Color(0xFF8D7168),
    outlineVariant = Color(0xFFE1BFB5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFEED5CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFFFE9E3),
    surfaceContainerHigh = Color(0xFFFDE3DC),
    surfaceContainerHighest = Color(0xFFF7DDD6)
)

internal val SunsetDark = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF5E1600),
    primaryContainer = Color(0xFFF46233),
    onPrimaryContainer = Color(0xFF160200),
    inversePrimary = Color(0xFFAE3100),
    secondary = Color(0xFFFFB59F),
    onSecondary = Color(0xFF591D09),
    secondaryContainer = Color(0xFF76321D),
    onSecondaryContainer = Color(0xFFFB9D80),
    tertiary = Color(0xFFA2C9FF),
    onTertiary = Color(0xFF00315B),
    tertiaryContainer = Color(0xFF3994EE),
    onTertiaryContainer = Color(0xFF000714),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1D100D),
    onBackground = Color(0xFFF7DDD6),
    surface = Color(0xFF1D100D),
    onSurface = Color(0xFFF7DDD6),
    surfaceVariant = Color(0xFF59413A),
    onSurfaceVariant = Color(0xFFE1BFB5),
    surfaceTint = Color(0xFFFFB59F),
    inverseSurface = Color(0xFFF7DDD6),
    inverseOnSurface = Color(0xFF3C2D28),
    outline = Color(0xFFA98A81),
    outlineVariant = Color(0xFF59413A),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF463531),
    surfaceDim = Color(0xFF1D100D),
    surfaceContainerLowest = Color(0xFF170B08),
    surfaceContainerLow = Color(0xFF261814),
    surfaceContainer = Color(0xFF2A1C18),
    surfaceContainerHigh = Color(0xFF352722),
    surfaceContainerHighest = Color(0xFF41312D)
)

internal val BerryLight = lightColorScheme(
    primary = Color(0xFF91036C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB02A86),
    onPrimaryContainer = Color(0xFFFFD6E9),
    inversePrimary = Color(0xFFFFAEDA),
    secondary = Color(0xFF8B4870),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDABD8),
    onSecondaryContainer = Color(0xFF7B3A62),
    tertiary = Color(0xFF275600),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF357100),
    onTertiaryContainer = Color(0xFFAEF47C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFFFF8F8),
    onBackground = Color(0xFF23181E),
    surface = Color(0xFFFFF8F8),
    onSurface = Color(0xFF23181E),
    surfaceVariant = Color(0xFFF8DBE7),
    onSurfaceVariant = Color(0xFF55414B),
    surfaceTint = Color(0xFFA92380),
    inverseSurface = Color(0xFF392D33),
    inverseOnSurface = Color(0xFFFFECF3),
    outline = Color(0xFF88717C),
    outlineVariant = Color(0xFFDABFCB),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFF8F8),
    surfaceDim = Color(0xFFE9D5DD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F5),
    surfaceContainer = Color(0xFFFDE9F1),
    surfaceContainerHigh = Color(0xFFF7E3EB),
    surfaceContainerHighest = Color(0xFFF2DDE5)
)

internal val BerryDark = darkColorScheme(
    primary = Color(0xFFFFAEDA),
    onPrimary = Color(0xFF610047),
    primaryContainer = Color(0xFFB02A86),
    onPrimaryContainer = Color(0xFFFFD6E9),
    inversePrimary = Color(0xFFA92380),
    secondary = Color(0xFFFFAEDA),
    onSecondary = Color(0xFF541A40),
    secondaryContainer = Color(0xFF6F3057),
    onSecondaryContainer = Color(0xFFED9CC9),
    tertiary = Color(0xFF95D965),
    onTertiary = Color(0xFF173800),
    tertiaryContainer = Color(0xFF357100),
    onTertiaryContainer = Color(0xFFAEF47C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1B1016),
    onBackground = Color(0xFFF2DDE5),
    surface = Color(0xFF1B1016),
    onSurface = Color(0xFFF2DDE5),
    surfaceVariant = Color(0xFF55414B),
    onSurfaceVariant = Color(0xFFDABFCB),
    surfaceTint = Color(0xFFFFAEDA),
    inverseSurface = Color(0xFFF2DDE5),
    inverseOnSurface = Color(0xFF392D33),
    outline = Color(0xFFA28A95),
    outlineVariant = Color(0xFF55414B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF43363C),
    surfaceDim = Color(0xFF1B1016),
    surfaceContainerLowest = Color(0xFF150B11),
    surfaceContainerLow = Color(0xFF23181E),
    surfaceContainer = Color(0xFF281C22),
    surfaceContainerHigh = Color(0xFF33272D),
    surfaceContainerHighest = Color(0xFF3E3137)
)

private val MintLine = Color(0xFF2FD6A6)
private val MintLineDark = Color(0xFF3DE3B2)

internal val OceanBrandLight = ScanProColors(
    gradientStart = Color(0xFF1556C9),
    gradientEnd = Color(0xFF0A78AB),
    onGradient = Color(0xFFFFFFFF),
    onGradientMuted = Color(0xFFD6E6FF),
    scanLine = MintLine,
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFFB0C6FF)
)
internal val OceanBrandDark = OceanBrandLight.copy(
    gradientStart = Color(0xFF1F63D6),
    gradientEnd = Color(0xFF0B7FB0),
    scanLine = MintLineDark,
    paper = Color(0xFFF3F5FA),
    ink = Color(0xFF7FA6F0)
)

internal val EmeraldBrandLight = ScanProColors(
    gradientStart = Color(0xFF04785B),
    gradientEnd = Color(0xFF0B7285),
    onGradient = Color(0xFFFFFFFF),
    onGradientMuted = Color(0xFFCFF3E6),
    scanLine = MintLine,
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFF8FD9BE)
)
internal val EmeraldBrandDark = EmeraldBrandLight.copy(
    gradientStart = Color(0xFF088466),
    gradientEnd = Color(0xFF0F7D91),
    scanLine = MintLineDark,
    paper = Color(0xFFF2F7F4),
    ink = Color(0xFF5FBF9E)
)

internal val SunsetBrandLight = ScanProColors(
    gradientStart = Color(0xFFC2410C),
    gradientEnd = Color(0xFFBE185D),
    onGradient = Color(0xFFFFFFFF),
    onGradientMuted = Color(0xFFFFE0D6),
    scanLine = MintLine,
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFFFFB59F)
)
internal val SunsetBrandDark = SunsetBrandLight.copy(
    gradientStart = Color(0xFFCF4A12),
    gradientEnd = Color(0xFFC81E66),
    scanLine = MintLineDark,
    paper = Color(0xFFFAF4F2),
    ink = Color(0xFFF0907A)
)

internal val BerryBrandLight = ScanProColors(
    gradientStart = Color(0xFFBE1F6E),
    gradientEnd = Color(0xFF7A2BD0),
    onGradient = Color(0xFFFFFFFF),
    onGradientMuted = Color(0xFFFFDDF0),
    scanLine = MintLine,
    paper = Color(0xFFFFFFFF),
    ink = Color(0xFFFFAEDA)
)
internal val BerryBrandDark = BerryBrandLight.copy(
    gradientStart = Color(0xFFC92A78),
    gradientEnd = Color(0xFF8536DB),
    scanLine = MintLineDark,
    paper = Color(0xFFF9F3F7),
    ink = Color(0xFFE58CBF)
)
