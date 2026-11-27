package com.example.scanpro.scanner

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.filled.Check
import android.widget.Toast
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.ui.findActivity
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.defaultFilter
import com.example.scanpro.imaging.ImageProcessor
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How the live preview helps: Auto captures by itself, Manual only outlines the page, No detect does neither. */
private enum class DetectMode(@StringRes val label: Int) { AUTO(R.string.scan_detect_auto), MANUAL(R.string.scan_detect_manual), NO_DETECT(R.string.scan_detect_no_detect) }

private var oldSessionsCleaned = false

@Composable
fun CameraScannerScreen(
    /** Mode to open in: a [ScanMode] name, "QR", or empty for the usual document mode. */
    initialMode: String = "",
    /** Filter new scans start with, from Settings; null keeps each mode's own. */
    defaultFilter: PageFilter? = null,
    /** Check pages before saving (Settings, R26): ALWAYS opens the review on Done; the others save at once when nothing needs a look. */
    reviewMode: com.example.scanpro.data.ReviewMode = com.example.scanpro.data.ReviewMode.ALWAYS,
    /** Position of the saved page this session replaces; the session then takes exactly one photo. */
    retakeOfPage: Int? = null,
    onClose: () -> Unit,
    /**
     * Pages, mode, outlines, filters, slider adjustments, then the name typed on the review screen (null: automatic) and
     * the folder chosen there (null: none).
     */
    onComplete: (List<Uri>, ScanMode, List<CropQuad>, List<PageFilter>, List<PageAdjustments?>, String?, String?) -> Unit,
    /** Existing folders a new scan can be saved into (locked ones left out). */
    folders: List<String> = emptyList(),
    /** The folder Files was showing when the scan started (Genius Scan scans into the open folder); blank for none. */
    initialFolder: String = "",
    /** Adding pages to a saved document: no Name or Folder, the pages join that document. */
    appending: Boolean = false,
    /** QR mode's history button; null hides it. */
    onOpenQrHistory: (() -> Unit)? = null,
    /** QR mode's "Create a QR code" button; null hides it. */
    onOpenQrGenerator: (() -> Unit)? = null,
    onFallbackScanner: () -> Unit,
    qrScans: com.example.scanpro.feature.qr.QrScanViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Turned sideways the controls move into a panel on the right, so they don't cover most of a short screen.
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val panelClearance = if (landscape) 244.dp else 0.dp
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    // One decode and one detection per photo, shared by the crop outline and the quality notes.
    // Read when a photo is inspected: an ID card is small by nature, so in that mode a small outline is still the crop.
    val inspectingCards = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val inspector = remember(scope) {
        PageInspector(scope, inspect = { file ->
            OpenCvScanProcessor.inspect(file, if (inspectingCards.get()) 0f else DocumentDetection.MIN_PAGE_AREA)
        })
    }
    val haptic = LocalHapticFeedback.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
            // The screen must not dim or lock while someone lines up a page.
            keepScreenOn = true
        }
    }

    // The viewfinder fades in when the first frame arrives (and out when the camera is released for the review), instead
    // of cutting from black to a picture.
    var previewStreaming by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, previewView) {
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { previewStreaming = it == PreviewView.StreamState.STREAMING }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose { previewView.previewStreamState.removeObserver(observer) }
    }
    val previewAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (previewStreaming) 1f else 0f,
        androidx.compose.animation.core.tween(180),
        label = "previewAlpha"
    )

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    // After a refusal with "don't ask again", the system dialog no longer appears; the panel then leads to Settings.
    var permissionRefused by remember { mutableStateOf(false) }
    var detection by remember { mutableStateOf<DocumentDetection?>(null) }
    var stable by remember { mutableStateOf(false) }
    // How close the page is to being steady enough to capture; fills the ring round the shutter.
    var steadiness by remember { mutableFloatStateOf(0f) }
    // The outline as drawn: it follows the detector with a little lag so it glides instead of jittering.
    var shownQuad by remember { mutableStateOf<CropQuad?>(null) }
    // Holds the outline over a missed frame, ignores a single stray one and damps trembling (2026-11-19).
    val outlineTracker = remember { OutlineTracker() }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var boundUseCases by remember { mutableStateOf<List<androidx.camera.core.UseCase>>(emptyList()) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // A camera problem the user has to act on (camera busy, disabled, failed to open); shown in every mode.
    var cameraError by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("scanner_prefs", android.content.Context.MODE_PRIVATE) }
    // One flash control for the photo flash and the torch together, in the top bar where CamScanner, Adobe Scan and
    // Lens keep theirs (R31): Off, Auto and On fire with the shutter; Torch is a steady light for dark rooms.
    // Remembered between scans, like the detect mode. (A separate torch switch used to write a flag this screen's
    // flash effect never watched, so the torch button often did nothing until the camera restarted.)
    var flashMode by rememberSaveable {
        mutableStateOf(runCatching { FlashMode.valueOf(prefs.getString("flash", null).orEmpty()) }.getOrDefault(FlashMode.OFF))
    }
    val torchEnabled = flashMode == FlashMode.TORCH
    fun setFlash(mode: FlashMode) {
        flashMode = mode
        prefs.edit { putString("flash", mode.name) }
    }
    var detectMode by rememberSaveable {
        // Manual by default (owner decision): auto-capture surprised people; it is one tap away in the top menu.
        mutableStateOf(runCatching { DetectMode.valueOf(prefs.getString("detect", null).orEmpty()) }.getOrDefault(DetectMode.MANUAL))
    }
    // Grid and spirit level are off unless switched on in the menu: the live outline is guidance enough.
    var showGrid by remember { mutableStateOf(prefs.getBoolean("grid", false)) }
    var showLevel by remember { mutableStateOf(prefs.getBoolean("level", false)) }
    // HD capture: the sensor's high-resolution mode and the slower, higher-quality capture. Off by default (fast and
    // light on storage); most modern phones carry a 48 or 50 MP sensor, so it is one tap away in the top bar.
    var hdCapture by remember { mutableStateOf(prefs.getBoolean("hd", false)) }
    // Whether this camera has photos clearly larger than the standard 12 MP (CaptureSizes.offersHd); the HD switch is
    // offered only then. Unknown (true) until the camera is bound.
    var hdOffered by remember { mutableStateOf(true) }
    var overflowOpen by rememberSaveable { mutableStateOf(false) }
    val onLabel = stringResource(R.string.common_on)
    val offLabel = stringResource(R.string.common_off)
    val gridLabel = stringResource(R.string.scan_show_grid)
    var showAdjustments by rememberSaveable { mutableStateOf(false) }
    val autoCapture = detectMode == DetectMode.AUTO
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    // `status` holds short-lived feedback (captured, errors); live detection guidance is kept separately
    // so the per-frame analyser callback cannot overwrite it.
    var status by remember { mutableStateOf<String?>(null) }
    var liveGuidance by remember { mutableStateOf(resources.getString(R.string.scan_guidance_point_at_document)) }
    val disposed = remember { AtomicBoolean(false) }
    var autoCaptureSignal by remember { mutableIntStateOf(0) }
    // Saveable, so captured pages survive rotation and process death instead of being lost (and
    // their files orphaned) when the activity is recreated.
    var scanMode by rememberSaveable { mutableStateOf(ScanMode.entries.firstOrNull { it.name == initialMode } ?: ScanMode.DOCUMENT) }
    androidx.compose.runtime.SideEffect { inspectingCards.set(scanMode == ScanMode.ID_CARD) }
    // Both sides by default: the owner's ID cards are two-sided and both belong on one page (front above back). The
    // last choice (one side, both sides or passport) is remembered, and picking the ID card mode no longer resets it.
    var idKind by rememberSaveable {
        mutableStateOf(IdCardKind.entries.firstOrNull { it.name == prefs.getString("id_kind", null) } ?: IdCardKind.BOTH_SIDES)
    }
    val scanBothSides = idKind == IdCardKind.BOTH_SIDES
    // The one page both sides were put on (IdCardComposer), once Done was tapped after the second side.
    var idComposite by rememberSaveable { mutableStateOf<String?>(null) }
    // Book mode (R29): 0 one page per shot, 1 two pages left first, 2 two pages right first; and the halves made so far,
    // which fast save trusts like the ID composite (they are cut out already).
    var bookLayout by rememberSaveable { mutableIntStateOf(1) }
    var bookHalves by rememberSaveable { mutableStateOf(listOf<String>()) }
    // First-use explainer for Document, ID Card and Book modes: shown once per mode, ever. It used to come back in
    // every camera session until a "Don't remind me again" box was ticked (owner, 2026-11-16: "should show only first
    // time"). Settings > Show introductions again brings them back.
    var introShownFor by rememberSaveable { mutableStateOf(listOf<String>()) }
    var introMode by rememberSaveable { mutableStateOf<String?>(null) }
    // "Try now" on the Document explainer: the built-in sample page goes through the normal review (crop, filters).
    var trySample by remember { mutableStateOf(false) }
    LaunchedEffect(scanMode, permissionGranted) {
        // Not over the camera-permission card: two prompts at once. It shows once the camera is usable.
        if (!permissionGranted) return@LaunchedEffect
        val key = scanMode.introPrefKey()
        if (key != null && scanMode.name !in introShownFor && !prefs.getBoolean(key, false)) {
            introShownFor = introShownFor + scanMode.name
            introMode = scanMode.name
            // Remembered as soon as it is shown, so leaving the app or the camera with it open does not bring it back.
            prefs.edit { putBoolean(key, true) }
        }
    }
    introMode?.let { name ->
        val mode = ScanMode.entries.firstOrNull { it.name == name }
        if (mode == null) {
            introMode = null
        } else {
            ScanIntroDialog(
                mode,
                onTry = {
                    introMode = null
                    trySample = true
                },
                onStart = { introMode = null })
        }
    }
    // Set when the review was left to add a page or to go back to the camera: the next Done returns to it rather
    // than saving at once, whatever "Check pages before saving" says.
    var returnToReview by rememberSaveable { mutableStateOf(false) }
    // A page removed from the strip with a long press (R30): kept for a few seconds so Undo can put it back.
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    // The "Page n removed" message that offers Undo. Undo is shown only beside that message (2026-11-18): it used to
    // appear beside any message while a removal was pending, so "Page 4 captured" offered to bring a page back.
    var undoStatus by remember { mutableStateOf<String?>(null) }
    val pages = rememberSaveable(
        saver = listSaver(save = { list -> list.map(Uri::toString) }, restore = { saved -> mutableStateListOf(*saved.map(Uri::parse).toTypedArray()) })
    ) { mutableStateListOf<Uri>() }
    // Each scanner session writes to its own folder, so closing one never deletes files another
    // session's import is still reading.
    val sessionId = rememberSaveable { UUID.randomUUID().toString() }
    val sessionDir = remember(sessionId) { File(scannerSessionRoot(context), sessionId) }
    var completed by remember { mutableStateOf(false) }
    // The review step after capture: crop, rotate, reorder and add pages before saving.
    var reviewing by rememberSaveable { mutableStateOf(false) }
    // The name typed on the review screen; blank leaves it to the automatic title.
    var documentName by rememberSaveable { mutableStateOf("") }
    var documentFolder by rememberSaveable { mutableStateOf(initialFolder) }
    var reviewIndex by rememberSaveable { mutableIntStateOf(0) }
    // Page the next capture replaces, when the review step asked to retake one; -1 otherwise.
    var retakeIndex by rememberSaveable { mutableIntStateOf(-1) }
    var processing by remember { mutableStateOf(false) }
    val quads = rememberSaveable(
        saver = listSaver(
            save = { map -> encodeQuads(map) },
            restore = { saved -> mutableStateMapOf<String, CropQuad>().apply { saved.mapNotNull(::decodeQuad).forEach { (k, v) -> put(k, v) } } }
        )
    ) { mutableStateMapOf<String, CropQuad>() }
    // The look picked for each page on the review screen; pages without a choice use [sessionFilter].
    val filters = rememberSaveable(
        saver = listSaver(
            save = { map -> map.map { (k, v) -> "${v.name}|$k" } },
            restore = { saved ->
                mutableStateMapOf<String, PageFilter>().apply {
                    saved.forEach { entry ->
                        val filter = runCatching { PageFilter.valueOf(entry.substringBefore('|')) }.getOrNull()
                        if (filter != null) put(entry.substringAfter('|'), filter)
                    }
                }
            }
        )
    ) { mutableStateMapOf<String, PageFilter>() }
    // Brightness / contrast / saturation chosen on the review screen, per page (keyed like filters). Saved across
    // rotation and process death as "b|c|s|shadows|fingers|key" strings.
    val adjustments = rememberSaveable(
        saver = listSaver(
            save = { map ->
                map.map { (k, v) -> "${v.brightness}|${v.contrast}|${v.saturation}|${v.removeShadows}|${v.removeFingers}|$k" }
            },
            restore = { saved ->
                mutableStateMapOf<String, PageAdjustments>().apply {
                    saved.forEach { entry ->
                        val p = entry.split('|', limit = 6)
                        if (p.size == 6) {
                            val b = p[0].toFloatOrNull()
                            val c = p[1].toFloatOrNull()
                            val s = p[2].toFloatOrNull()
                            if (b != null && c != null && s != null) {
                                put(p[5], PageAdjustments(b, c, s, p[3].toBoolean(), p[4].toBoolean()).normalized())
                            }
                        }
                    }
                }
            }
        )
    ) { mutableStateMapOf<String, PageAdjustments>() }
    // What the review should point out per page (blurred, glare, too dark), keyed like filters. Worked out in the
    // background for every page as it arrives (captures, retakes and gallery picks alike); not saved, it is simply
    // worked out again after a restart.
    val quality = remember { mutableStateMapOf<String, Set<PageIssue>>() }
    LaunchedEffect(pages.toList()) {
        val keys = pages.map { it.toString() }.toSet()
        quality.keys.retainAll(keys)
        inspector.retain(pages.map { File(it.path.orEmpty()).absolutePath }.toSet())
        for (page in pages.toList()) {
            val key = page.toString()
            if (key in quality) continue
            val issues = inspector.of(File(page.path.orEmpty()))?.second ?: emptySet()
            if (pages.contains(page)) quality[key] = issues
        }
    }
    // Starts from the mode's usual look; the last filter picked becomes the default for the rest of the session.
    var sessionFilter by rememberSaveable { mutableStateOf<PageFilter?>(null) }
    // Bumped when the user asks to retry camera opening after an error.
    var retryTrigger by remember { mutableIntStateOf(0) }
    val pageLimit = when {
        retakeOfPage != null -> 1
        scanMode == ScanMode.ID_CARD -> if (scanBothSides && idComposite == null) 2 else 1
        else -> 100
    }

    var confirmQuit by rememberSaveable { mutableStateOf(false) }
    // Closing with captured pages would discard them, so ask first.
    val retaking = retakeIndex in pages.indices
    // Leaving: the detector stops at once, so its OpenCV work does not compete with the slide-out animation (the preview
    // keeps showing until the screen is gone; the camera itself is released when the screen leaves the composition).
    val closeScreen = {
        (boundUseCases.firstOrNull { it is ImageAnalysis } as? ImageAnalysis)?.clearAnalyzer()
        onClose()
    }
    val requestClose = {
        when {
            retaking -> { retakeIndex = -1; reviewing = true }
            pages.isNotEmpty() && !completed -> confirmQuit = true
            else -> closeScreen()
        }
    }
    BackHandler(enabled = pages.isNotEmpty() && !completed) { requestClose() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        if (!it) permissionRefused = true
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = (pageLimit - pages.size).coerceAtLeast(0)
            scope.launch {
                // Copy picks into the session folder: the picker's read grant ends with the process,
                // so pages restored after process death would otherwise be unreadable.
                val made = java.util.Collections.synchronizedList(mutableListOf<PickResult>())
                val picks = try {
                    withContext(Dispatchers.IO) {
                        sessionDir.mkdirs()
                        uris.take(remaining).map { uri -> copyPick(context, uri, sessionDir).also { made += it } }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    // The screen went while the copies were being made (the copying itself runs to the end). They would
                    // be lost, files and all: a screen turn hands them to the new screen, as late camera photos are;
                    // leaving the screen deletes them.
                    val copiedFiles = made.filterIsInstance<PickResult.Copied>().map { it.uri }
                    val rotating = (context as? android.app.Activity)?.isChangingConfigurations == true
                    if (rotating) {
                        lateCaptures.update { it + (sessionId to (it[sessionId].orEmpty() + copiedFiles.map(Uri::toString))) }
                    } else {
                        copiedFiles.forEach { uri -> uri.path?.let { File(it).delete() } }
                    }
                    throw cancelled
                }
                val allCopied = picks.filterIsInstance<PickResult.Copied>().map { it.uri }
                // Camera shots taken while copying count too: re-check the limit now and drop the extra copies.
                val room = (pageLimit - pages.size).coerceAtLeast(0)
                val copied = allCopied.take(room)
                allCopied.drop(room).forEach { uri -> uri.path?.let { File(it).delete() } }
                // Say what was left out instead of dropping it silently.
                skippedPicksMessage(
                    resources,
                    heic = picks.count { it == PickResult.Heic },
                    unreadable = picks.count { it == PickResult.Unreadable },
                    overLimit = uris.size - uris.take(remaining).size + (allCopied.size - copied.size),
                    limit = pageLimit
                )?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                pages.addAll(copied)
                status = when {
                    scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> resources.getString(R.string.scan_status_front_ready)
                    pages.isNotEmpty() -> resources.getQuantityString(R.plurals.scan_status_pages_ready, pages.size, pages.size)
                    else -> resources.getString(R.string.scan_status_select_document)
                }
            }
        }
    }

    // QR mode reads codes from the same camera frames instead of looking for a page.
    var barcodeMode by rememberSaveable { mutableStateOf(initialMode == "QR") }
    // Auto-capture is live only when it is switched on, nothing is in the way (tips, a capture in progress, the
    // review step, a full page list) and the mode shows the outline it fires on: ID cards are lined up in the
    // bracket by hand, with no outline or guidance, so firing the shutter there would come as a surprise.
    val autoCaptureActive = autoCapture && !barcodeMode && !capturing && !reviewing &&
        (retaking || pages.size < pageLimit) && scanMode != ScanMode.ID_CARD
    // The hint only changes once the new one has held for GUIDANCE_HOLD_MS: the detector's verdict flips between
    // "too dark", "hold steady" and "ready" from frame to frame, and a pill that changes text four times a second
    // reads as flicker (seen in an owner's screen recording).
    val guidanceHold = remember { GuidanceHold(GUIDANCE_HOLD_MS) }
    val analyzer = remember {
        OpenCvDocumentAnalyzer(
            onDetection = detect@{ result, isStable, progress ->
                // A frame that finished just before the camera stopped (review step, QR mode, detection off) arrives after
                // the outline was cleared: drop it, or it is drawn again and nothing ever replaces it.
                if (reviewing || barcodeMode || detectMode == DetectMode.NO_DETECT) return@detect
                // The screen recomposes on this object; it is replaced only when something it shows changed, not every frame.
                val previousResult = detection
                if (previousResult == null || (previousResult.quad == null) != (result.quad == null) || previousResult.isDark != result.isDark ||
                    previousResult.isBlurry != result.isBlurry || previousResult.isReady != result.isReady ||
                    previousResult.isSkewed != result.isSkewed || previousResult.isConvincingPage != result.isConvincingPage ||
                    PageQuality.isGlare(previousResult.glareShare) != PageQuality.isGlare(result.glareShare) ||
                    previousResult.frameWidth != result.frameWidth || previousResult.frameHeight != result.frameHeight
                ) detection = result
                // A soft tick the moment the page locks on, so the eye can stay on the paper.
                if (isStable && !stable) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                stable = isStable
                steadiness = if (result.quad == null) 0f else progress
                shownQuad = outlineTracker.update(result.quad, android.os.SystemClock.uptimeMillis())
                // "Hold… capturing" is chosen where it is shown, which knows whether auto-capture is on.
                guidanceHold.update(result.guidance.labelRes, android.os.SystemClock.uptimeMillis())?.let { label ->
                    liveGuidance = resources.getString(label)
                }
            },
            onAutoCapture = { autoCaptureSignal++ },
            onUnavailable = { cameraError = resources.getString(R.string.scan_error_detector_unavailable) }
        )
    }
    // The outline, the lock-on state and the ring belong to the page last seen. When the camera stops looking for one
    // (the review step, QR mode, detection switched off) they are cleared, so a retake or a return to the camera does
    // not start with the previous page's outline and tick until the first new frame arrives (or for good, with detection off).
    LaunchedEffect(reviewing, barcodeMode, detectMode) {
        outlineTracker.reset()
        shownQuad = null
        detection = null
        stable = false
        steadiness = 0f
    }
    // Taking a page by hand while the tips show means they have done their job.

    var foundCode by remember { mutableStateOf<ScannedCode?>(null) }
    // Frames with two or more codes show a tappable outline over each instead of opening one at random.
    var outlinedFrame by remember { mutableStateOf<FrameCodes?>(null) }
    // Several codes found in a picture from the gallery: the person picks one from a list.
    var pictureCodes by remember { mutableStateOf<List<ScannedCode>>(emptyList()) }
    var barcodeUnavailable by remember { mutableStateOf(false) }
    // QR mode's batch scan collects every new code without opening it; beep and vibrate are the person's choice.
    var qrBatch by remember { mutableStateOf(prefs.getBoolean("qr_batch", false)) }
    var qrBeep by remember { mutableStateOf(prefs.getBoolean("qr_beep", false)) }
    var qrVibrate by remember { mutableStateOf(prefs.getBoolean("qr_vibrate", true)) }
    val batchCodes = remember { mutableStateListOf<ScannedCode>() }
    var showBatch by remember { mutableStateOf(false) }
    val beeper = remember { CodeBeeper() }
    DisposableEffect(beeper) { onDispose { beeper.release() } }
    val codeFeedback = {
        if (qrVibrate) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (qrBeep) beeper.beep()
    }
    /** Adds the codes not yet in the batch; true when any was new. */
    val collect = { codes: List<ScannedCode> ->
        val fresh = codes.filter { code -> batchCodes.none { it.raw == code.raw } }.distinctBy { it.raw }
        fresh.forEach { batchCodes.add(it); qrScans.record(it) }
        if (fresh.isNotEmpty()) codeFeedback()
        fresh.isNotEmpty()
    }
    val barcodeAnalyzer = remember {
        BarcodeFrameAnalyzer(
            context = context.applicationContext,
            onCodes = { frame ->
                if (qrBatch) {
                    // Keep reading: a repeat of a collected code is ignored, a new one is added with a beep or buzz.
                    collect(frame.codes.map { it.code })
                    false
                } else if (frame.codes.size >= 2 || outlinedFrame != null) {
                    // Keep looking: the outlines follow the codes until one is tapped.
                    outlinedFrame = frame
                    false
                } else {
                    // A single code opens straight away, as it always has; returning true pauses the reader meanwhile.
                    val only = frame.codes.first().code
                    codeFeedback()
                    foundCode = only
                    qrScans.record(only)
                    true
                }
            },
            onUnavailable = { barcodeUnavailable = true }
        )
    }
    val frameAnalyzer = remember { SwitchingAnalyzer(analyzer, barcodeAnalyzer) }
    // QR mode's gallery button: read a code from a screenshot or photo, like Lens.
    val codePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) barcodeAnalyzer.scanImage(uri) { codes ->
            if (qrBatch && codes.isNotEmpty()) {
                collect(codes)
                barcodeAnalyzer.resume()
                showBatch = true
            } else if (codes.size == 1) {
                foundCode = codes.first()
                qrScans.record(codes.first())
            } else if (codes.size > 1) {
                pictureCodes = codes
            } else {
                barcodeAnalyzer.resume()
                status = resources.getString(R.string.scan_qr_none_in_image)
            }
        }
    }
    // Outlines go away once no code has been seen for about half a second.
    LaunchedEffect(outlinedFrame) {
        if (outlinedFrame != null) {
            delay(500)
            outlinedFrame = null
        }
    }
    SideEffect {
        frameAnalyzer.barcodeMode = barcodeMode
        // Nothing reads the result in No-detect mode or during review, so the frames are not analysed at all.
        analyzer.enabled = detectMode != DetectMode.NO_DETECT && !reviewing && !barcodeMode
        analyzer.autoCaptureEnabled = autoCaptureActive
    }

    /** Opens the review step on the newest page. */
    /** Applies each page's confirmed crop, then hands the finished images to the importer. */
    fun saveReviewed() {
        // Ignore a second tap during the exit animation; it would import twice.
        if (completed || processing || pages.isEmpty()) return
        processing = true
        val inputs = pages.toList().map { it to quads[it.toString()] }
        val chosen = pages.map { filters[it.toString()] ?: sessionFilter ?: defaultFilter ?: scanMode.defaultFilter }
        scope.launch {
            // The untouched originals are kept and the confirmed crop travels beside them, so the border can
            // still be widened later; nothing here cuts pixels out of the photo.
            val crops = withContext(Dispatchers.Default) {
                inputs.map { (uri, quad) ->
                    quad ?: inspector.of(File(uri.path.orEmpty()))?.first ?: CropQuad.DEFAULT
                }
            }
            // The navigation host accepts the scan only while this screen is resumed. If the user left the app
            // while the outlines were being found, wait for their return instead of marking the scan done and
            // having it ignored (it then could be neither saved nor imported).
            lifecycleOwner.lifecycle.withResumed {
                completed = true
                processing = false
                onComplete(
                    inputs.map { it.first }, scanMode, crops, chosen, inputs.map { (uri, _) -> adjustments[uri.toString()] },
                    documentName.trim().ifBlank { null },
                    documentFolder.trim().ifBlank { null }
                )
            }
        }
    }

    /**
     * Fast save (R26): with "Check pages before saving" on Always, or when a saved page is being shot again, Done opens
     * the review as always. Otherwise every page is checked (outline found? sharp, no glare, not dark?) and the scan is
     * saved straight away unless the setting says to review the pages that need a look, and some do.
     */
    fun reviewOrSave() {
        if (FastSave.reviewWithoutChecking(reviewMode, retaking = retakeOfPage != null, returnToReview = returnToReview)) {
            returnToReview = false
            reviewIndex = pages.lastIndex
            reviewing = true
            return
        }
        processing = true
        status = resources.getString(R.string.scan_status_checking_pages)
        val snapshot = pages.toList()
        val trusted = snapshot.indices.filter { snapshot[it].toString() == idComposite || snapshot[it].toString() in bookHalves }.toSet()
        scope.launch {
            // Whatever is still missing (Done tapped before the background work landed) is worked out once here, from
            // a single decode and detection per page, and kept, so saving does not do it again.
            val (crops, issues) = withContext(Dispatchers.Default) {
                val crops = ArrayList<CropQuad>(snapshot.size)
                val issues = ArrayList<Set<PageIssue>>(snapshot.size)
                snapshot.forEachIndexed { i, uri ->
                    val key = uri.toString()
                    var crop = quads[key]
                    var issue = quality[key]
                    if (i in trusted) issue = issue ?: emptySet()
                    if (crop == null || issue == null) {
                        // Joins the background run already under way for this photo instead of starting another.
                        val found = inspector.of(File(uri.path.orEmpty()))
                        crop = crop ?: found?.first
                        issue = issue ?: found?.second
                    }
                    crops += crop ?: CropQuad.DEFAULT
                    issues += issue ?: emptySet()
                }
                crops to issues
            }
            snapshot.forEachIndexed { i, uri ->
                val key = uri.toString()
                if (pages.contains(uri)) {
                    if (quads[key] == null) quads[key] = crops[i]
                    if (quality[key] == null) quality[key] = issues[i]
                }
            }
            val needing = FastSave.pagesNeedingReview(crops, issues, trusted)
            processing = false
            if (snapshot != pages.toList()) {
                // Pages changed while checking (a retake landed): just show the review.
                reviewIndex = pages.lastIndex
                reviewing = true
            } else if (FastSave.shouldReview(reviewMode, needing)) {
                reviewIndex = needing.firstOrNull() ?: pages.lastIndex
                if (needing.isNotEmpty()) status = resources.getQuantityString(R.plurals.scan_status_pages_need_look, needing.size, needing.size)
                reviewing = true
            } else {
                saveReviewed()
            }
        }
    }

    // A removed page's file goes after six seconds without Undo; Done settles it at once.
    LaunchedEffect(pendingDelete) {
        val deleted = pendingDelete ?: return@LaunchedEffect
        delay(6_000)
        if (pendingDelete === deleted) {
            pendingDelete = null
            deleteInBackground(File(deleted.uri.path.orEmpty()))
        }
    }

    fun finishScan() {
        pendingDelete?.let { deleteInBackground(File(it.uri.path.orEmpty())); pendingDelete = null }
        if (completed || pages.isEmpty() || processing) return
        // Both sides of an ID card go onto one page before the review, so what is reviewed is what gets saved.
        if (scanMode == ScanMode.ID_CARD && scanBothSides && idComposite == null && pages.size == 2 && retakeOfPage == null) {
            processing = true
            status = resources.getString(R.string.scan_status_id_composing)
            val (front, back) = pages.toList()
            val frontQuad = quads[front.toString()]
            val backQuad = quads[back.toString()]
            val composed = File(sessionDir, "id_${UUID.randomUUID()}.jpg")
            scope.launch {
                val ok = withContext(Dispatchers.Default) {
                    runCatching {
                        IdCardComposer.compose(File(front.path.orEmpty()), frontQuad, File(back.path.orEmpty()), backQuad, composed)
                    }.isSuccess
                }
                processing = false
                if (ok) {
                    val uri = Uri.fromFile(composed)
                    pages.clear()
                    pages.add(uri)
                    quads[uri.toString()] = CropQuad.DEFAULT
                    idComposite = uri.toString()
                    reviewOrSave()
                } else {
                    // The two sides stay as two pages rather than losing the capture, and are shown for a look.
                    status = resources.getString(R.string.scan_status_id_compose_failed)
                    reviewIndex = pages.lastIndex
                    reviewing = true
                }
            }
            return
        }
        reviewOrSave()
    }


    // Capture feedback: a quick white flash, then the photo shrinks into the page counter, which gives a bounce.
    val flash = remember { Animatable(0f) }
    val flight = remember { Animatable(0f) }
    val counterPulse = remember { Animatable(1f) }
    var flyingPage by remember { mutableStateOf<Bitmap?>(null) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    /** Height of the bottom control panel (portrait), so the viewfinder can stay clear of it. */
    var panelHeightPx by remember { mutableIntStateOf(0) }
    var counterCenter by remember { mutableStateOf<Offset?>(null) }

    fun flyIntoCounter(photo: File) {
        scope.launch {
            val thumb = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeOrientedScaled(photo.absolutePath, 480) }.getOrNull() }
                ?: return@launch
            flyingPage = thumb
            flight.snapTo(0f)
            flight.animateTo(1f, tween(durationMillis = 480, delayMillis = 120, easing = FastOutSlowInEasing))
            flyingPage = null
            counterPulse.snapTo(1.25f)
            counterPulse.animateTo(1f, spring(dampingRatio = 0.4f))
        }
    }

    fun capturePage() {
        val capture = imageCapture ?: return
        // A retake replaces a page, so it is allowed at the page limit.
        if (capturing || (!retaking && pages.size >= pageLimit)) return
        capturing = true
        status = resources.getString(R.string.scan_status_capturing)
        scope.launch {
            flash.snapTo(0.8f)
            flash.animateTo(0f, tween(280))
        }

        val id = UUID.randomUUID().toString()
        val raw = File(sessionDir, "raw_$id.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(raw).build()
        // Made here, before the shutter: queued behind analysis frames it could come too late for CameraX to write the
        // photo, or be refused outright once the screen's executor was shut down. An existing folder costs nothing.
        sessionDir.mkdirs()
        capture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // The screen was closed while the shutter was running; launching on its cancelled
                    // scope would skip the cleanup below, so drop the raw file here.
                    if (disposed.get()) {
                        // A rotation recreated the screen while the shutter ran: hand the photo to the new
                        // composition (same session folder) instead of losing the page.
                        val rotating = (context as? android.app.Activity)?.isChangingConfigurations == true
                        if (rotating && raw.exists()) {
                            lateCaptures.update { it + (sessionId to (it[sessionId].orEmpty() + Uri.fromFile(raw).toString())) }
                        } else {
                            raw.delete()
                        }
                        return
                    }
                    // Keep the raw photo: cropping happens in the review step, from the full-size original.
                    val captured = Uri.fromFile(raw)
                    // Read once: the checks below wait for the page inspector, and Back in that time clears retakeIndex.
                    val retakeAt = retakeIndex.takeIf { it in pages.indices } ?: -1
                    val replacing = retakeAt >= 0
                    if (replacing) {
                        val old = pages[retakeAt]
                        pages[retakeAt] = captured
                        quads.remove(old.toString())
                        filters.remove(old.toString())
                        adjustments.remove(old.toString())
                        if (old.scheme == "file") deleteInBackground(File(old.path.orEmpty()))
                    } else {
                        pages.add(captured)
                    }
                    capturing = false
                    // ID cards keep only what was inside the guide; other pages are outlined in the background so the
                    // review screen opens ready.
                    val viewW = previewView.width.toFloat()
                    val viewH = previewView.height.toFloat()
                    val density = previewView.resources.displayMetrics.density
                    val idMode = scanMode == ScanMode.ID_CARD
                    val idRatio = idKind.heightRatio
                    val detectsEdges = detectMode != DetectMode.NO_DETECT
                    scope.launch {
                        val guideCrop = if (!idMode) null else withContext(Dispatchers.Default) {
                            runCatching {
                                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                android.graphics.BitmapFactory.decodeFile(raw.absolutePath, bounds)
                                val turned = androidx.exifinterface.media.ExifInterface(raw.absolutePath).rotationDegrees % 180 != 0
                                val (w, h) = if (turned) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
                                idCardCrop(viewW, viewH, w, h, density, idRatio)
                            }.getOrNull()
                        }
                        val detected = inspector.of(raw)?.first
                        // ID cards: the card's own outline when it lies inside the guide (a tight, straightened crop),
                        // else everything inside the guide as before. With edge detection off, always the guide.
                        val quad = when {
                            guideCrop == null -> detected
                            detectsEdges -> IdCardOutline.choose(guideCrop, detected)
                            else -> guideCrop
                        }
                        if (quad != null && quads[captured.toString()] == null && pages.contains(captured)) quads[captured.toString()] = quad
                        // Book mode, two pages: the spread is cut out and split at the spine into two pages (R29).
                        if (scanMode == ScanMode.BOOK && bookLayout != 0 && !replacing && quad != null && pages.contains(captured)) {
                            val leftFile = File(sessionDir, "raw_${id}_left.jpg")
                            val rightFile = File(sessionDir, "raw_${id}_right.jpg")
                            val ok = withContext(Dispatchers.Default) {
                                runCatching { com.example.scanpro.imaging.BookSpread.split(raw, quad, leftFile, rightFile) }.isSuccess
                            }
                            val at = pages.indexOf(captured)
                            if (ok && at >= 0) {
                                val halves = com.example.scanpro.imaging.BookSpread.ordered(Uri.fromFile(leftFile), Uri.fromFile(rightFile), leftFirst = com.example.scanpro.imaging.BookSpread.leftFirst(bookLayout))
                                pages.removeAt(at)
                                pages.addAll(at, halves)
                                quads.remove(captured.toString())
                                halves.forEach { quads[it.toString()] = CropQuad.DEFAULT }
                                bookHalves = bookHalves + halves.map { it.toString() }
                                deleteInBackground(raw)
                                status = resources.getString(R.string.scan_status_spread_split, at + 1, at + 2)
                            } else {
                                leftFile.delete(); rightFile.delete()
                            }
                        }
                    }
                    status = when {
                        replacing -> resources.getString(R.string.scan_status_page_retaken, retakeAt + 1)
                        scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> resources.getString(R.string.scan_status_front_saved)
                        scanMode == ScanMode.ID_CARD && pages.size == pageLimit -> resources.getString(R.string.scan_status_id_ready)
                        else -> resources.getString(R.string.scan_status_page_captured, pages.size)
                    }
                    if (replacing) {
                        // Straight back to the page that was retaken, so it can be checked at once.
                        reviewIndex = retakeAt
                        retakeIndex = -1
                        reviewing = true
                    } else if (retakeOfPage != null) finishScan() else flyIntoCounter(raw)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    // The shot failed, so the page was not captured: let auto-capture try it again.
                    analyzer.rearm()
                    // A half-written file must not be mistaken for a page later.
                    deleteInBackground(raw)
                    status = when (exception.imageCaptureError) {
                        ImageCapture.ERROR_FILE_IO -> resources.getString(R.string.scan_error_save_photo)
                        ImageCapture.ERROR_CAMERA_CLOSED -> resources.getString(R.string.scan_error_camera_closed)
                        else -> resources.getString(R.string.scan_error_capture_retry)
                    }
                }
            }
        )
    }

    LaunchedEffect(trySample) {
        if (!trySample) return@LaunchedEffect
        trySample = false
        if (pages.size >= pageLimit) return@LaunchedEffect
        val sample = File(sessionDir, "raw_sample_${UUID.randomUUID()}.jpg")
        val quad = withContext(Dispatchers.IO) {
            runCatching {
                sessionDir.mkdirs()
                val bitmap = com.example.scanpro.core.designsystem.renderSampleScanBitmap()
                try {
                    sample.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                } finally {
                    bitmap.recycle()
                }
                runCatching { OpenCvScanProcessor.detectCrop(sample) }.getOrNull()
            }
        }
        if (quad.isFailure || !sample.exists()) {
            deleteInBackground(sample)
            status = resources.getString(R.string.intro_doc_sample_failed)
            return@LaunchedEffect
        }
        val uri = Uri.fromFile(sample)
        pages.add(uri)
        quad.getOrNull()?.let { quads[uri.toString()] = it }
        status = resources.getString(R.string.intro_doc_sample_ready)
        reviewIndex = pages.lastIndex
        reviewing = true
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(1.seconds)
            focusPoint = null
        }
    }

    LaunchedEffect(status) {
        if (status != null) {
            // A removal message stays as long as its Undo works (six seconds, see pendingDelete).
            delay(if (pendingDelete != null && status == undoStatus) 6.seconds else 3.seconds)
            status = null
        }
    }

    LaunchedEffect(autoCaptureSignal) {
        if (autoCaptureSignal > 0 && autoCapture && !capturing && !reviewing && (retaking || pages.size < pageLimit)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            capturePage()
        }
    }

    // Re-keyed on `reviewing`: the camera is released while the review step is up, so the preview and
    // OpenCV analysis stop costing battery behind a screen that doesn't show them, and bound again after.
    // Saved, so a rotation (no transition) does not wait again.
    var enterSettled by rememberSaveable { mutableStateOf(false) }
    val motionEnabled = com.example.scanpro.core.designsystem.rememberMotionEnabled()
    // Counts binding requests. CameraX answers each one later on the main thread, and an answer to an out-of-date request
    // (the review step opened, HD toggled, a retry) must not bind the camera behind the screen that replaced it.
    val bindGeneration = remember { java.util.concurrent.atomic.AtomicInteger() }
    // The one observer of the camera's state, so a rebind replaces it instead of piling up another.
    val cameraStateWatch = remember { CameraStateWatch() }
    LaunchedEffect(permissionGranted, lifecycleOwner, reviewing, retryTrigger, hdCapture) {
        val generation = bindGeneration.incrementAndGet()
        if (!permissionGranted) return@LaunchedEffect
        if (reviewing) {
            cameraStateWatch.stop()
            cameraProvider?.unbindAll()
            imageCapture = null
            boundCamera = null
            boundUseCases = emptyList()
            return@LaunchedEffect
        }
        val future = ProcessCameraProvider.getInstance(context)
        // Binding the camera holds the main thread for several frames. Done while the screen was still animating in,
        // it made the opening stutter (owner, 2026-11-16), so the first bind waits for the transition to finish.
        if (!enterSettled) {
            if (motionEnabled) delay(com.example.scanpro.navigation.CameraEnterDuration + 20L)
            enterSettled = true
        }
        future.addListener({
            // The screen may have been left, or this request replaced by a newer one, before the provider became ready.
            if (disposed.get() || generation != bindGeneration.get()) return@addListener
            runCatching {
                val provider = future.get()
                if (disposed.get()) {
                    provider.unbindAll()
                    return@addListener
                }
                if (generation != bindGeneration.get()) return@addListener
                cameraProvider = provider
                // The same 4:3 field of view for preview, capture and analysis, so what the overlay outlines is what is saved.
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = previewView.surfaceProvider }
                // Standard: about 12 MP, enough for the 3400 px the pipeline keeps, with no extra shutter lag, disk or
                // decode time. HD: up to about 48 MP where the sensor offers it (a closest-lower rule, so a 12 MP phone
                // stays at 12 MP), preferring resolution over capture rate, with the quality capture mode and quality 98.
                // The extra pixels are kept: the page is cut out of a larger photo, so small print stays sharp.
                val captureSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            // 4096x3072, not 4000x3000: the closest-lower rule skipped a 4096x3072 camera's full size and
                            // took 3456x2592, a third fewer pixels (measured on the owner's phone, see CaptureSizes).
                            if (hdCapture) android.util.Size(CaptureSizes.HD_WIDTH, CaptureSizes.HD_HEIGHT)
                            else android.util.Size(CaptureSizes.STANDARD_WIDTH, CaptureSizes.STANDARD_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .apply { if (hdCapture) setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE) }
                    .build()
                val capture = ImageCapture.Builder()
                    .setResolutionSelector(captureSelector)
                    // Quality mode for every capture (2026-11-18; standard used the low-latency mode). In low-latency mode
                    // CameraX takes the frame without the focus and exposure pre-capture sequence and may skip the
                    // camera's multi-frame noise reduction, so a page caught while focus was still settling came out soft.
                    // Auto-capture already waits for a steady, sharp page, so the extra few hundred milliseconds cost
                    // little. Quality 97: at 95 the strokes of small print showed JPEG ringing after the filters.
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(if (hdCapture) 98 else 97)
                    .build()
                // Live detection stays bounded at 4:3 1280x960 (or the nearest lower size the camera offers): the detector
                // scales every frame down to 900 px anyway, so a larger stream only costs conversion time and memory.
                // Capture keeps its own high-resolution selector, so this does not shrink saved pages.
                val analysisSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(android.util.Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(analysisSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }

                provider.unbindAll()
                if (disposed.get() || generation != bindGeneration.get()) return@addListener

                // Tablets and Chromebooks may only have a front or external camera; any camera beats none.
                val cameraSelector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.Builder().build()
                }
                val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture, analysis)
                // On a 12 MP camera HD asks for nothing more than standard: the switch would do nothing, so it goes.
                // An empty list means the camera could not be asked: keep the switch then.
                val sizes = CaptureSizes.jpegSizes(provider, cameraSelector)
                hdOffered = sizes.isEmpty() || CaptureSizes.offersHd(sizes)
                imageCapture = capture
                boundCamera = camera
                boundUseCases = listOf(preview, capture, analysis)
                cameraError = null
                // Errors after binding (another app took the camera, device policy, a fold closing) arrive here.
                cameraStateWatch.watch(camera.cameraInfo.cameraState, lifecycleOwner) { state ->
                    val error = state.error
                    cameraError = if (error == null) null else when (error.code) {
                        androidx.camera.core.CameraState.ERROR_CAMERA_IN_USE,
                        androidx.camera.core.CameraState.ERROR_MAX_CAMERAS_IN_USE -> resources.getString(R.string.scan_error_camera_in_use)
                        androidx.camera.core.CameraState.ERROR_CAMERA_DISABLED -> resources.getString(R.string.scan_error_camera_disabled)
                        androidx.camera.core.CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> resources.getString(R.string.scan_error_camera_dnd)
                        androidx.camera.core.CameraState.ERROR_STREAM_CONFIG -> resources.getString(R.string.scan_error_camera_stream)
                        else -> if (error.type == androidx.camera.core.CameraState.ErrorType.CRITICAL) resources.getString(R.string.scan_error_camera_stopped) else null
                    }
                }
                camera.cameraInfo.zoomState.value?.let { maxZoomRatio = it.maxZoomRatio }
                val exposureState = camera.cameraInfo.exposureState
                if (exposureState.isExposureCompensationSupported) {
                    exposureMin = exposureState.exposureCompensationRange.lower
                    exposureMax = exposureState.exposureCompensationRange.upper
                    exposureIndex = exposureState.exposureCompensationIndex.coerceIn(exposureMin, exposureMax)
                } else {
                    exposureMin = 0
                    exposureMax = 0
                    exposureIndex = 0
                }
            }.onFailure {
                cameraError = resources.getString(R.string.scan_error_camera_open)
            }
        }, mainExecutor)
    }

    // Volume keys take a photo while this screen can; presses arrive from the activity.
    // The screen is not restarted on rotation, so the camera must be told the new rotation (see followDisplayRotation).
    DisposableEffect(boundUseCases) {
        val stop = if (boundUseCases.isEmpty()) ({}) else followDisplayRotation(context, previewView, boundUseCases)
        onDispose(stop)
    }
    DisposableEffect(reviewing, barcodeMode, permissionGranted) {
        HardwareShutter.active = permissionGranted && !reviewing && !barcodeMode
        onDispose { HardwareShutter.active = false }
    }
    val shutterPresses by HardwareShutter.presses.collectAsState()
    LaunchedEffect(shutterPresses) {
        if (shutterPresses > 0 && HardwareShutter.active && !capturing && (retaking || pages.size < pageLimit)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            capturePage()
        }
    }

    // 17: a level bubble from the accelerometer, so an oblique angle shows before the capture.
    var tilt by remember { mutableStateOf(Offset.Zero) }
    val levelShowing = showLevel && permissionGranted && !barcodeMode && !reviewing && scanMode != ScanMode.ID_CARD
    DisposableEffect(levelShowing) {
        val sensors = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val accelerometer = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val listener = object : android.hardware.SensorEventListener {
            private var sx = 0f
            private var sy = 0f
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                // Degrees of lean along each axis; smoothed so the bubble glides.
                val x = Math.toDegrees(kotlin.math.atan2(event.values[0].toDouble(), event.values[2].toDouble().coerceAtLeast(0.01))).toFloat()
                val y = Math.toDegrees(kotlin.math.atan2(event.values[1].toDouble(), event.values[2].toDouble().coerceAtLeast(0.01))).toFloat()
                sx += (x - sx) * 0.2f
                sy += (y - sy) * 0.2f
                tilt = Offset(sx, sy)
            }
            // Only the readings matter for the bubble level; a change in the sensor's accuracy needs no reaction.
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) { /* the level only needs the readings */ }
        }
        // Registered only while the bubble is on screen: at SENSOR_DELAY_UI the callback runs dozens of
        // times a second, which is pure waste while nothing shows the readings.
        if (levelShowing && accelerometer != null) sensors.registerListener(listener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors?.unregisterListener(listener) }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            bindGeneration.incrementAndGet()
            cameraStateWatch.stop()
            cameraProvider?.unbindAll()
            analysisExecutor.shutdownNow()
            barcodeAnalyzer.close()
            // Leaving without Done discards the session's captures. A rotation recreates the screen
            // with the same saved pages, so keep the files then.
            val changingConfigurations = (context as? android.app.Activity)?.isChangingConfigurations == true
            // Up to a hundred large JPEGs: not on the main thread during the exit animation.
            if (!completed && !changingConfigurations) Thread { sessionDir.deleteRecursively() }.start()
            analyzer.close()
        }
    }

    // Photos finished by the previous incarnation of this screen while it was being rotated.
    LaunchedEffect(sessionId) {
        lateCaptures.collect { waiting ->
            val late = waiting[sessionId] ?: return@collect
            lateCaptures.update { it - sessionId }
            late.map(Uri::parse).forEach { uri ->
                when {
                    pages.contains(uri) -> Unit
                    pages.size < pageLimit -> pages.add(uri)
                    // No room for it any more: a photo nobody will see must not stay in the session folder.
                    else -> uri.path?.let { deleteInBackground(File(it)) }
                }
            }
        }
    }

    // Clear out sessions abandoned by a crash or process death more than a day ago (once per app process).
    LaunchedEffect(Unit) {
        if (!oldSessionsCleaned) {
            oldSessionsCleaned = true
            withContext(Dispatchers.IO) {
                val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                scannerSessionRoot(context).listFiles()
                    ?.filter { it.name != sessionId && it.lastModified() < cutoff && !File(it, SESSION_PENDING_IMPORT).exists() }
                    ?.forEach { it.deleteRecursively() }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Granted in Settings and came back: the panel must go without a tap.
                permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(flashMode, boundCamera) {
        // torchEnabled follows flashMode, one of this effect's keys, so Torch applies the moment it is picked.
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
        imageCapture?.flashMode = when (flashMode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    // snapshotFlow, not effect keys: a pinch changes the zoom every frame, and re-keying the effect on it
    // cancelled and restarted a coroutine per frame. One effect per camera now watches the values instead.
    LaunchedEffect(boundCamera) {
        val camera = boundCamera ?: return@LaunchedEffect
        snapshotFlow { zoomRatio.coerceIn(1f, maxZoomRatio) }.collectLatest { camera.cameraControl.setZoomRatio(it) }
    }

    LaunchedEffect(boundCamera) {
        val camera = boundCamera ?: return@LaunchedEffect
        snapshotFlow { Triple(exposureIndex, exposureMin, exposureMax) }.collectLatest { (index, min, max) ->
            if (min < max) camera.cameraControl.setExposureCompensationIndex(index.coerceIn(min, max))
        }
    }

    if (!permissionGranted) {
        val activity = context.findActivity()
        val askable = activity == null || !permissionRefused ||
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        PermissionPanel(
            askable = askable,
            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onFallback = onFallbackScanner,
            onClose = onClose
        )
        return
    }

    if (reviewing && pages.isNotEmpty()) {
        ScanReviewScreen(
            pages = pages,
            quads = quads,
            filters = filters,
            adjustments = adjustments,
            quality = quality,
            defaultFilter = sessionFilter ?: defaultFilter ?: scanMode.defaultFilter,
            onDefaultFilterChange = { sessionFilter = it },
            index = reviewIndex,
            processing = processing,
            onIndexChange = { reviewIndex = it },
            onAdd = { retakeIndex = -1; returnToReview = true; reviewing = false },
            onBack = { retakeIndex = -1; returnToReview = true; reviewing = false },
            onDone = ::saveReviewed,
            onRetake = {
                if (idComposite != null && pages.getOrNull(reviewIndex)?.toString() == idComposite) {
                    // The composed ID page can only be shot again as a whole: both sides, front first.
                    val composed = pages[reviewIndex]
                    pages.clear()
                    quads.remove(composed.toString())
                    deleteInBackground(File(composed.path.orEmpty()))
                    idComposite = null
                    retakeIndex = -1
                    status = resources.getString(R.string.scan_status_id_retake)
                } else {
                    retakeIndex = reviewIndex.coerceIn(0, pages.lastIndex)
                    status = resources.getString(R.string.scan_status_retaking_page, retakeIndex + 1)
                }
                reviewing = false
            },
            name = documentName,
            // A page shot again for a saved document keeps that document's name.
            onNameChange = if (retakeOfPage == null && !appending) ({ documentName = it }) else null,
            folder = documentFolder,
            folders = folders,
            onFolderChange = if (retakeOfPage == null && !appending) ({ documentFolder = it }) else null
        )
        return
    }

    // The scanner's chrome uses the dark theme's surfaces whatever the app theme, so the preview reads as the content.
    val scanLine = ScanProTheme.colors.scanLine
        // The hint / Undo chip. Portrait: in the dark band above the viewfinder, where the live guidance pill sits (that
        // pill is hidden while a hint shows), so it neither covers the outline nor resizes the frame. Landscape: in the panel.
        val statusChip: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            shape = RoundedCornerShape(50),
            // Dark like the guidance pill: it can sit over the camera image on shorter screens.
            color = Color.Black.copy(alpha = 0.6f),
            modifier = modifier
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    status.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
                pendingDelete?.takeIf { status == undoStatus }?.let { deleted ->
                    // Undo puts the page back where it was, with its outline, filter and sliders.
                    TextButton(onClick = {
                        val at = deleted.index.coerceAtMost(pages.size)
                        pages.add(at, deleted.uri)
                        val key = deleted.uri.toString()
                        deleted.quad?.let { quads[key] = it }
                        deleted.filter?.let { filters[key] = it }
                        deleted.adjustments?.let { adjustments[key] = it }
                        if (deleted.wasComposite) idComposite = key
                        pendingDelete = null
                        status = resources.getString(R.string.scan_status_page_restored, at + 1)
                    }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(stringResource(R.string.common_undo), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(CameraBackdrop)
            .onGloballyPositioned { screenOrigin = it.positionInRoot(); screenSize = it.size }
    ) {
        // Pinch to zoom. A touch that ever had two fingers down is a pinch, not a tap to focus.
        val twoFingers = remember { BooleanArray(1) }
        val pinchDetector = remember(context) {
            android.view.ScaleGestureDetector(
                context,
                object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                        zoomRatio = (zoomRatio * detector.scaleFactor).coerceIn(1f, maxZoomRatio.coerceAtLeast(1f))
                        return true
                    }
                }
            )
        }
        // The viewfinder shows the whole 4:3 frame, and the page outline is drawn over that same frame, so both live in
        // one box that stops above the control panel. Before, the panel was drawn over the bottom of the frame and hid
        // the bottom edge and corners of the outline. The top of the frame stays where it was and the frame shrinks a
        // little to end above the panel (FIT_CENTER keeps the whole frame, so nothing is cropped).
        // Landscape: the panel is on the right, and the box ends where it starts.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val previewInsets: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> =
            if (landscape || screenSize.width == 0 || panelHeightPx == 0) 0.dp to 0.dp else {
                // The hint chip is not in the portrait panel (it sits above the viewfinder), so the panel's height is steady.
                val reserved = panelHeightPx
                val frameHeight = screenSize.width * 4f / 3f
                val limit = screenSize.height - reserved
                val naturalTop = (screenSize.height - frameHeight) / 2f
                if (naturalTop + frameHeight <= limit) 0.dp to 0.dp else {
                    with(density) { naturalTop.coerceAtLeast(0f).toDp() to reserved.toDp() }
                }
            }
        val previewTop by androidx.compose.animation.core.animateDpAsState(previewInsets.first, tween(180), label = "previewTop")
        val previewBottom by androidx.compose.animation.core.animateDpAsState(previewInsets.second, tween(180), label = "previewBottom")
        Box(Modifier.fillMaxSize().padding(top = previewTop, bottom = previewBottom, end = panelClearance)) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previewAlpha },
                update = { view ->
                    view.setOnTouchListener { _, event ->
                        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) twoFingers[0] = false
                        if (event.pointerCount > 1) twoFingers[0] = true
                        pinchDetector.onTouchEvent(event)
                        if (event.action == android.view.MotionEvent.ACTION_UP && !twoFingers[0]) {
                            // Report the tap as a click for accessibility services.
                            view.performClick()
                            focusPoint = Offset(event.x, event.y)
                            boundCamera?.let { camera ->
                                val point = view.meteringPointFactory.createPoint(event.x, event.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                camera.cameraControl.startFocusAndMetering(action)
                            }
                        }
                        true
                    }
                }
            )

            if (showGrid && !reviewing) ThirdsGrid()
            if (!barcodeMode && detectMode != DetectMode.NO_DETECT) {
                // ID card mode (2026-11-19, it had no outline): the card is outlined once it is seen inside the guide,
                // and that outline is what the capture keeps. Anything else found in the frame is not drawn.
                val idMode = scanMode == ScanMode.ID_CARD
                val idRatio = idKind.heightRatio
                DocumentBoundaryOverlay(
                    detection = detection,
                    quad = {
                        val outline = shownQuad
                        val frame = detection
                        if (!idMode || outline == null || frame == null) outline else {
                            val guide = idCardCrop(
                                previewView.width.toFloat(), previewView.height.toFloat(), frame.frameWidth, frame.frameHeight,
                                previewView.resources.displayMetrics.density, idRatio
                            )
                            outline.takeIf { guide != null && IdCardOutline.fits(guide, it) }
                        }
                    },
                    stable = stable
                )
            }
            if (scanMode == ScanMode.BOOK && bookLayout != 0 && !barcodeMode && !reviewing) {
                val tracking = detectMode != DetectMode.NO_DETECT
                BookSplitGuide(
                    detection = if (tracking) detection else null,
                    quad = { if (tracking) shownQuad else null },
                    accent = scanLine,
                    leftFirst = com.example.scanpro.imaging.BookSpread.leftFirst(bookLayout)
                )
            }
            if (scanMode == ScanMode.ID_CARD) {
                // Which side is next, and how many there are: "Front · 1 of 2", then "Back · 2 of 2".
                val idLabel = when {
                    idKind == IdCardKind.PASSPORT -> stringResource(R.string.scan_id_passport_page)
                    scanBothSides -> {
                        val back = pages.isNotEmpty()
                        stringResource(
                            R.string.scan_id_side_step,
                            stringResource(if (back) R.string.scan_id_side_back else R.string.scan_id_side_front),
                            if (back) 2 else 1,
                            2
                        )
                    }
                    else -> stringResource(R.string.scan_id_side_front)
                }
                IdCardFrame(label = idLabel, heightRatio = idKind.heightRatio)
            }
            focusPoint?.let { point ->
                Box(
                    Modifier
                        .offset { IntOffset((point.x - 32.dp.toPx()).toInt(), (point.y - 32.dp.toPx()).toInt()) }
                        .size(64.dp)
                        .border(2.dp, Color(0xFF22D3EE), RoundedCornerShape(4.dp))
                )
            }
        }

        // QR viewfinder and tappable code outlines (2026-11-18): in the same padded box as the camera image, so the
        // outlines land on the codes when the preview is inset (landscape panel, short phones), and before the zoom pill
        // and status chip so the dimming layer is no longer drawn over them.
        Box(Modifier.fillMaxSize().padding(top = previewTop, bottom = previewBottom, end = panelClearance)) {
            if (barcodeMode && foundCode == null) {
                QrViewfinder(
                    hint = stringResource(
                        when {
                            barcodeUnavailable -> R.string.scan_qr_downloading
                            qrBatch -> R.string.qrscan_batch_on
                            outlinedFrame != null -> R.string.qr_pick_one_hint
                            else -> R.string.scan_qr_point
                        }
                    ),
                    motion = com.example.scanpro.core.designsystem.rememberMotionEnabled()
                )
            }
            outlinedFrame?.let { frame ->
                if (barcodeMode && foundCode == null && !qrBatch) {
                    QrCodeOutlines(frame) { picked ->
                        barcodeAnalyzer.pause()
                        codeFeedback()
                        outlinedFrame = null
                        foundCode = picked
                        qrScans.record(picked)
                    }
                }
            }
        }
        if (!reviewing && maxZoomRatio > 1.05f) {
            ZoomPill(
                zoomRatio = { zoomRatio },
                maxZoomRatio = maxZoomRatio,
                onZoomChange = { zoomRatio = it },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp + panelClearance)
            )
        }
        if (status != null && !landscape) {
            statusChip(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 62.dp, start = 16.dp, end = 16.dp))
        }
        if (levelShowing) {
            // The lean is handed over as a lambda and read while drawing, so a sensor reading never
            // recomposes this screen, only redraws the bubble.
            LevelBubble(tilt = { tilt }, accent = scanLine, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 16.dp + panelClearance))
        }

        foundCode?.let { code ->
            val scanAgain = {
                foundCode = null
                outlinedFrame = null
                barcodeAnalyzer.resume()
            }
            BarcodeResultSheet(code, onScanAgain = scanAgain, onDismiss = scanAgain)
        }
        if (showBatch && barcodeMode && foundCode == null) {
            QrBatchSheet(
                codes = batchCodes,
                onPick = { picked ->
                    showBatch = false
                    barcodeAnalyzer.pause()
                    foundCode = picked
                },
                onClear = {
                    batchCodes.clear()
                    showBatch = false
                },
                onDismiss = { showBatch = false }
            )
        }
        if (pictureCodes.isNotEmpty()) {
            QrCodeChooserSheet(
                codes = pictureCodes,
                onPick = { picked ->
                    pictureCodes = emptyList()
                    foundCode = picked
                    qrScans.record(picked)
                },
                onDismiss = {
                    pictureCodes = emptyList()
                    barcodeAnalyzer.resume()
                }
            )
        }

        if (!barcodeMode && status == null && detectMode != DetectMode.NO_DETECT && scanMode != ScanMode.ID_CARD) {
            // In low light the hint is the button: one tap turns the torch on.
            val dark = detection?.isDark == true && !torchEnabled
            // A tilted page remains capturable: the geometric hint is advice, not an auto-capture veto.
            val found = stable && detection?.isReady == true
            Surface(
                onClick = { setFlash(FlashMode.TORCH) },
                enabled = dark,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 62.dp, end = panelClearance),
                shape = RoundedCornerShape(16.dp),
                color = if (found) HintFound else Color.Black.copy(alpha = 0.6f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    val state = detection
                    // In manual mode, do not announce auto-capture; a tilted page gets an advisory rather than "ready".
                    val guidance = if (found && autoCaptureActive) stringResource(R.string.scan_guidance_hold_capturing) else liveGuidance
                    val ready = found
                    val hintIcon = when {
                        ready -> Icons.Default.TaskAlt
                        state?.isDark == true -> Icons.Default.Bolt
                        state?.quad != null && state.isBlurry -> Icons.Default.PanTool
                        state?.quad != null && PageQuality.isGlare(state.glareShare) -> Icons.Default.WbSunny
                        state?.isSkewed == true -> Icons.Default.Rotate90DegreesCw
                        state?.quad != null -> Icons.Default.CenterFocusWeak
                        else -> Icons.Default.DocumentScanner
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(hintIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.animation.AnimatedContent(
                            targetState = guidance,
                            transitionSpec = {
                                (androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(tween(160)) { it / 3 })
                                    .togetherWith(androidx.compose.animation.fadeOut(tween(120)))
                            },
                            label = "guidance"
                        ) { text ->
                            // Read out as it changes, so TalkBack users hear "page found" and the other hints (Lens and
                            // Genius Scan speak their framing guidance too).
                            Text(
                                text,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                            )
                        }
                    }
                    state?.takeIf { it.quad != null }?.let { page ->
                        Spacer(Modifier.height(6.dp))
                        ScannerQualityIndicators(page)
                    }
                }
            }
        }
        cameraError?.let { problem ->
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp).widthIn(max = 420.dp),
                shape = RoundedCornerShape(16.dp),
                color = CameraGlass
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(problem, color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { cameraError = null; retryTrigger++ }) { Text(stringResource(R.string.common_retry), color = Color.White) }
                        TextButton(onClick = onFallbackScanner) { Text(stringResource(R.string.scan_compat_scanner), color = scanLine) }
                    }
                }
            }
        }
        if (retakeOfPage != null && status == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 108.dp, end = panelClearance),
                shape = RoundedCornerShape(50),
                color = CameraGlass
            ) {
                Text(stringResource(R.string.scan_retaking_page, retakeOfPage + 1), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                title = { Text(stringResource(R.string.scan_quit_title)) },
                text = {
                    Column(
                        Modifier.verticalScroll(
                        rememberScrollState()
                    )) { Text(stringResource(R.string.scan_quit_body)) }
                },
                confirmButton = { TextButton(onClick = { confirmQuit = false; closeScreen() }) { Text(stringResource(R.string.scan_quit_discard), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp + panelClearance),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = CameraGlass) {
                IconButton(onClick = requestClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White) }
            }
            // The page count is on the Done button and on the page strip; it is not repeated here.
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // HD capture switch: lit mint while on. A scanner has no use for it on a barcode.
                if (!barcodeMode && hdOffered) {
                    val hdLabel = stringResource(R.string.scan_hd_capture)
                    val hdOnMessage = stringResource(R.string.scan_hd_on)
                    val hdOffMessage = stringResource(R.string.scan_hd_off)
                    Surface(
                        shape = CircleShape,
                        color = if (hdCapture) scanLine else CameraGlass,
                        contentColor = if (hdCapture) Color(0xFF062B1F) else Color.White,
                        modifier = Modifier.semantics { contentDescription = hdLabel; stateDescription = if (hdCapture) onLabel else offLabel }
                    ) {
                        IconButton(onClick = {
                            hdCapture = !hdCapture
                            prefs.edit { putBoolean("hd", hdCapture) }
                            status = if (hdCapture) hdOnMessage else hdOffMessage
                        }) {
                            Text("HD", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                if (barcodeMode) {
                    onOpenQrGenerator?.let { open ->
                        Surface(shape = CircleShape, color = CameraGlass) {
                            IconButton(onClick = open) {
                                Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.qr_create_code), tint = Color.White)
                            }
                        }
                    }
                    onOpenQrHistory?.let { open ->
                        Surface(shape = CircleShape, color = CameraGlass) {
                            IconButton(onClick = open) {
                                Icon(Icons.Default.History, contentDescription = stringResource(R.string.qr_history_title), tint = Color.White)
                            }
                        }
                    }
                }
                // One flash button cycling Off → Auto → On → Torch (R31); amber while any light is chosen.
                val flashState = stringResource(R.string.scan_flash_state, stringResource(flashMode.labelRes))
                val flashActive = flashMode != FlashMode.OFF
                Surface(
                    shape = CircleShape,
                    color = if (flashActive) TorchOn else CameraGlass,
                    contentColor = if (flashActive) Color(0xFF222222) else Color.White
                ) {
                    IconButton(onClick = {
                        val cycle = FlashMode.entries
                        setFlash(cycle[(cycle.indexOf(flashMode) + 1) % cycle.size])
                    }) {
                        Icon(
                            when (flashMode) {
                                FlashMode.AUTO -> Icons.Default.FlashAuto
                                FlashMode.ON -> Icons.Default.FlashOn
                                FlashMode.TORCH -> Icons.Default.Bolt
                                else -> Icons.Default.FlashOff
                            },
                            contentDescription = flashState
                        )
                    }
                }
                Box {
                    Surface(shape = CircleShape, color = CameraGlass) {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.scan_more_options), tint = Color.White)
                        }
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (showAdjustments) R.string.scan_hide_zoom_exposure else R.string.scan_zoom_exposure)) },
                            onClick = { showAdjustments = !showAdjustments; overflowOpen = false }
                        )
                        @Composable
                        fun SwitchItem(@StringRes label: Int, on: Boolean, onToggle: () -> Unit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(label)) },
                                trailingIcon = { if (on) Icon(Icons.Default.Check, contentDescription = null) },
                                modifier = Modifier.semantics { stateDescription = if (on) onLabel else offLabel },
                                onClick = onToggle
                            )
                        }
                        if (!barcodeMode && scanMode != ScanMode.ID_CARD) {
                            SwitchItem(R.string.scan_detect_edges, detectMode != DetectMode.NO_DETECT) {
                                detectMode = if (detectMode == DetectMode.NO_DETECT) DetectMode.MANUAL else DetectMode.NO_DETECT
                                prefs.edit { putString("detect", detectMode.name) }
                            }
                        }
                        // Also here, not only on the pill that the page count replaces: so it can be changed mid-batch.
                        if (!barcodeMode && scanMode != ScanMode.ID_CARD && detectMode != DetectMode.NO_DETECT) {
                            SwitchItem(R.string.scan_detection_mode, autoCapture) {
                                detectMode = if (autoCapture) DetectMode.MANUAL else DetectMode.AUTO
                                prefs.edit { putString("detect", detectMode.name) }
                            }
                        }
                        if (!barcodeMode && hdOffered) {
                            SwitchItem(R.string.scan_hd_capture, hdCapture) { hdCapture = !hdCapture; prefs.edit { putBoolean("hd", hdCapture) } }
                        }
                        if (barcodeMode) {
                            SwitchItem(R.string.qrscan_batch, qrBatch) { qrBatch = !qrBatch; outlinedFrame = null; prefs.edit { putBoolean("qr_batch", qrBatch) } }
                            SwitchItem(R.string.qrscan_beep, qrBeep) {
                                qrBeep = !qrBeep
                                prefs.edit { putBoolean("qr_beep", qrBeep) }
                                if (qrBeep) beeper.beep() // a sample, so the person hears what they turned on
                            }
                            SwitchItem(R.string.qrscan_vibrate, qrVibrate) { qrVibrate = !qrVibrate; prefs.edit { putBoolean("qr_vibrate", qrVibrate) } }
                        }
                        SwitchItem(R.string.scan_show_grid, showGrid) { showGrid = !showGrid; prefs.edit { putBoolean("grid", showGrid) } }
                        SwitchItem(R.string.scan_show_level, showLevel) { showLevel = !showLevel; prefs.edit { putBoolean("level", showLevel) } }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_compat_scanner)) },
                            onClick = { overflowOpen = false; onFallbackScanner() }
                        )
                    }
                }
            }
        }

        // Floating thumbnail strip in portrait: floats above the bottom panel so it never expands the panel
        // or shrinks the 4:3 camera viewfinder.
        if (!landscape && pages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = with(LocalDensity.current) { panelHeightPx.toDp() } + 8.dp
                    )
            ) {
                CapturedStrip(
                    pages = pages,
                    quads = quads,
                    filters = filters,
                    quality = quality,
                    // The same fallback the save uses, so a thumbnail shows the look the page will get.
                    sessionFilter = sessionFilter ?: defaultFilter ?: scanMode.defaultFilter,
                    onOpen = { index ->
                        reviewIndex = index
                        reviewing = true
                    },
                    onDelete = { index ->
                        val uri = pages.getOrNull(index) ?: return@CapturedStrip
                        val key = uri.toString()
                        pendingDelete?.let { deleteInBackground(File(it.uri.path.orEmpty())) }
                        pendingDelete = PendingDelete(uri, index, quads[key], filters[key], adjustments[key], idComposite == key)
                        quads.remove(key); filters.remove(key); adjustments.remove(key)
                        if (idComposite == key) idComposite = null
                        pages.removeAt(index)
                        status = resources.getString(R.string.scan_status_page_removed, index + 1)
                        undoStatus = status
                    }
                )
            }
        }

        Column(
            modifier = (
                if (landscape) {
                    // A tall panel on the right edge; the mode list in it scrolls when the screen is short.
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(232.dp)
                        .background(CameraPanel, RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp))
                        .statusBarsPadding()
                } else {
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .onSizeChanged { panelHeightPx = it.height }
                        .background(CameraPanel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                }
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // In landscape, captured pages stay inside the side panel where vertical height is unconstrained.
            if (landscape && pages.isNotEmpty()) {
                CapturedStrip(
                    pages = pages,
                    quads = quads,
                    filters = filters,
                    quality = quality,
                    // The same fallback the save uses, so a thumbnail shows the look the page will get.
                    sessionFilter = sessionFilter ?: defaultFilter ?: scanMode.defaultFilter,
                    onOpen = { index ->
                        reviewIndex = index
                        reviewing = true
                    },
                    onDelete = { index ->
                        val uri = pages.getOrNull(index) ?: return@CapturedStrip
                        val key = uri.toString()
                        pendingDelete?.let { deleteInBackground(File(it.uri.path.orEmpty())) }
                        pendingDelete = PendingDelete(uri, index, quads[key], filters[key], adjustments[key], idComposite == key)
                        quads.remove(key); filters.remove(key); adjustments.remove(key)
                        if (idComposite == key) idComposite = null
                        pages.removeAt(index)
                        status = resources.getString(R.string.scan_status_page_removed, index + 1)
                        undoStatus = status
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            if (status != null && landscape) {
                statusChip(Modifier)
                Spacer(Modifier.height(8.dp))
            }

            if (showAdjustments) {
                if (maxZoomRatio > 1.05f) {
                    val zoomLabel = "${(zoomRatio * 10).roundToInt() / 10f}x"
                    val zoomDescription = stringResource(R.string.scan_zoom_level, zoomLabel)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(zoomLabel, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                        Slider(
                            value = zoomRatio.coerceIn(1f, maxZoomRatio),
                            onValueChange = { zoomRatio = it },
                            valueRange = 1f..maxZoomRatio,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).semantics { contentDescription = zoomDescription }
                        )
                    }
                }
                if (exposureMin < exposureMax) {
                    val exposureDescription = stringResource(R.string.scan_exposure)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.scan_ev), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = exposureIndex.toFloat(),
                            // Slider ticks are not exact in float (2.9999998), so round rather than truncate.
                            onValueChange = { exposureIndex = it.roundToInt().coerceIn(exposureMin, exposureMax) },
                            valueRange = exposureMin.toFloat()..exposureMax.toFloat(),
                            steps = (exposureMax - exposureMin - 1).coerceAtLeast(0),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).semantics { contentDescription = exposureDescription }
                        )
                        val exposureLabel = if (exposureIndex > 0) "+$exposureIndex" else exposureIndex.toString()
                        Text(exposureLabel, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Mode tabs. Receipt, Whiteboard and Book live in the overflow menu under "Docs".
            // Book mode adds One page / Two pages (left first) / Two pages, right first (R29).
            if (scanMode == ScanMode.BOOK) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val canChange = pages.isEmpty() && retakeOfPage == null
                    ModeTab(stringResource(R.string.scan_book_one_page), selected = bookLayout == 0, enabled = canChange) {
                        if (canChange) bookLayout = 0
                    }
                    ModeTab(stringResource(R.string.intro_book_two_pages), selected = bookLayout != 0, enabled = canChange) {
                        if (canChange && bookLayout == 0) bookLayout = 1
                    }
                    if (bookLayout != 0) {
                        // A | B or B | A: which half is saved first. The letters in the viewfinder swap with it.
                        val leftFirst = com.example.scanpro.imaging.BookSpread.leftFirst(bookLayout)
                        val orderText = stringResource(if (leftFirst) R.string.intro_book_order_left_first else R.string.intro_book_order_right_first)
                        val (a, b) = com.example.scanpro.core.designsystem.bookHalfLetters(leftFirst)
                        Surface(
                            onClick = {
                                bookLayout = com.example.scanpro.imaging.BookSpread.swapped(bookLayout)
                                status = resources.getString(
                                    if (com.example.scanpro.imaging.BookSpread.leftFirst(bookLayout)) R.string.intro_book_order_left_first
                                    else R.string.intro_book_order_right_first
                                )
                            },
                            enabled = retakeOfPage == null,
                            shape = RoundedCornerShape(50),
                            color = CameraGlass,
                            contentColor = Color.White,
                            modifier = Modifier.semantics { contentDescription = orderText }
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$a | $b", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(18.dp))
                            }
                        }
                    }
                    Surface(
                        onClick = { showGrid = !showGrid; prefs.edit { putBoolean("grid", showGrid) } },
                        shape = CircleShape,
                        color = if (showGrid) Color.White else CameraGlass,
                        contentColor = if (showGrid) Color(0xFF222222) else Color.White,
                        modifier = Modifier.semantics {
                            contentDescription = gridLabel
                            stateDescription = if (showGrid) onLabel else offLabel
                        }
                    ) {
                        Icon(if (showGrid) Icons.Default.GridOn else Icons.Default.GridOff, contentDescription = null, modifier = Modifier.padding(6.dp).size(20.dp))
                    }
                }
            }
            // ID cards add a Front only / Both sides / Passport row; the mode tabs stay, so a wrong tap is undone with another tap.
            if (scanMode == ScanMode.ID_CARD) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IdCardKind.entries.forEach { kind ->
                        ModeTab(stringResource(kind.labelRes), selected = idKind == kind, enabled = pages.isEmpty() && retakeOfPage == null) {
                            if (pages.isEmpty() && retakeOfPage == null) {
                                idKind = kind
                                prefs.edit { putString("id_kind", kind.name) }
                            }
                        }
                    }
                }
            }
            // Every mode in one strip that scrolls sideways; the chosen one is underlined.
            val modesEnabled = pages.isEmpty() && retakeOfPage == null
            // Tapping a mode says what it does. Once a page is taken the modes stay put, and a tap says why.
            val modeLockedMessage = stringResource(R.string.scan_mode_locked)
            val modeTabs: @Composable () -> Unit = {
                listOf(ScanMode.DOCUMENT, ScanMode.RECEIPT, ScanMode.WHITEBOARD, ScanMode.ID_CARD, ScanMode.BOOK, ScanMode.TEXT).forEach { mode ->
                    val hint = stringResource(modeHint(mode))
                    ModeTab(stringResource(mode.labelRes), selected = !barcodeMode && scanMode == mode, enabled = modesEnabled) {
                        if (modesEnabled) {
                            barcodeMode = false
                            scanMode = mode
                            status = hint
                        } else {
                            status = modeLockedMessage
                        }
                    }
                }
                val qrHint = stringResource(R.string.scan_hint_qr)
                ModeTab(stringResource(R.string.scan_qr_code), selected = barcodeMode, enabled = modesEnabled) {
                    if (modesEnabled) {
                        barcodeMode = true
                        scanMode = ScanMode.DOCUMENT
                        status = qrHint
                    } else {
                        status = modeLockedMessage
                    }
                }
            }
            if (landscape) {
                // Down the side panel, filling what the controls below leave free.
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { modeTabs() }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) { modeTabs() }
            }

            val takePhotoDescription = stringResource(R.string.scan_take_photo)
            // A FlowRow, not a Row: in the narrow landscape panel the gallery button, the shutter and the
            // Auto/Manual pill do not fit side by side, and the pill used to be squeezed to "Ma" (R44). It now
            // moves to a second line, centred, when there is not room.
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (landscape) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(if (landscape) Modifier.fillMaxWidth() else Modifier.width(104.dp), contentAlignment = if (landscape) Alignment.Center else Alignment.CenterStart) {
                    val importLabel = stringResource(if (barcodeMode) R.string.scan_qr_from_image else R.string.scan_import_photos)
                    BottomPill(
                        onClick = {
                            val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (barcodeMode) codePicker.launch(request) else photoPicker.launch(request)
                        },
                        modifier = Modifier.semantics { contentDescription = importLabel }
                    ) { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White) }
                }

                // Outer ring fills clockwise as the page settles and turns solid mint once it is steady; the
                // inner disc is the button.
                // Driven from a coroutine and read only while drawing: `steadiness` changes with every camera frame, and
                // reading it here during composition recomposed the whole camera screen about 25 times a second.
                val ringProgress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    snapshotFlow { (if (stable) 1f else steadiness) to (steadiness == 0f) }.collectLatest { (target, resetting) ->
                        ringProgress.animateTo(target, if (resetting) tween(200) else spring(stiffness = 700f))
                    }
                }
                // QR codes are read live, so there is no shutter in QR mode: just the viewfinder, like Lens.
                if (barcodeMode) {
                    // Batch scan on or off, where the shutter sits in the other modes (codes are read live, no shutter).
                    val batchLabel = stringResource(R.string.qrscan_batch)
                    Surface(
                        onClick = {
                            qrBatch = !qrBatch
                            outlinedFrame = null
                            prefs.edit { putBoolean("qr_batch", qrBatch) }
                        },
                        shape = CircleShape,
                        color = if (qrBatch) TorchOn else Color.White.copy(alpha = 0.12f),
                        contentColor = if (qrBatch) Color(0xFF2B2000) else Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .semantics {
                                contentDescription = batchLabel
                                stateDescription = if (qrBatch) onLabel else offLabel
                                role = Role.Switch
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.DynamicFeed, contentDescription = null, modifier = Modifier.size(30.dp))
                        }
                    }
                } else {
                val shutterScale by androidx.compose.animation.core.animateFloatAsState(if (capturing) 0.86f else 1f, spring(stiffness = 900f), label = "shutter")
                Box(
                    Modifier
                        .size(72.dp)
                        .graphicsLayer { scaleX = shutterScale; scaleY = shutterScale }
                        .drawBehind {
                            val strokeWidth = 4.dp.toPx()
                            val inset = strokeWidth / 2
                            drawCircle(Color.White.copy(alpha = 0.35f), radius = size.minDimension / 2 - inset, style = Stroke(strokeWidth))
                            if (ringProgress.value > 0f) {
                                drawArc(
                                    color = scanLine,
                                    startAngle = -90f,
                                    sweepAngle = 360f * ringProgress.value,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                        .padding(7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            capturePage()
                        },
                        enabled = !barcodeMode && !capturing && (retaking || pages.size < pageLimit),
                        shape = CircleShape,
                        // Dimmed in QR mode: codes are read live, there is nothing to capture.
                        // Also dimmed when it can't take a page (both ID card sides taken, or the page limit): it used to
                        // look live and do nothing.
                        color = if (barcodeMode) Color.White.copy(alpha = 0.25f)
                            else if (!capturing && !retaking && pages.size >= pageLimit) Color.White.copy(alpha = 0.35f)
                            else Color.White,
                        // The shutter has no visible label, so screen readers need one.
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = takePhotoDescription }
                    ) {}
                }
                }

                Box(if (landscape) Modifier.fillMaxWidth() else Modifier.width(104.dp), contentAlignment = if (landscape) Alignment.Center else Alignment.CenterEnd) {
                    if (pages.isNotEmpty()) {
                        // The page count; tapping it finishes the scan.
                        val doneLabel = stringResource(R.string.common_done)
                        BottomPill(
                            onClick = { finishScan() },
                            enabled = !capturing && (scanMode != ScanMode.ID_CARD || pages.size == pageLimit),
                            color = scanLine,
                            contentColor = Color(0xFF04281E),
                            modifier = Modifier
                                .onGloballyPositioned { counterCenter = it.boundsInRoot().center }
                                .graphicsLayer { scaleX = counterPulse.value; scaleY = counterPulse.value }
                                .semantics { contentDescription = doneLabel }
                        ) {
                            // A tick beside the count says this is the way to finish, not just a counter.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${pages.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (!barcodeMode && scanMode != ScanMode.ID_CARD && detectMode != DetectMode.NO_DETECT) {
                        // Auto capture on or off (off is Manual: the page is outlined, the shutter is yours).
                        val modeDescription = stringResource(R.string.scan_detection_mode)
                        BottomPill(
                            onClick = {
                                detectMode = if (autoCapture) DetectMode.MANUAL else DetectMode.AUTO
                                prefs.edit { putString("detect", detectMode.name) }
                            },
                            color = if (autoCapture) TorchOn else Color.White.copy(alpha = 0.1f),
                            contentColor = if (autoCapture) Color(0xFF222222) else Color.White,
                            modifier = Modifier.semantics { contentDescription = modeDescription; stateDescription = if (autoCapture) onLabel else offLabel }
                        ) {
                            // Names the current state (Auto or Manual) instead of "Auto" in both.
                            Text(
                                stringResource(if (autoCapture) R.string.scan_detect_auto else R.string.scan_detect_manual),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else if (barcodeMode && qrBatch && batchCodes.isNotEmpty()) {
                        // The collected codes; tapping the count opens the list.
                        val countLabel = stringResource(R.string.qrscan_batch_count, batchCodes.size)
                        BottomPill(
                            onClick = { showBatch = true },
                            color = TorchOn,
                            contentColor = Color(0xFF2B2000),
                            modifier = Modifier.semantics { contentDescription = countLabel }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${batchCodes.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Drawn last so it sits above the controls; neither layer takes touches.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
        flyingPage?.let { photo ->
            val start = Offset(screenSize.width / 2f, screenSize.height / 2f)
            val end = counterCenter?.minus(screenOrigin) ?: Offset(screenSize.width * 0.85f, screenSize.height * 0.9f)
            Image(
                photo.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.6f)
                    .aspectRatio(photo.width.toFloat() / photo.height.toFloat())
                    .graphicsLayer {
                        val t = flight.value
                        translationX = (end.x - start.x) * t
                        translationY = (end.y - start.y) * t
                        val scale = 1f - 0.88f * t
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.3f * t
                    }
                    .border(3.dp, Color.White)
            )
        }
    }
}

/** Scanner chrome colours: the dark theme's surfaces, used whatever the app theme. */
private val CameraBackdrop = Color(0xFF0B0D14)
private val CameraPanel = Color(0xFF14161F)
private val CameraGlass = Color(0xFF0F1118).copy(alpha = 0.72f)
/** The design's accents: amber for the torch, the underline and Auto; green for "document found". */
private val TorchOn = Color(0xFFFFD54A)
private val HintFound = Color(0xFF14804F)

/**
 * The page outline is blue everywhere it is drawn (owner, 2026-11-18): over the live camera, in the review and in the
 * crop editor. The lighter blue is the outline while the page is still being found; the full blue once it is steady.
 */
internal val PageOutlineBlue = Color(0xFF2979FF)
internal val PageOutlineBlueSoft = Color(0xFF82B1FF)

/**
 * QR mode's viewfinder, like Google Lens: everything outside a centred square is dimmed, the square has rounded
 * corner brackets and a scan line sweeping down it, and a hint sits under it. Any code in view is read, inside the
 * square or not; the square only shows where to aim.
 */
@Composable
private fun QrViewfinder(hint: String, motion: Boolean) {
    val sweep = if (motion) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "qr")
        // The State, read only inside the Canvas below: reading .value here recomposed the viewfinder on every frame.
        transition.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(1800), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "qrSweep"
        )
    } else null
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val side = minOf(size.width * 0.68f, size.height * 0.42f)
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f - 60.dp.toPx()
            val radius = 22.dp.toPx()
            val outside = Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + side, top + side, radius, radius))
            }
            drawPath(outside, Color.Black.copy(alpha = 0.55f))
            val arm = side * 0.16f
            val w = 5.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                val path = Path().apply {
                    moveTo(x, y + dy * arm)
                    lineTo(x, y + dy * radius)
                    quadraticTo(x, y, x + dx * radius, y)
                    lineTo(x + dx * arm, y)
                }
                drawPath(path, Color.White, style = Stroke(w, cap = StrokeCap.Round))
            }
            corner(left, top, 1f, 1f)
            corner(left + side, top, -1f, 1f)
            corner(left, top + side, 1f, -1f)
            corner(left + side, top + side, -1f, -1f)
            val y = top + side * (0.1f + 0.8f * (sweep?.value ?: 0.5f))
            drawLine(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color.Transparent, TorchOn, Color.Transparent),
                    startX = left + radius, endX = left + side - radius
                ),
                Offset(left + radius, y), Offset(left + side - radius, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }
        Text(
            hint,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .offset(y = 150.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

/**
 * One small pill instead of a column of three buttons: the column sat over the middle of the page being
 * framed. It shows the current zoom and a tap moves to the next stop (1x, 2x, 5x); pinching still works.
 * Its own composable on purpose: a pinch changes the zoom every frame, and reading it at the call site
 * recomposed the whole camera screen for each step; here only this pill recomposes.
 */
@Composable
private fun ZoomPill(zoomRatio: () -> Float, maxZoomRatio: Float, onZoomChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val ratio = zoomRatio()
    val stops = listOf(1f, 2f, 5f).filter { it <= maxZoomRatio + 0.01f || it == 1f }
    val label = stops.firstOrNull { kotlin.math.abs(ratio - it) < 0.01f }?.let { "${it.toInt()}x" }
        ?: String.format(java.util.Locale.ROOT, "%.1fx", ratio)
    val description = stringResource(R.string.scan_zoom_level, label)
    Surface(
        onClick = { onZoomChange(stops.firstOrNull { it > ratio + 0.01f } ?: stops.first()) },
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.45f),
        contentColor = Color.White,
        modifier = modifier.size(44.dp).semantics { contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

/** The square pills either side of the shutter (gallery; Auto or the page count). */
@Composable
private fun BottomPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.1f),
    contentColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = color,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = if (color == Color.White.copy(alpha = 0.1f)) 0.5f else 0f)),
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).alpha(if (enabled) 1f else 0.5f)
    ) {
        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** One of the mode pills above the shutter. */
/** One line on what a scan mode does, shown when it is chosen. */
@StringRes
private fun modeHint(mode: ScanMode): Int = when (mode) {
    ScanMode.DOCUMENT -> R.string.scan_hint_document
    ScanMode.RECEIPT -> R.string.scan_hint_receipt
    ScanMode.WHITEBOARD -> R.string.scan_hint_whiteboard
    ScanMode.ID_CARD -> R.string.scan_hint_id_card
    ScanMode.BOOK -> R.string.scan_hint_book
    ScanMode.TEXT -> R.string.scan_hint_text
}

@Composable
private fun ModeTab(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // The chosen mode scrolls into view. Seven modes do not fit a phone's width, so a camera opened in a mode from
    // further along (QR code or Book from Home, ID card from the widget, a default mode from Settings) showed the
    // start of the strip with nothing underlined.
    val intoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            // One frame first, so the strip has been laid out when this runs for a mode chosen before it opened.
            androidx.compose.runtime.withFrameNanos { }
            runCatching { intoView.bringIntoView() }
        }
    }
    Column(
        Modifier
            .bringIntoViewRequester(intoView)
            // As wide as the label: a fillMaxWidth underline in a plain Row otherwise made the first tab take the whole
            // row, and "Both sides" was pushed off screen (only "Front only" showed in ID card mode).
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color.White.copy(alpha = if (enabled) 0.6f else 0.3f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(Modifier.height(2.dp).fillMaxWidth().background(if (selected) TorchOn else Color.Transparent))
    }
}

@Composable
private fun PermissionPanel(askable: Boolean, onRequest: () -> Unit, onFallback: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    // Scrolls, clear of the system bars (2026-11-18): in landscape or at a large font the last buttons were cut off.
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (askable) R.string.scan_permission_body else R.string.scan_permission_denied_body))
                Spacer(Modifier.height(16.dp))
                // After "Don't ask again" the system dialog no longer appears; settings is the only way back.
                if (askable) Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_permission_allow)) }
                else Button(onClick = openSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_permission_open_settings)) }
                if (askable) TextButton(onClick = openSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_permission_open_settings)) }
                OutlinedButton(onClick = onFallback, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(stringResource(R.string.scan_permission_use_compat)) }
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

/** The ID card guide's rectangle in a camera area of [width] x [height] px: card-shaped, a little above centre. */
internal fun idCardFrame(width: Float, height: Float, density: Float, heightRatio: Float = IdCardKind.ID1_RATIO): FrameRect {
    val w = width * 0.88f
    val h = w * heightRatio
    val left = (width - w) / 2f
    val top = (height - h) / 2f - 40f * density
    return FrameRect(left, top, left + w, top + h)
}

/**
 * What ID card mode captures. Cards are ID-1 (85.6 x 54 mm); a passport photo page is ID-3 (125 x 88 mm), so its guide
 * is a little taller. Both sides put the front above the back on one page ([IdCardComposer]).
 */
internal enum class IdCardKind(@StringRes val labelRes: Int, val heightRatio: Float) {
    ONE_SIDE(R.string.scan_id_front_only, 0.63f),
    BOTH_SIDES(R.string.scan_id_both_sides, 0.63f),
    PASSPORT(R.string.scan_id_passport, 88f / 125f);

    companion object {
        /** Card height / width. 0.63 rather than 54 / 85.6 so the guide is unchanged from before. */
        const val ID1_RATIO = 0.63f
        const val ID3_RATIO = 88f / 125f
    }
}

internal data class FrameRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width() = right - left
    fun height() = bottom - top
}

/**
 * Where the ID card guide lies in a photo of [imageWidth] x [imageHeight] (already upright) shown fitted and centred
 * in a [viewWidth] x [viewHeight] camera area, as the photo's crop: only the card inside the guide is kept.
 */
internal fun idCardCrop(
    viewWidth: Float,
    viewHeight: Float,
    imageWidth: Int,
    imageHeight: Int,
    density: Float,
    heightRatio: Float = IdCardKind.ID1_RATIO
): CropQuad? {
    if (viewWidth <= 0f || viewHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) return null
    val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
    val shownW = imageWidth * scale
    val shownH = imageHeight * scale
    val offX = (viewWidth - shownW) / 2f
    val offY = (viewHeight - shownH) / 2f
    val frame = idCardFrame(viewWidth, viewHeight, density, heightRatio)
    fun nx(x: Float) = ((x - offX) / shownW).coerceIn(0f, 1f)
    fun ny(y: Float) = ((y - offY) / shownH).coerceIn(0f, 1f)
    val l = nx(frame.left); val r = nx(frame.right); val t = ny(frame.top); val b = ny(frame.bottom)
    if (r - l < 0.05f || b - t < 0.05f) return null
    return CropQuad(
        topLeft = NormalizedPoint(l, t), topRight = NormalizedPoint(r, t),
        bottomRight = NormalizedPoint(r, b), bottomLeft = NormalizedPoint(l, b)
    )
}

/**
 * Credit-card shaped guide with corner brackets, plus a Front/Back label. Everything outside the guide is blacked
 * out, since only what is inside it is kept (see [idCardCrop]).
 */
@Composable
private fun IdCardFrame(label: String, heightRatio: Float) {
    val motion = com.example.scanpro.core.designsystem.rememberMotionEnabled()
    // Switching Front only / Both sides / Passport morphs the guide between the two card shapes instead of
    // snapping, and the whole guide settles in with a soft spring when the mode opens.
    val shownRatio by androidx.compose.animation.core.animateFloatAsState(
        heightRatio,
        if (motion) spring(dampingRatio = 0.8f, stiffness = 380f) else androidx.compose.animation.core.snap(),
        label = "idRatio"
    )
    var entered by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { entered = true }
    val appear by androidx.compose.animation.core.animateFloatAsState(
        if (entered) 1f else 0f,
        if (motion) spring(dampingRatio = 0.85f, stiffness = 300f) else androidx.compose.animation.core.snap(),
        label = "idAppear"
    )
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear }) {
        Canvas(Modifier.fillMaxSize()) {
            val frame = idCardFrame(size.width, size.height, density, shownRatio)
            val left = frame.left; val top = frame.top; val width = frame.width(); val height = frame.height()
            val radius = 14.dp.toPx()
            val outside = Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + width, top + height, radius, radius))
            }
            drawPath(outside, Color.Black.copy(alpha = 0.88f))
            val arm = 28.dp.toPx()
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(Color.White, Offset(x, y), Offset(x + dx * arm, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(Color.White, Offset(x, y), Offset(x, y + dy * arm), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            corner(left, top, 1f, 1f)
            corner(left + width, top, -1f, 1f)
            corner(left, top + height, 1f, -1f)
            corner(left + width, top + height, -1f, -1f)
        }
        // The hint and the side label sit inside the guide (hint near its top edge, label in its middle), placed from the
        // same geometry as the brackets. They used fixed offsets from the screen and overlapped once the viewfinder got
        // smaller (F150) or on short screens.
        val local = androidx.compose.ui.platform.LocalDensity.current
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        Box(Modifier.fillMaxSize().onSizeChanged { areaSize = it })
        if (areaSize.width > 0) {
            val frame = idCardFrame(areaSize.width.toFloat(), areaSize.height.toFloat(), local.density, shownRatio)
            Box(
                Modifier
                    .offset { IntOffset(frame.left.toInt(), frame.top.toInt()) }
                    .size(with(local) { frame.width().toDp() }, with(local) { frame.height().toDp() })
            ) {
                Text(
                    stringResource(R.string.scan_id_fit_inside),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp, start = 16.dp, end = 16.dp)
                )
                Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.45f)) {
                    // "Front · 1 of 2" turns into "Back · 2 of 2" with a small rise, so the change of side is seen.
                    androidx.compose.animation.AnimatedContent(
                        targetState = label,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(tween(200)) + androidx.compose.animation.slideInVertically(tween(200)) { it / 2 })
                                .togetherWith(androidx.compose.animation.fadeOut(tween(140)))
                        },
                        label = "idSide"
                    ) { text ->
                        Text(text, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
}

/** File deletes happen off the main thread; the UI never waits on the disk. */
private fun deleteInBackground(file: File) {
    Thread { file.delete() }.start()
}

/** Flash setting for the shutter; torch is the steady light the low-light hint offers. */
enum class FlashMode(@StringRes val labelRes: Int) {
    OFF(R.string.scan_flash_off), AUTO(R.string.scan_flash_auto), ON(R.string.scan_flash_on), TORCH(R.string.scan_flash_torch)
}

/**
 * Spirit level: the bubble drifts with the phone's lean and the ring turns mint when the phone is flat
 * over the page (within a few degrees), so a skewed shot is noticed before it is taken.
 */
@Composable
private fun LevelBubble(tilt: () -> Offset, accent: Color, modifier: Modifier = Modifier) {
    // [tilt] is read only inside the draw block: a new reading (dozens a second) redraws this one
    // bubble and recomposes nothing. The mint blend is smoothed in the same place for the same reason.
    val blend = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { tilt().let { kotlin.math.abs(it.x) < 6f && kotlin.math.abs(it.y) < 6f } }
            .collectLatest { level -> blend.animateTo(if (level) 1f else 0f, tween(180)) }
    }
    Canvas(modifier.size(44.dp)) {
        val lean = tilt()
        val ring = androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.7f), accent, blend.value)
        val radius = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), radius)
        drawCircle(ring, radius - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
        drawCircle(ring.copy(alpha = 0.5f), 5.dp.toPx(), style = Stroke(1.dp.toPx()))
        // Up to 25 degrees of lean moves the bubble to the rim.
        val reach = radius - 7.dp.toPx()
        val dx = (lean.x / 25f).coerceIn(-1f, 1f) * reach
        val dy = (-lean.y / 25f).coerceIn(-1f, 1f) * reach
        drawCircle(ring, 5.dp.toPx(), center = center + Offset(dx, dy))
    }
}

/** A page removed from the strip, held until Undo or the timeout (R30). */
private class PendingDelete(val uri: Uri, val index: Int, val quad: CropQuad?, val filter: PageFilter?, val adjustments: PageAdjustments?, val wasComposite: Boolean)

/**
 * The pages so far (R30, after Adobe Scan and Genius Scan): each drawn cut out and filtered the way it will be saved,
 * redrawn when its outline arrives from the background, with an amber mark on a page that needs a look (no outline
 * found, blurred, glare or too dark). Tap opens the review on it; a long press removes it, with Undo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CapturedStrip(
    pages: List<Uri>,
    quads: Map<String, CropQuad>,
    filters: Map<String, PageFilter>,
    quality: Map<String, Set<PageIssue>>,
    sessionFilter: PageFilter?,
    onOpen: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    val cache = remember { HashMap<String, Bitmap>() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val deleteHint = stringResource(R.string.scan_strip_delete_hint)
    LaunchedEffect(pages.size) { if (pages.isNotEmpty()) listState.animateScrollToItem(pages.lastIndex) }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(2.dp)
    ) {
        itemsIndexed(pages, key = { _, uri -> uri.toString() }, contentType = { _, _ -> "captured_thumb" }) { index, uri ->
            val key = uri.toString()
            val quad = quads[key]
            val filter = filters[key] ?: sessionFilter ?: PageFilter.AUTO
            val issues = quality[key].orEmpty()
            val flagged = (quad != null && quad.isIdentity()) || issues.isNotEmpty()
            val pageLabel = if (flagged) {
                stringResource(R.string.scan_quality_page_flagged, index + 1)
            } else {
                stringResource(R.string.scan_review_page_n, index + 1)
            }
            // Keyed on the outline and filter too, so the thumbnail is redrawn when either changes.
            val cacheKey = "$key#${quad?.let { "${it.topLeft},${it.topRight},${it.bottomRight},${it.bottomLeft}" }}#$filter"
            val bitmap by produceState<Bitmap?>(initialValue = cache[cacheKey], cacheKey) {
                if (value == null) {
                    value = withContext(Dispatchers.IO) {
                        val file = File(uri.path.orEmpty())
                        if (!file.exists()) return@withContext null
                        runCatching {
                            ImageProcessor.renderPreview(
                                source = file,
                                filter = filter,
                                manualRotationDegrees = 0,
                                crop = quad ?: CropQuad.DEFAULT,
                                maxDimension = 160
                            )
                        }.getOrNull() ?: runCatching {
                            ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 160)
                        }.getOrNull()
                    }
                    value?.let { cache[cacheKey] = it }
                }
            }
            Box(
                Modifier
                    // A new page fades in at the end and a removed one fades out while its neighbours close the gap (F146).
                    .animateItem()
                    .size(48.dp, 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.5.dp, if (flagged) QualityAmber else Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { onOpen(index) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete(index)
                        }
                    )
                    .semantics {
                        contentDescription = "$pageLabel. $deleteHint"
                        customActions = listOf(
                            CustomAccessibilityAction(deleteHint) {
                                onDelete(index)
                                true
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (flagged) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = QualityAmber,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .size(14.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(1.5.dp)
                    )
                }
                Text(
                    "${index + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topStart = 6.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Rule-of-thirds lines that help keep the document square to the camera. They are laid on the camera image itself (a 4:3
 * picture fitted and centred on the screen), not on the whole screen, so the thirds line up with what is saved. Each
 * line is a light stroke over a dark one: a single faint white line vanished on a white page.
 */
@Composable
private fun ThirdsGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val aspect = if (size.height >= size.width) 3f / 4f else 4f / 3f
        val width = minOf(size.width, size.height * aspect)
        val height = width / aspect
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val shadow = Color.Black.copy(alpha = 0.45f)
        val light = Color.White.copy(alpha = 0.9f)
        val thin = 1.dp.toPx()
        val wide = 3.dp.toPx()
        for (i in 1..2) {
            val x = left + width * i / 3f
            val y = top + height * i / 3f
            drawLine(shadow, Offset(x, top), Offset(x, top + height), strokeWidth = wide)
            drawLine(light, Offset(x, top), Offset(x, top + height), strokeWidth = thin)
            drawLine(shadow, Offset(left, y), Offset(left + width, y), strokeWidth = wide)
            drawLine(light, Offset(left, y), Offset(left + width, y), strokeWidth = thin)
        }
    }
}

/** Preference key that silences a mode's first-use explainer; null for modes without one. Book keeps its original key. */
private fun ScanMode.introPrefKey(): String? = when (this) {
    ScanMode.BOOK -> "book_intro_off"
    ScanMode.ID_CARD -> "idcard_intro_off"
    ScanMode.DOCUMENT -> "document_intro_off"
    else -> null
}

/** Settings > Show introductions again (F146): the mode explainers show again even where "Don't remind me again" was ticked. */
internal fun resetScanIntros(context: android.content.Context) {
    val keys = ScanMode.entries.mapNotNull { it.introPrefKey() }
    context.getSharedPreferences("scanner_prefs", android.content.Context.MODE_PRIVATE).edit { keys.forEach { remove(it) } }
}

/**
 * What a scan mode does, for someone using it for the first time (F146, rebuilt 2026-11-16 with clear pictures). Document
 * mode is "How to scan": the built-in sample page on a desk, with Skip and Try now ([onTry] runs the sample through the
 * normal review); without [onTry] the main button is Start now. ID card shows a card's front and back; Book an open
 * book with the A / B halves the viewfinder shows. Each shows once (remembered the moment it appears), with no tick box.
 */
@Composable
internal fun ScanIntroDialog(mode: ScanMode, onTry: (() -> Unit)? = null, onStart: () -> Unit) {
    val sample = mode != ScanMode.BOOK && mode != ScanMode.ID_CARD
    val (title, body) = when (mode) {
        ScanMode.BOOK -> R.string.scan_book_intro_title to R.string.intro_book_body
        ScanMode.ID_CARD -> R.string.scan_idcard_intro_title to R.string.intro_idcard_body
        else -> R.string.intro_doc_title to R.string.intro_doc_body
    }
    val frontLabel = stringResource(R.string.scan_id_side_front)
    val backLabel = stringResource(R.string.scan_id_side_back)
    com.example.scanpro.core.designsystem.IllustratedDialog(
        title = stringResource(title),
        body = stringResource(body),
        pictureHeight = 236.dp,
        illustration = {
            val art = Modifier.fillMaxSize()
            when (mode) {
                ScanMode.BOOK -> com.example.scanpro.core.designsystem.OpenBookArt(art)
                ScanMode.ID_CARD -> com.example.scanpro.core.designsystem.IdCardArt(frontLabel, backLabel, art)
                else -> com.example.scanpro.core.designsystem.SampleDocumentArt(art)
            }
        },
        confirmText = stringResource(if (sample && onTry != null) R.string.intro_doc_try else R.string.scan_book_intro_start),
        onConfirm = { if (sample && onTry != null) onTry() else onStart() },
        dismissText = if (sample && onTry != null) stringResource(R.string.hint_skip) else null,
        buttonsInRow = sample && onTry != null,
        onDismiss = onStart
    )
}

/**
 * The page outline over the preview: a faint fill, an edge line and bracket corners. White while the page
 * is being tracked; mint, thicker and filled a touch more once it is steady. [quad] is the smoothed outline, read
 * while drawing so a new one each camera frame redraws only this outline;
 * [detection] supplies the frame size the outline is expressed in.
 */
@Composable
private fun DocumentBoundaryOverlay(detection: DocumentDetection?, quad: () -> CropQuad?, stable: Boolean) {
    val lineColor by androidx.compose.animation.animateColorAsState(if (stable) PageOutlineBlue else PageOutlineBlueSoft, tween(220), label = "outline")
    val fillAlpha by androidx.compose.animation.core.animateFloatAsState(if (stable) 0.16f else 0.07f, tween(220), label = "fill")
    // Stroke widths in dp (2026-11-18): they were raw pixels, under 1dp on a 3x phone, beside 4-5dp QR and ID marks.
    val weight by androidx.compose.animation.core.animateFloatAsState(if (stable) 3f else 2f, tween(220), label = "weight")
    Canvas(Modifier.fillMaxSize()) {
        val result = detection ?: return@Canvas
        val outline = quad() ?: return@Canvas
        val frameWidth = result.frameWidth.toFloat().coerceAtLeast(1f)
        val frameHeight = result.frameHeight.toFloat().coerceAtLeast(1f)
        val scale = minOf(size.width / frameWidth, size.height / frameHeight)
        val left = (size.width - frameWidth * scale) / 2f
        val top = (size.height - frameHeight * scale) / 2f

        fun point(x: Float, y: Float): Offset = Offset(left + x * frameWidth * scale, top + y * frameHeight * scale)
        val points = outline.asList().map { point(it.x, it.y) }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        drawPath(path, color = lineColor.copy(alpha = fillAlpha))
        drawPath(path, color = lineColor.copy(alpha = 0.9f), style = Stroke(width = weight.dp.toPx(), cap = StrokeCap.Round))

        // Bracket corners: a short run along each edge away from the corner, plus anchor dot on lock.
        val armLength = (size.minDimension * 0.052f).coerceIn(16.dp.toPx(), 32.dp.toPx())
        for (i in points.indices) {
            val corner = points[i]
            val next = points[(i + 1) % 4]
            val previous = points[(i + 3) % 4]
            fun arm(toward: Offset) {
                val dx = toward.x - corner.x
                val dy = toward.y - corner.y
                val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                val end = Offset(corner.x + dx / length * armLength, corner.y + dy / length * armLength)
                drawLine(lineColor, corner, end, strokeWidth = (weight + 1.5f).dp.toPx(), cap = StrokeCap.Round)
            }
            arm(next)
            arm(previous)
            if (stable) {
                drawCircle(Color.White, radius = 4.dp.toPx(), center = corner)
                drawCircle(lineColor, radius = 2.5.dp.toPx(), center = corner)
            }
        }
    }
}

private fun CropQuad.asList() = listOf(topLeft, topRight, bottomRight, bottomLeft)

/** What became of one photo picked from the gallery. */
internal sealed interface PickResult {
    data class Copied(val uri: Uri) : PickResult
    /** HEIC on Android 8 or older, which can't decode it. */
    data object Heic : PickResult
    /** Missing, unreadable or not an image. */
    data object Unreadable : PickResult
}

/**
 * Copies a pick into the session folder: the picker's read grant ends with the process, so pages restored after
 * process death would otherwise be unreadable. A photo that can't be decoded is removed again and reported.
 */
private fun copyPick(context: android.content.Context, uri: Uri, sessionDir: File): PickResult {
    val target = File(sessionDir, "pick_${UUID.randomUUID()}.jpg")
    val opened = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
    }.getOrNull()
    if (opened == null) {
        target.delete()
        return PickResult.Unreadable
    }
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(target.absolutePath, bounds)
    if (bounds.outWidth > 0) {
        // A mirrored photo (a selfie, some gallery apps) is stood upright here, once, before any page is made from it.
        ImageProcessor.bakeMirroredOrientation(target)
        return PickResult.Copied(Uri.fromFile(target))
    }
    target.delete()
    // Picker URIs carry no extension and old decoders leave outMimeType null, so ask the provider for the type too.
    val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val heic = listOfNotNull(bounds.outMimeType, type, uri.toString()).any { it.contains("hei", ignoreCase = true) }
    return if (heic) PickResult.Heic else PickResult.Unreadable
}

/** One line on the photos that were left out of a gallery pick, or null when every photo was added. */
internal fun skippedPicksMessage(resources: android.content.res.Resources, heic: Int, unreadable: Int, overLimit: Int, limit: Int): String? {
    val parts = buildList {
        if (heic > 0) add(resources.getQuantityString(R.plurals.scan_pick_skipped_heic, heic, heic))
        if (unreadable > 0) add(resources.getQuantityString(R.plurals.scan_pick_skipped_unreadable, unreadable, unreadable))
        if (overLimit > 0) add(resources.getQuantityString(R.plurals.scan_pick_skipped_limit, overLimit, overLimit, limit))
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/** Sub-folder holding one folder per scanner session; cleaned after import or when abandoned. */
/** How long a changed live hint must hold before it replaces the one on screen. */
private const val GUIDANCE_HOLD_MS = 400L

/** About the length of the camera's slide-up (see the navigation host): the first bind starts after it. */
private const val CAMERA_BIND_DELAY_MS = 240L

internal const val SESSION_ROOT = "pro_scanner_session"

/** Marker in a session folder whose photos are queued for import: cleanup must leave the folder alone. */
internal const val SESSION_PENDING_IMPORT = ".pending_import"
/** The import request as WorkManager data, so a kept scan can be saved again with the user's edits. */
internal const val SESSION_IMPORT_REQUEST = ".import_request"
/** Written when every automatic import attempt failed; holds the last error message. */
internal const val SESSION_IMPORT_FAILED = ".import_failed"

/**
 * Where scanner sessions live: in the app's files, not its cache. Android may clear the cache whenever storage
 * runs low, and a finished scan waiting for its import exists nowhere else.
 */
internal fun scannerSessionRoot(context: android.content.Context) = File(context.filesDir, SESSION_ROOT)

/** Raw photos whose shutter finished during a rotation, by scanner session id; the recreated screen adopts them. */
private val lateCaptures = kotlinx.coroutines.flow.MutableStateFlow<Map<String, List<String>>>(emptyMap())

/**
 * Holds the single observer of a camera's state. Each bind of the camera used to add another observer to its own
 * LiveData and none was ever removed, so stale observers of earlier bindings kept writing the screen's error text.
 */
private class CameraStateWatch {
    private var data: androidx.lifecycle.LiveData<androidx.camera.core.CameraState>? = null
    private var observer: androidx.lifecycle.Observer<androidx.camera.core.CameraState>? = null

    fun watch(
        source: androidx.lifecycle.LiveData<androidx.camera.core.CameraState>,
        owner: androidx.lifecycle.LifecycleOwner,
        onState: (androidx.camera.core.CameraState) -> Unit
    ) {
        stop()
        val next = androidx.lifecycle.Observer<androidx.camera.core.CameraState> { onState(it) }
        data = source
        observer = next
        source.observe(owner, next)
    }

    fun stop() {
        val old = observer
        if (old != null) data?.removeObserver(old)
        data = null
        observer = null
    }
}
