package com.example.scanpro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.NoAccounts
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.scanpro.core.designsystem.AuroraBackground
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private class WelcomePage(
    val icon: ImageVector,
    val tone: TileTone,
    val eyebrow: String,
    val title: String,
    val body: String,
    val points: List<Triple<ImageVector, String, TileTone>>
)

private val pages = listOf(
    WelcomePage(
        Icons.Rounded.Shield,
        TileTone.Teal,
        "Private by design",
        "Your documents stay on your phone",
        "ScanPro works without an account. Nothing you scan is uploaded unless you choose to share it or save it somewhere.",
        listOf(
            Triple(Icons.Rounded.NoAccounts, "No account or sign-in", TileTone.Indigo),
            Triple(Icons.Rounded.CloudOff, "Scanning, OCR and PDF tools run on this phone", TileTone.Teal),
            Triple(Icons.Rounded.MoneyOff, "No ads, and no watermark on your scans", TileTone.Amber)
        )
    ),
    WelcomePage(
        Icons.Rounded.DocumentScanner,
        TileTone.Indigo,
        "Smart capture",
        "Scan like a pro",
        "Point the camera at a page and hold steady. ScanPro finds the edges, straightens the page and captures it for you.",
        listOf(
            Triple(Icons.Rounded.CameraAlt, "Batch mode for many pages, ID cards and books", TileTone.Indigo),
            Triple(Icons.Rounded.AutoFixHigh, "Clean up shadows and fingers, flatten curved pages", TileTone.Violet),
            Triple(Icons.Rounded.QrCodeScanner, "Read QR codes and barcodes", TileTone.Sky)
        )
    ),
    WelcomePage(
        Icons.Rounded.PictureAsPdf,
        TileTone.Coral,
        "Searchable PDFs",
        "Do more with every page",
        "Every scan becomes a searchable PDF, ready to share, sign or export.",
        listOf(
            Triple(Icons.Rounded.Search, "Find any word you ever scanned", TileTone.Indigo),
            Triple(Icons.Rounded.Description, "Export to PDF, Word, text or images", TileTone.Coral),
            Triple(Icons.Rounded.Draw, "Sign, redact, watermark and password-protect", TileTone.Violet),
            Triple(Icons.Rounded.Badge, "Business cards straight to your contacts", TileTone.Teal)
        )
    )
)

/**
 * Shown once on first launch: the privacy promise first, then what the app can do. Drawn edge to edge
 * over the ambient brand backdrop, with the content stepping aside for the system bars.
 */
@Composable
fun WelcomeScreen(onStartScanning: () -> Unit, onExplore: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    AuroraBackground(
        Modifier
            .fillMaxSize()
            // Taps on empty parts of the welcome must not reach the app drawn underneath it.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandLockup(Modifier.weight(1f))
                    AnimatedContent(
                        targetState = last,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "skip"
                    ) { isLast ->
                        if (!isLast) TextButton(onClick = onExplore) { Text("Skip") } else Spacer(Modifier.width(64.dp))
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1
                ) { index ->
                    // Pages slide with a touch of depth: the one leaving fades and shrinks a little.
                    val offset = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    PageContent(
                        page = pages[index],
                        modifier = Modifier.graphicsLayer {
                            alpha = lerp(1f, 0.35f, offset)
                            val scale = lerp(1f, 0.94f, offset)
                            scaleX = scale
                            scaleY = scale
                        },
                        offset = offset
                    )
                }
                PageIndicator(count = pages.size, current = pagerState.currentPage, Modifier.padding(vertical = 14.dp))
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    GradientButton(
                        text = if (last) "Scan your first page" else "Continue",
                        icon = if (last) Icons.Rounded.CameraAlt else Icons.AutoMirrored.Rounded.ArrowForward,
                        onClick = {
                            if (last) onStartScanning()
                            else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = last,
                            transitionSpec = { fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                            label = "explore"
                        ) { isLast ->
                            if (isLast) TextButton(onClick = onExplore) { Text("Look around first") }
                            else Text(
                                "Takes about 20 seconds",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Worm indicator: the active dot stretches into a pill, the others stay as dots. */
@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 26.dp else 8.dp, spring(stiffness = 500f), label = "dot")
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(width)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PageContent(page: WelcomePage, modifier: Modifier = Modifier, offset: Float) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))
        HeroIllustration(
            icon = page.icon,
            tone = page.tone,
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp)
                // Parallax: the illustration moves a little slower than the text as pages slide.
                .graphicsLayer { translationX = offset * 40f }
        )
        Spacer(Modifier.height(26.dp))
        Text(
            page.eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp),
            color = page.tone.content(),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(22.dp))
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                page.points.forEach { (icon, text, tone) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon, tone, size = 38.dp, corner = 12.dp, iconSize = 20.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * A stack of three sheets with a scan beam sweeping down the front one, and a toned badge holding the
 * page's icon. Drawn in Compose so it follows the theme and scales to any width.
 */
@Composable
private fun HeroIllustration(icon: ImageVector, tone: TileTone, modifier: Modifier = Modifier) {
    val brand = ScanProTheme.colors
    val scheme = MaterialTheme.colorScheme
    val sweep = rememberInfiniteTransition(label = "scan")
    val beam by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beam"
    )
    val glow by sweep.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val motion = com.example.scanpro.core.designsystem.rememberMotionEnabled()
    val beamPosition = if (motion) beam else 0.5f
    val haloScale = if (motion) glow else 1f
    val badgeContainer = tone.container()
    val badgeContent = tone.content()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val sheetWidth = minOf(maxWidth * 0.46f, 170.dp)
        val sheetHeight = sheetWidth * 1.3f
        // Soft halo behind the stack.
        Box(
            Modifier
                .size(sheetWidth * 1.9f)
                .graphicsLayer { scaleX = haloScale; scaleY = haloScale }
                .background(
                    Brush.radialGradient(listOf(brand.gradientEnd.copy(alpha = 0.22f), Color.Transparent)),
                    CircleShape
                )
        )
        // Two sheets behind, fanned out.
        Sheet(sheetWidth, sheetHeight, rotation = -9f, offsetX = (-22).dp, offsetY = 10.dp, alpha = 0.55f, lines = false)
        Sheet(sheetWidth, sheetHeight, rotation = 6f, offsetX = 18.dp, offsetY = 6.dp, alpha = 0.75f, lines = false)
        // Front sheet with text lines and the beam.
        Box(contentAlignment = Alignment.Center) {
            Sheet(sheetWidth, sheetHeight, rotation = 0f, offsetX = 0.dp, offsetY = 0.dp, alpha = 1f, lines = true)
            Canvas(Modifier.size(sheetWidth + 36.dp, sheetHeight)) {
                val y = size.height * (0.12f + 0.76f * beamPosition)
                // Wake of the beam: a faint band fading upwards.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, brand.scanLine.copy(alpha = 0.22f)),
                        startY = y - 70f,
                        endY = y
                    ),
                    topLeft = Offset(18f, y - 70f),
                    size = Size(size.width - 36f, 70f)
                )
                drawLine(brand.scanLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 5f, cap = StrokeCap.Round)
                drawCircle(brand.scanLine, radius = 7f, center = Offset(0f, y))
                drawCircle(brand.scanLine, radius = 7f, center = Offset(size.width, y))
            }
        }
        // Brackets around the whole stack.
        Canvas(Modifier.size(sheetWidth + 76.dp, sheetHeight + 44.dp)) {
            val l = 28f
            val stroke = 6f
            val c = scheme.primary.copy(alpha = 0.55f)
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(c, Offset(x, y), Offset(x + dx * l, y), stroke, StrokeCap.Round)
                drawLine(c, Offset(x, y), Offset(x, y + dy * l), stroke, StrokeCap.Round)
            }
            corner(0f, 0f, 1f, 1f)
            corner(size.width, 0f, -1f, 1f)
            corner(0f, size.height, 1f, -1f)
            corner(size.width, size.height, -1f, -1f)
        }
        // Icon badge, bottom right.
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = badgeContainer,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = maxWidth * 0.12f, bottom = 6.dp)
                .size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = badgeContent, modifier = Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun Sheet(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    rotation: Float,
    offsetX: androidx.compose.ui.unit.Dp,
    offsetY: androidx.compose.ui.unit.Dp,
    alpha: Float,
    lines: Boolean
) {
    val brand = ScanProTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .padding(start = if (offsetX > 0.dp) offsetX * 2 else 0.dp, end = if (offsetX < 0.dp) -offsetX * 2 else 0.dp, top = offsetY * 2)
            .graphicsLayer { rotationZ = rotation; this.alpha = alpha }
            .shadow(14.dp, shape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .background(brand.paper)
            .size(width, height)
    ) {
        if (lines) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = size.width * 0.14f
                val ink = brand.ink
                val lineHeight = size.height * 0.035f
                // A "heading" block followed by text lines of varying length.
                drawRoundRect(ink, Offset(inset, size.height * 0.14f), Size(size.width * 0.42f, lineHeight * 1.6f), CornerRadius(lineHeight))
                val widths = listOf(0.72f, 0.66f, 0.70f, 0.5f, 0.68f, 0.6f, 0.4f)
                widths.forEachIndexed { i, w ->
                    val y = size.height * (0.30f + i * 0.085f)
                    drawRoundRect(ink.copy(alpha = ink.alpha * 0.6f), Offset(inset, y), Size(size.width * w, lineHeight), CornerRadius(lineHeight))
                }
                // Folded corner.
                val fold = size.width * 0.18f
                drawRect(brand.paper, Offset(size.width - fold, 0f), Size(fold, fold))
                drawRoundRect(ink.copy(alpha = 0.35f), Offset(size.width - fold, 0f), Size(fold, fold), CornerRadius(4f))
            }
        }
    }
}
