package com.example.scanpro.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import androidx.compose.ui.util.lerp
import com.example.scanpro.core.designsystem.AuroraBackground
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private class WelcomePage(
    val icon: ImageVector,
    val tone: TileTone,
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    val points: List<Triple<ImageVector, Int, TileTone>>
)

private val pages = listOf(
    WelcomePage(
        Icons.Rounded.Shield,
        TileTone.Teal,
        R.string.ui_welcome_privacy_eyebrow,
        R.string.ui_welcome_privacy_title,
        R.string.ui_welcome_privacy_body,
        listOf(
            Triple(Icons.Rounded.NoAccounts, R.string.ui_welcome_privacy_point_account, TileTone.Indigo),
            Triple(Icons.Rounded.CloudOff, R.string.ui_welcome_privacy_point_offline, TileTone.Teal),
            Triple(Icons.Rounded.MoneyOff, R.string.ui_welcome_privacy_point_ads, TileTone.Amber)
        )
    ),
    WelcomePage(
        Icons.Rounded.DocumentScanner,
        TileTone.Indigo,
        R.string.ui_welcome_scan_eyebrow,
        R.string.ui_welcome_scan_title,
        R.string.ui_welcome_scan_body,
        listOf(
            Triple(Icons.Rounded.CameraAlt, R.string.ui_welcome_scan_point_modes, TileTone.Indigo),
            Triple(Icons.Rounded.AutoFixHigh, R.string.ui_welcome_scan_point_cleanup, TileTone.Violet),
            Triple(Icons.Rounded.QrCodeScanner, R.string.ui_welcome_scan_point_codes, TileTone.Sky)
        )
    ),
    WelcomePage(
        Icons.Rounded.PictureAsPdf,
        TileTone.Coral,
        R.string.ui_welcome_more_eyebrow,
        R.string.ui_welcome_more_title,
        R.string.ui_welcome_more_body,
        listOf(
            Triple(Icons.Rounded.Search, R.string.ui_welcome_more_point_search, TileTone.Indigo),
            Triple(Icons.Rounded.Description, R.string.ui_welcome_more_point_export, TileTone.Coral),
            Triple(Icons.Rounded.Draw, R.string.ui_welcome_more_point_sign, TileTone.Violet),
            Triple(Icons.Rounded.Badge, R.string.ui_welcome_more_point_contacts, TileTone.Teal)
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
    val motion = rememberMotionEnabled()
    // First appearance: the header, pager, dots and buttons rise in one after another. With animations
    // off in the system settings everything is simply there.
    var entered by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { entered = true }
    @Composable
    fun Modifier.enter(order: Int): Modifier {
        val progress by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(durationMillis = 520, delayMillis = 90 + order * 110, easing = FastOutSlowInEasing),
            label = "enter$order"
        )
        return graphicsLayer { alpha = progress; translationY = (1f - progress) * 36.dp.toPx() }
    }

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
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp).enter(0),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BrandLockup(Modifier.weight(1f))
                    AnimatedContent(
                        targetState = last,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "skip"
                    ) { isLast ->
                        if (!isLast) TextButton(onClick = onExplore) { Text(stringResource(R.string.ui_welcome_skip)) } else Spacer(Modifier.width(64.dp))
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).enter(1),
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
                        offset = offset,
                        current = index == pagerState.currentPage && pagerState.currentPageOffsetFraction.absoluteValue < 0.5f
                    )
                }
                PageIndicator(count = pages.size, current = pagerState.currentPage, Modifier.padding(vertical = 14.dp).enter(2))
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).enter(3)) {
                    GradientButton(
                        text = stringResource(if (last) R.string.ui_welcome_scan_first_page else R.string.common_continue),
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
                            if (isLast) TextButton(onClick = onExplore) { Text(stringResource(R.string.ui_welcome_look_around)) }
                            else Text(
                                stringResource(R.string.ui_welcome_takes_seconds),
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
    val description = stringResource(R.string.ui_welcome_page_of, current + 1, count)
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
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
private fun PageContent(page: WelcomePage, modifier: Modifier = Modifier, offset: Float, current: Boolean) {
    val motion = rememberMotionEnabled()
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
                .graphicsLayer { translationX = offset * 40f },
            current = current
        )
        Spacer(Modifier.height(26.dp))
        Text(
            stringResource(page.eyebrow).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp),
            color = page.tone.content(),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(page.body),
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
                page.points.forEachIndexed { index, (icon, text, tone) ->
                    // Rows step in one after another as the page settles into view.
                    val reveal by animateFloatAsState(
                        targetValue = if (current || !motion) 1f else 0f,
                        animationSpec = if (current) tween(380, delayMillis = 140 + index * 70, easing = FastOutSlowInEasing) else tween(120),
                        label = "point$index"
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = reveal; translationX = (1f - reveal) * 24.dp.toPx() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(icon, tone, size = 38.dp, corner = 12.dp, iconSize = 20.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
private fun HeroIllustration(icon: ImageVector, tone: TileTone, modifier: Modifier = Modifier, current: Boolean = true) {
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
    val float by sweep.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val motion = rememberMotionEnabled()
    val beamPosition = if (motion) beam else 0.5f
    val haloScale = if (motion) glow else 1f
    val bob = if (motion) float else 0f
    // Lock-on: as a page comes into view the brackets start loose and white, then snap tight and mint,
    // the way the scanner's own outline does when it finds a page.
    val lock by animateFloatAsState(
        targetValue = if (current || !motion) 1f else 0f,
        animationSpec = if (current) tween(620, delayMillis = 160, easing = FastOutSlowInEasing) else tween(150),
        label = "lock"
    )
    val bracketColor = androidx.compose.ui.graphics.lerp(scheme.onSurface.copy(alpha = 0.35f), brand.scanLine, lock)
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
        // Two sheets behind, fanned out; the whole stack drifts gently, the back sheets a little more.
        Box(Modifier.graphicsLayer { translationY = bob * 7.dp.toPx(); rotationZ = bob * 1.2f }) {
            Sheet(sheetWidth, sheetHeight, rotation = -9f, offsetX = (-22).dp, offsetY = 10.dp, alpha = 0.55f, lines = false)
        }
        Box(Modifier.graphicsLayer { translationY = -bob * 5.dp.toPx(); rotationZ = -bob * 0.8f }) {
            Sheet(sheetWidth, sheetHeight, rotation = 6f, offsetX = 18.dp, offsetY = 6.dp, alpha = 0.75f, lines = false)
        }
        // Front sheet with text lines and the beam.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer { translationY = bob * 3.dp.toPx() }) {
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
        // Brackets around the whole stack; loose until the page locks on.
        Canvas(
            Modifier
                .size(sheetWidth + 76.dp, sheetHeight + 44.dp)
                .graphicsLayer { val s = 1f + (1f - lock) * 0.14f; scaleX = s; scaleY = s }
        ) {
            val l = 28f + (1f - lock) * 10f
            val stroke = 6f
            val c = bracketColor
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
