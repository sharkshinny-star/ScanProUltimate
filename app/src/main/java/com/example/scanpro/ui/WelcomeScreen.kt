package com.example.scanpro.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.view.WindowCompat
import com.example.scanpro.core.designsystem.PhoneScanArt
import com.example.scanpro.core.designsystem.PrivateToolsArt
import com.example.scanpro.core.designsystem.ReceiptGridArt
import kotlinx.coroutines.launch
import com.example.scanpro.core.designsystem.AuroraBackground
import com.example.scanpro.data.FilesAccessPrompts
import com.example.scanpro.feature.phonepdfs.PhonePdfSource
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.designsystem.rememberMotionEnabled

/**
 * The runtime permissions asked for together once, right after the welcome on first launch, as most scanner apps do:
 * the camera, because scanning is what the app is for, and notifications on Android 13 and newer (older versions
 * allow them without asking), so finished and failed jobs can be announced while the user is in another app. If the
 * user says no here, each is asked for again where it is used: the camera screen and Settings > Notifications.
 */
internal object StartupPermissions {
    /**
     * Not an Android runtime permission: "All files access" (Android 11+), granted on a system settings page rather
     * than in a dialog. Listed with the others so the page explains it in the same place (F196, owner's request).
     */
    const val FILES_ACCESS = "com.example.scanpro.permission.FILES_ACCESS"

    fun wanted(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        // Up to Android 10 reading shared storage is an ordinary runtime permission, asked with the others.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun missing(context: Context): List<String> = buildList {
        addAll(wanted().filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED })
        if (com.example.scanpro.BuildConfig.ALL_FILES_ACCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !PhonePdfSource.hasAccess(context)) {
            add(FILES_ACCESS)
        }
    }
}

/**
 * Shown once on first launch: three full-bleed pages, each a large picture over a bold headline and a line with its
 * key words in bold (scan; manage files; PDF tools that never leave the phone), with page dots and a Next pill. On
 * the last page Get started goes on, as before, to the permission page if Android has not granted the camera and
 * notifications yet, then [onGetStarted]. Skip (or Back, handled by the caller) jumps straight to that step.
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
            WelcomePager(onFinish = {
                // Nothing left to ask (granted already, e.g. reinstalled with permissions restored): straight in.
                if (StartupPermissions.missing(context).isEmpty()) onGetStarted() else askingPermissions = true
            })
        }
    }
}

/** One welcome page: its picture, headline, and a line whose %1$s is filled with [keyword], shown in bold. */
private class WelcomeSlide(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val keyword: Int,
    val art: @Composable (Modifier) -> Unit
)

private val welcomeSlides = listOf(
    WelcomeSlide(R.string.intro_welcome_scan_title, R.string.intro_welcome_scan_body, R.string.intro_welcome_scan_keyword) { PhoneScanArt(it) },
    WelcomeSlide(R.string.intro_welcome_files_title, R.string.intro_welcome_files_body, R.string.intro_welcome_files_keyword) { ReceiptGridArt(it) },
    WelcomeSlide(R.string.intro_welcome_tools_title, R.string.intro_welcome_tools_body, R.string.intro_welcome_tools_keyword) { PrivateToolsArt(it) }
)

@Composable
private fun WelcomePager(onFinish: () -> Unit) {
    val pager = rememberPagerState { welcomeSlides.size }
    val scope = rememberCoroutineScope()
    val motion = rememberMotionEnabled()
    val last = welcomeSlides.lastIndex
    val background = MaterialTheme.colorScheme.background
    // The pictures sit under the status bar on a dark desk: light status-bar icons while the welcome is up.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(background)
            // Taps on empty parts of the welcome must not reach the app drawn underneath it.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        val artHeight = maxHeight * (if (maxHeight < 620.dp) 0.42f else 0.52f)
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Top) { page ->
                val slide = welcomeSlides[page]
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(artHeight)) {
                        slide.art(Modifier.fillMaxSize())
                        // The picture fades into the page below it.
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(56.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, background)))
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 28.dp)
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(slide.title),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { heading() }
                        )
                        Spacer(Modifier.height(14.dp))
                        val keyword = stringResource(slide.keyword)
                        val body = stringResource(slide.body, keyword)
                        val onSurface = MaterialTheme.colorScheme.onSurface
                        Text(
                            buildAnnotatedString {
                                append(body)
                                val at = body.indexOf(keyword)
                                if (at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurface), at, at + keyword.length)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 28.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WelcomeDots(welcomeSlides.size, pager.currentPage)
                Spacer(Modifier.weight(1f))
                val onLast = pager.currentPage == last
                Button(
                    onClick = {
                        if (onLast) {
                            onFinish()
                        } else {
                            scope.launch {
                                if (motion) pager.animateScrollToPage(pager.currentPage + 1) else pager.scrollToPage(pager.currentPage + 1)
                            }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Text(
                        stringResource(if (onLast) R.string.ui_welcome_get_started else R.string.hint_next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        // Skip, over the picture: a dark pill so it reads on the desk in either theme.
        if (pager.currentPage < last) {
            TextButton(
                onClick = onFinish,
                colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = 0.38f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 16.dp)
            ) { Text(stringResource(R.string.hint_skip), fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Page dots, the current one a wider pill; read out as "Page 1 of 3". */
@Composable
private fun WelcomeDots(count: Int, current: Int) {
    val description = stringResource(R.string.intro_welcome_page, current + 1, count)
    Row(Modifier.clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .height(8.dp)
                    .width(if (selected) 24.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f))
            )
        }
    }
}

/**
 * The second welcome page: why the app would like each permission it does not have yet, then Android's own dialogs
 * and, on Android 11 and newer, the "All files access" settings page; the app opens when the person comes back.
 */
@Composable
private fun PermissionsPage(onDone: () -> Unit) {
    val context = LocalContext.current
    // Fixed while the page is up, so the rows don't change under Android's dialogs as each answer comes in.
    val asked = remember { StartupPermissions.missing(context) }
    val runtime = remember(asked) { asked.filter { it != StartupPermissions.FILES_ACCESS } }
    val wantsFilesPage = StartupPermissions.FILES_ACCESS in asked
    // Waiting for the person to come back from the settings page.
    var waitingForSettings by rememberSaveable { mutableStateOf(false) }

    fun finish() {
        // Asked here, so the one-time prompt for people who passed the welcome before (ScanProApp) never repeats it.
        FilesAccessPrompts.markStartFilesAsked(context)
        onDone()
    }
    fun openFilesPage() {
        if (PhonePdfSource.openAllFilesAccessSettings(context)) waitingForSettings = true else finish()
    }

    // Whatever the answers, the app opens next; a "no" is asked again where that permission is needed.
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (wantsFilesPage && !PhonePdfSource.hasAccess(context)) openFilesPage() else finish()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, waitingForSettings) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && waitingForSettings) {
                waitingForSettings = false
                finish()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(asked) { if (asked.isEmpty()) finish() }
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
                Manifest.permission.READ_EXTERNAL_STORAGE -> OnboardingPoint(
                    Icons.Rounded.Folder, TileTone.Teal,
                    stringResource(R.string.ui_permission_files_title), stringResource(R.string.ui_permission_files_body_dialog)
                )
                StartupPermissions.FILES_ACCESS -> OnboardingPoint(
                    Icons.Rounded.Folder, TileTone.Teal,
                    stringResource(R.string.ui_permission_files_title), stringResource(R.string.ui_permission_files_body_settings)
                )
                else -> null
            }
        },
        primary = stringResource(R.string.ui_permissions_allow),
        // One request at a time: a second tap while Android's dialog is opening would be refused by the system.
        primaryEnabled = !requested,
        onPrimary = {
            requested = true
            if (runtime.isNotEmpty()) request.launch(runtime.toTypedArray()) else openFilesPage()
        },
        secondary = stringResource(R.string.ui_permissions_not_now),
        onSecondary = { finish() }
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
            animationSpec = tween(durationMillis = 360, delayMillis = order * 60, easing = FastOutSlowInEasing),
            label = "enter$order"
        )
        return graphicsLayer { alpha = progress; translationY = (1f - progress) * 20.dp.toPx() }
    }

    AuroraBackground(
        Modifier
            .fillMaxSize()
            // Taps on empty parts of the welcome must not reach the app drawn underneath it.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            val maxH = maxHeight
            val isCompact = maxH < 760.dp
            val isShort = maxH < 620.dp

            val heroHeight = when {
                isShort -> 110.dp
                isCompact -> 150.dp
                maxH < 840.dp -> 180.dp
                else -> 220.dp
            }
            val topPad = if (isCompact) 8.dp else 16.dp
            val spacerBeforeHero = if (isCompact) 4.dp else 10.dp
            val spacerAfterHero = if (isCompact) 10.dp else 20.dp
            val spacerAfterTitle = if (isCompact) 4.dp else 8.dp
            val spacerAfterBody = if (isCompact) 10.dp else 18.dp
            val pointVerticalPadding = if (isCompact) 5.dp else 8.dp
            val surfaceVerticalPadding = if (isCompact) 4.dp else 6.dp
            val buttonVerticalPadding = if (isCompact) 8.dp else 12.dp

            val titleStyle = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
            val bodyStyle = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge

            // widthIn before fillMaxSize: the other way round, fillMaxSize pins the width and the 600 dp cap never applies.
            Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                BrandLockup(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = topPad).enter(0))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(spacerBeforeHero))
                    HeroIllustration(
                        icon = heroIcon,
                        tone = heroTone,
                        modifier = Modifier.fillMaxWidth().height(heroHeight).enter(1),
                        current = entered
                    )
                    Spacer(Modifier.height(spacerAfterHero))
                    Text(
                        title,
                        style = titleStyle,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().enter(2)
                    )
                    Spacer(Modifier.height(spacerAfterTitle))
                    Text(
                        body,
                        style = bodyStyle,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().enter(2)
                    )
                    Spacer(Modifier.height(spacerAfterBody))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().enter(3)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = surfaceVerticalPadding)) {
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
                                        .padding(vertical = pointVerticalPadding),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconTile(point.icon, point.tone, size = 36.dp, corner = 11.dp, iconSize = 18.dp)
                                    Spacer(Modifier.width(12.dp))
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
                    Spacer(Modifier.height(12.dp))
                }
                GradientButton(
                    text = primary,
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = onPrimary,
                    enabled = primaryEnabled,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = buttonVerticalPadding).enter(4)
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
 * page's icon. Drawn in Compose so it follows the theme and scales to any width and height.
 */
@Composable
private fun HeroIllustration(icon: ImageVector, tone: TileTone, modifier: Modifier = Modifier, current: Boolean = true) {
    val brand = ScanProTheme.colors
    val scheme = MaterialTheme.colorScheme
    val sweep = rememberInfiniteTransition(label = "scan")
    val beam = sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "beam"
    )
    val glow = sweep.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val float = sweep.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val motion = rememberMotionEnabled()
    // Read only inside graphicsLayer and Canvas blocks (2026-11-18): read here, they recomposed the illustration on every
    // frame of the three endless animations.
    fun beamPosition() = if (motion) beam.value else 0.5f
    fun haloScale() = if (motion) glow.value else 1f
    fun bob() = if (motion) float.value else 0f
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
        val sheetWidth = minOf(maxWidth * 0.44f, maxHeight * 0.65f, 160.dp)
        val sheetHeight = sheetWidth * 1.3f
        val backOffsetX = (sheetWidth * 0.14f).coerceAtMost(18.dp)
        // Soft halo behind the stack.
        Box(
            Modifier
                .size(sheetWidth * 1.8f)
                .graphicsLayer { scaleX = haloScale(); scaleY = haloScale() }
                .background(
                    Brush.radialGradient(listOf(brand.gradientEnd.copy(alpha = 0.22f), Color.Transparent)),
                    CircleShape
                )
        )
        // Two sheets behind, fanned out; the whole stack drifts gently, the back sheets a little more.
        Box(Modifier.graphicsLayer { translationY = bob() * 7.dp.toPx(); rotationZ = bob() * 1.2f }) {
            Sheet(sheetWidth, sheetHeight, rotation = -9f, offsetX = -backOffsetX - 4.dp, offsetY = 8.dp, alpha = 0.55f, lines = false)
        }
        Box(Modifier.graphicsLayer { translationY = -bob() * 5.dp.toPx(); rotationZ = -bob() * 0.8f }) {
            Sheet(sheetWidth, sheetHeight, rotation = 6f, offsetX = backOffsetX, offsetY = 5.dp, alpha = 0.75f, lines = false)
        }
        // Front sheet with text lines and the beam.
        Box(contentAlignment = Alignment.Center, modifier = Modifier.graphicsLayer { translationY = bob() * 3.dp.toPx() }) {
            Sheet(sheetWidth, sheetHeight, rotation = 0f, offsetX = 0.dp, offsetY = 0.dp, alpha = 1f, lines = true)
            Canvas(Modifier.size(sheetWidth + 30.dp, sheetHeight)) {
                val y = size.height * (0.12f + 0.76f * beamPosition())
                // Wake of the beam: a faint band fading upwards.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, brand.scanLine.copy(alpha = 0.22f)),
                        startY = y - 50f,
                        endY = y
                    ),
                    topLeft = Offset(14f, y - 50f),
                    size = Size(size.width - 28f, 50f)
                )
                drawLine(brand.scanLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 4f, cap = StrokeCap.Round)
                drawCircle(brand.scanLine, radius = 6f, center = Offset(0f, y))
                drawCircle(brand.scanLine, radius = 6f, center = Offset(size.width, y))
            }
        }
        // Brackets around the whole stack; loose until the page locks on.
        Canvas(
            Modifier
                .size(sheetWidth + 64.dp, sheetHeight + 36.dp)
                .graphicsLayer { val s = 1f + (1f - lock) * 0.14f; scaleX = s; scaleY = s }
        ) {
            val l = 24f + (1f - lock) * 8f
            val stroke = 5f
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
        val badgeSize = (sheetWidth * 0.42f).coerceIn(40.dp, 60.dp)
        val badgeIconSize = (badgeSize * 0.52f).coerceIn(20.dp, 30.dp)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = badgeContainer,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = (maxWidth * 0.12f).coerceAtMost(36.dp), bottom = 4.dp)
                .size(badgeSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = badgeContent, modifier = Modifier.size(badgeIconSize))
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
