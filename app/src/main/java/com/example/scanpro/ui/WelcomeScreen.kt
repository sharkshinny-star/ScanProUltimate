package com.example.scanpro.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.AuroraBackground
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.designsystem.rememberMotionEnabled

private class WelcomePoint(val icon: ImageVector, @StringRes val text: Int, val tone: TileTone)

private val points = listOf(
    WelcomePoint(Icons.Rounded.DocumentScanner, R.string.ui_welcome_point_scan, TileTone.Indigo),
    WelcomePoint(Icons.Rounded.AutoFixHigh, R.string.ui_welcome_point_clean, TileTone.Teal),
    WelcomePoint(Icons.Rounded.Draw, R.string.ui_welcome_point_share, TileTone.Violet)
)

/**
 * The runtime permissions asked for together once, right after the welcome on first launch, as most scanner apps do:
 * the camera, because scanning is what the app is for, and notifications on Android 13 and newer (older versions
 * allow them without asking), so finished and failed jobs can be announced while the user is in another app. If the
 * user says no here, each is asked for again where it is used: the camera screen and Settings > Notifications.
 */
internal object StartupPermissions {
    fun wanted(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun missing(context: Context): List<String> =
        wanted().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
}

/**
 * Shown once on first launch: one page with the promise, the privacy line and three things the app does, then a
 * single button. If Android has not granted the camera and notifications yet, a second page explains why the app
 * would like them and asks for both in one go, so the first scan opens straight into the camera. [onGetStarted]
 * runs when the user is through (Back on either page skips the rest). Drawn edge to edge over the ambient brand
 * backdrop, with the content stepping aside for the system bars.
 */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val context = LocalContext.current
    val motion = rememberMotionEnabled()
    // Saveable, so turning the phone on the permission page doesn't bring the welcome back.
    var askingPermissions by rememberSaveable { mutableStateOf(false) }
    Crossfade(
        targetState = askingPermissions,
        animationSpec = if (motion) tween(320) else snap(),
        label = "welcomePage"
    ) { asking ->
        if (asking) {
            PermissionsPage(onDone = onGetStarted)
        } else {
            OnboardingPage(
                heroIcon = Icons.Rounded.Shield,
                heroTone = TileTone.Teal,
                title = stringResource(R.string.ui_welcome_title),
                body = stringResource(R.string.ui_welcome_tagline),
                points = points.map { OnboardingPoint(it.icon, it.tone, stringResource(it.text)) },
                primary = stringResource(R.string.ui_welcome_get_started),
                onPrimary = {
                    // Nothing left to ask (granted already, e.g. reinstalled with permissions restored): straight in.
                    if (StartupPermissions.missing(context).isEmpty()) onGetStarted() else askingPermissions = true
                }
            )
        }
    }
}

/** The second welcome page: why the app would like each permission it does not have yet, then Android's own dialogs. */
@Composable
private fun PermissionsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    // Fixed while the page is up, so the rows don't change under Android's dialogs as each answer comes in.
    val asked = remember { StartupPermissions.missing(context) }
    // Whatever the answers, the app opens next; a "no" is asked again where that permission is needed.
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone() }
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(asked) { if (asked.isEmpty()) onDone() }
    OnboardingPage(
        heroIcon = Icons.Rounded.PhotoCamera,
        heroTone = TileTone.Indigo,
        title = stringResource(R.string.ui_permissions_title),
        body = stringResource(R.string.ui_permissions_body),
        points = asked.mapNotNull { permission ->
            when (permission) {
                Manifest.permission.CAMERA -> OnboardingPoint(
                    Icons.Rounded.PhotoCamera, TileTone.Indigo,
                    stringResource(R.string.ui_permission_camera_title), stringResource(R.string.ui_permission_camera_body)
                )
                Manifest.permission.POST_NOTIFICATIONS -> OnboardingPoint(
                    Icons.Rounded.Notifications, TileTone.Amber,
                    stringResource(R.string.ui_permission_notifications_title), stringResource(R.string.ui_permission_notifications_body)
                )
                else -> null
            }
        },
        primary = stringResource(R.string.ui_permissions_allow),
        // One request at a time: a second tap while Android's dialog is opening would be refused by the system.
        primaryEnabled = !requested,
        onPrimary = {
            requested = true
            request.launch(asked.toTypedArray())
        },
        secondary = stringResource(R.string.ui_permissions_not_now),
        onSecondary = onDone
    )
}

/** One row of an onboarding page's card: an icon, a line of text and, for permissions, why it is asked for. */
private class OnboardingPoint(val icon: ImageVector, val tone: TileTone, val title: String, val detail: String? = null)

/**
 * The layout both welcome pages share: brand, illustration, title, text, a card of points, a main button and,
 * optionally, a quieter second one.
 */
@Composable
private fun OnboardingPage(
    heroIcon: ImageVector,
    heroTone: TileTone,
    title: String,
    body: String,
    points: List<OnboardingPoint>,
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {}
) {
    val motion = rememberMotionEnabled()
    // First appearance: the header, illustration, text and button rise in one after another. With
    // animations off in the system settings everything is simply there.
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
                BrandLockup(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp).enter(0))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(12.dp))
                    HeroIllustration(
                        icon = heroIcon,
                        tone = heroTone,
                        modifier = Modifier.fillMaxWidth().height(236.dp).enter(1),
                        current = entered
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().enter(2)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().enter(2)
                    )
                    Spacer(Modifier.height(22.dp))
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().enter(3)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            points.forEachIndexed { index, point ->
                                // Rows step in one after another as the card settles into view.
                                val reveal by animateFloatAsState(
                                    targetValue = if (entered) 1f else 0f,
                                    animationSpec = tween(380, delayMillis = 420 + index * 70, easing = FastOutSlowInEasing),
                                    label = "point$index"
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = reveal; translationX = (1f - reveal) * 24.dp.toPx() }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconTile(point.icon, point.tone, size = 38.dp, corner = 12.dp, iconSize = 20.dp)
                                    Spacer(Modifier.width(14.dp))
                                    if (point.detail == null) {
                                        Text(point.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    } else {
                                        Column(Modifier.weight(1f)) {
                                            Text(point.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            Text(
                                                point.detail,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                GradientButton(
                    text = primary,
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).enter(4)
                )
                if (secondary != null) {
                    TextButton(
                        onClick = onSecondary,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).enter(4)
                    ) { Text(secondary) }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
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
    // Lock-on: as the screen comes into view the brackets start loose and white, then snap tight and mint,
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
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(bracketColor, Offset(x, y), Offset(x + dx * l, y), stroke, StrokeCap.Round)
                drawLine(bracketColor, Offset(x, y), Offset(x, y + dy * l), stroke, StrokeCap.Round)
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
