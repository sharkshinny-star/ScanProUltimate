package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberUpdatedState

/*
 * Brand building blocks shared by the welcome, lock, home and settings screens: the mark, the wordmark,
 * the ambient "aurora" backdrop, the gradient call-to-action and a few layout helpers. Everything is drawn
 * with Compose, so it stays crisp at any size and follows the active colours.
 */

/**
 * The ScanPro mark: a sheet of paper inside scanner brackets with a mint scan line across it, on the
 * brand gradient. Mirrors the launcher icon so the app looks the same from the home screen onwards.
 */
@Composable
fun BrandMark(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    /** Draw the mark on a transparent background (for use on the gradient itself). */
    bare: Boolean = false
) {
    val brand = ScanProTheme.colors
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = "ScanPro" }
    ) {
        val s = this.size.minDimension
        if (!bare) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(brand.gradientStart, brand.gradientEnd),
                    start = Offset.Zero,
                    end = Offset(s, s)
                ),
                cornerRadius = CornerRadius(s * 0.26f)
            )
        }
        drawScanGlyph(s, paper = Color.White, ink = brand.gradientStart.copy(alpha = 0.55f), line = brand.scanLine, bracket = Color.White.copy(alpha = 0.9f))
    }
}

/** The paper-and-brackets glyph on its own, sized to a square of [s] px. Shared by the mark and illustrations. */
internal fun DrawScope.drawScanGlyph(s: Float, paper: Color, ink: Color, line: Color, bracket: Color) {
    val u = s / 108f
    // Sheet with a folded corner.
    val sheet = Path().apply {
        moveTo(36f * u, 26f * u)
        lineTo(62f * u, 26f * u)
        lineTo(74f * u, 38f * u)
        lineTo(74f * u, 78f * u)
        arcTo(androidx.compose.ui.geometry.Rect(64f * u, 72f * u, 74f * u, 82f * u), 0f, 90f, false)
        lineTo(39f * u, 82f * u)
        arcTo(androidx.compose.ui.geometry.Rect(34f * u, 72f * u, 44f * u, 82f * u), 90f, 90f, false)
        lineTo(34f * u, 31f * u)
        arcTo(androidx.compose.ui.geometry.Rect(34f * u, 26f * u, 44f * u, 36f * u), 180f, 90f, false)
        close()
    }
    drawPath(sheet, paper)
    val fold = Path().apply {
        moveTo(62f * u, 26f * u)
        lineTo(62f * u, 38f * u)
        lineTo(74f * u, 38f * u)
        close()
    }
    drawPath(fold, ink.copy(alpha = ink.alpha * 0.6f))
    // Text lines.
    val lineStroke = Stroke(width = 4f * u, cap = StrokeCap.Round)
    drawLine(ink, Offset(43f * u, 46f * u), Offset(58f * u, 46f * u), lineStroke.width, StrokeCap.Round)
    drawLine(ink, Offset(43f * u, 65f * u), Offset(63f * u, 65f * u), lineStroke.width, StrokeCap.Round)
    // Scan line, running past the sheet on both sides.
    drawLine(line, Offset(24f * u, 55.5f * u), Offset(84f * u, 55.5f * u), 5f * u, StrokeCap.Round)
    // Brackets.
    val bw = 4.5f * u
    fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
        val p = Path().apply {
            moveTo((x + dx * 12f) * u, y * u)
            lineTo((x + dx * 4f) * u, y * u)
            quadraticTo(x * u, y * u, x * u, (y + dy * 4f) * u)
            lineTo(x * u, (y + dy * 12f) * u)
        }
        drawPath(p, bracket, style = Stroke(bw, cap = StrokeCap.Round))
    }
    bracket(20f, 20f, 1f, 1f)
    bracket(88f, 20f, -1f, 1f)
    bracket(20f, 88f, 1f, -1f)
    bracket(88f, 88f, -1f, -1f)
}

/** "ScanPro" set in the brand weight. */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        "ScanPro",
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.6).sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

/** Mark and wordmark side by side. */
@Composable
fun BrandLockup(modifier: Modifier = Modifier, markSize: Dp = 28.dp, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = markSize)
        Spacer(Modifier.width(10.dp))
        BrandWordmark(color = color)
    }
}

/**
 * False when the person has turned animations off in developer or accessibility settings (animator
 * scale 0), so decorative loops can stay still instead of fighting that choice and burning battery.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

/**
 * The ambient backdrop behind the welcome and lock screens: the theme background with two soft colour
 * fields that drift slowly. Drawn behind the system bars so the colour runs edge to edge.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, animated: Boolean = rememberMotionEnabled(), content: @Composable () -> Unit = {}) {
    val brand = ScanProTheme.colors
    val background = MaterialTheme.colorScheme.background
    val dark = MaterialTheme.colorScheme.onBackground.luminanceApprox() > 0.5f
    val strength = if (dark) 0.34f else 0.22f
    val transition = rememberInfiniteTransition(label = "aurora")
    val animatedDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    val drift by rememberUpdatedState(if (animated) animatedDrift else 0.5f)
    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .drawWithCache {
                val w = size.width
                val h = size.height
                onDrawBehind {
                    val d = drift
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(brand.gradientEnd.copy(alpha = strength), Color.Transparent),
                            center = Offset(w * (0.15f + 0.2f * d), h * (0.08f + 0.06f * d)),
                            radius = w * 0.75f
                        ),
                        radius = w * 0.75f,
                        center = Offset(w * (0.15f + 0.2f * d), h * (0.08f + 0.06f * d))
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(brand.scanLine.copy(alpha = strength * 0.65f), Color.Transparent),
                            center = Offset(w * (0.95f - 0.15f * d), h * (0.42f + 0.08f * d)),
                            radius = w * 0.6f
                        ),
                        radius = w * 0.6f,
                        center = Offset(w * (0.95f - 0.15f * d), h * (0.42f + 0.08f * d))
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(brand.gradientStart.copy(alpha = strength * 0.8f), Color.Transparent),
                            center = Offset(w * (0.3f + 0.1f * d), h * 0.95f),
                            radius = w * 0.7f
                        ),
                        radius = w * 0.7f,
                        center = Offset(w * (0.3f + 0.1f * d), h * 0.95f)
                    )
                }
            }
    ) { content() }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** The primary call-to-action: full-width, on the brand gradient. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val brand = ScanProTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = brand.onGradient,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = brand.onGradient.copy(alpha = 0.6f)
        ),
        modifier = modifier.height(56.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        if (enabled) listOf(brand.gradientStart, brand.gradientEnd)
                        else listOf(brand.gradientStart.copy(alpha = 0.5f), brand.gradientEnd.copy(alpha = 0.5f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A section title with an optional trailing action, used above lists and grids. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Small uppercase label used above groups (settings sections, home eyebrow). */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
        color = color,
        fontWeight = FontWeight.Bold
    )
}

/**
 * A tone for icon tiles. Each has a container and a foreground that read in both light and dark, so a
 * grid of shortcuts can give every action its own hue without any of them clashing with the theme.
 */
@Immutable
class TileTone(val lightContainer: Color, val lightContent: Color, val darkContainer: Color, val darkContent: Color) {
    @Composable
    fun container(): Color = if (isDarkTheme()) darkContainer else lightContainer

    @Composable
    fun content(): Color = if (isDarkTheme()) darkContent else lightContent

    companion object {
        val Indigo = TileTone(Color(0xFFE1E4FF), Color(0xFF2F40C8), Color(0xFF2B3474), Color(0xFFC3C9FF))
        val Teal = TileTone(Color(0xFFCCF3E8), Color(0xFF066B5B), Color(0xFF14463F), Color(0xFF7CE8CD))
        val Amber = TileTone(Color(0xFFFFE6BF), Color(0xFF8A4F00), Color(0xFF4E3410), Color(0xFFFFC978))
        val Rose = TileTone(Color(0xFFFFDCE3), Color(0xFFA8233F), Color(0xFF55202B), Color(0xFFFFADBE))
        val Violet = TileTone(Color(0xFFEBDDFF), Color(0xFF6A3BC9), Color(0xFF3B2A63), Color(0xFFD4BBFF))
        val Sky = TileTone(Color(0xFFD5ECFF), Color(0xFF0B5A9C), Color(0xFF173A56), Color(0xFF9CD1FF))
        val Coral = TileTone(Color(0xFFFFE1D6), Color(0xFFB03A1A), Color(0xFF552619), Color(0xFFFFB59D))
        val Slate = TileTone(Color(0xFFE4E7EF), Color(0xFF3A4255), Color(0xFF353A47), Color(0xFFC8CDDA))
    }
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f

/** A rounded square holding an icon on a toned background; the building block of tiles and settings rows. */
@Composable
fun IconTile(
    icon: ImageVector,
    tone: TileTone,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    corner: Dp = 14.dp,
    iconSize: Dp = 22.dp
) {
    Surface(shape = RoundedCornerShape(corner), color = tone.container(), modifier = modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(iconSize))
        }
    }
}

/** A hairline between grouped rows that stops short of the leading icon column. */
@Composable
fun InsetDivider(startIndent: Dp = 68.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/** Standard vertical rhythm between stacked content blocks. */
val SectionGap = 20.dp
