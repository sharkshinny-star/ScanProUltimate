package com.example.scanpro.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import com.example.scanpro.imaging.ImageProcessor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.defaultFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** How the live preview helps: Auto captures by itself, Manual only outlines the page, No detect does neither. */
private enum class DetectMode(val label: String) { AUTO("Auto"), MANUAL("Manual"), NO_DETECT("No detect") }

@Composable
fun CameraScannerScreen(
    /** Mode to open in: a [ScanMode] name, "QR", or empty for the usual document mode. */
    initialMode: String = "",
    /** Position of the saved page this session replaces; the session then takes exactly one photo. */
    retakeOfPage: Int? = null,
    onClose: () -> Unit,
    onComplete: (List<Uri>, ScanMode, List<CropQuad>, List<PageFilter>) -> Unit,
    onFallbackScanner: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
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

    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var detection by remember { mutableStateOf<DocumentDetection?>(null) }
    var stable by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("scanner_prefs", android.content.Context.MODE_PRIVATE) }
    var detectMode by rememberSaveable {
        mutableStateOf(runCatching { DetectMode.valueOf(prefs.getString("detect", null).orEmpty()) }.getOrDefault(DetectMode.AUTO))
    }
    var detectMenuOpen by remember { mutableStateOf(false) }
    // A few tips the first time the camera opens; "Got it" hides them for good.
    var showTips by remember { mutableStateOf(!prefs.getBoolean("tips_seen", false)) }
    fun dismissTips() {
        showTips = false
        prefs.edit().putBoolean("tips_seen", true).apply()
    }
    var overflowOpen by remember { mutableStateOf(false) }
    var showAdjustments by remember { mutableStateOf(false) }
    val autoCapture = detectMode == DetectMode.AUTO
    // Single finishes after one capture; Batch keeps the camera open for more pages.
    var batch by rememberSaveable { mutableStateOf(prefs.getBoolean("batch", false)) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var zoom by remember { mutableFloatStateOf(0f) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureMin by remember { mutableIntStateOf(0) }
    var exposureMax by remember { mutableIntStateOf(0) }
    var capturing by remember { mutableStateOf(false) }
    // `status` holds short-lived feedback (captured, errors); live detection guidance is kept separately
    // so the per-frame analyzer callback cannot overwrite it.
    var status by remember { mutableStateOf<String?>(null) }
    var liveGuidance by remember { mutableStateOf("Point the camera at a document") }
    val disposed = remember { AtomicBoolean(false) }
    var autoCaptureSignal by remember { mutableIntStateOf(0) }
    // Saveable, so captured pages survive rotation and process death instead of being lost (and
    // their files orphaned) when the activity is recreated.
    var scanMode by rememberSaveable { mutableStateOf(ScanMode.entries.firstOrNull { it.name == initialMode } ?: ScanMode.DOCUMENT) }
    var scanBothSides by rememberSaveable { mutableStateOf(false) }
    val pages = rememberSaveable(
        saver = listSaver(save = { list -> list.map(Uri::toString) }, restore = { saved -> mutableStateListOf(*saved.map(Uri::parse).toTypedArray()) })
    ) { mutableStateListOf<Uri>() }
    // Each scanner session writes to its own folder, so closing one never deletes files another
    // session's import is still reading.
    val sessionId = rememberSaveable { UUID.randomUUID().toString() }
    val sessionDir = remember(sessionId) { File(File(context.cacheDir, SESSION_ROOT), sessionId) }
    var completed by remember { mutableStateOf(false) }
    // The review step after capture: crop, rotate, reorder and add pages before saving.
    var reviewing by rememberSaveable { mutableStateOf(false) }
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
    // Starts from the mode's usual look; the last filter picked becomes the default for the rest of the session.
    var sessionFilter by rememberSaveable { mutableStateOf<PageFilter?>(null) }
    // Bumped on every resume: CameraX resets torch, zoom and exposure when the camera restarts.
    var resumeCount by remember { mutableIntStateOf(0) }
    val pageLimit = when {
        retakeOfPage != null -> 1
        scanMode == ScanMode.ID_CARD -> if (scanBothSides) 2 else 1
        else -> 100
    }

    var confirmQuit by remember { mutableStateOf(false) }
    // Closing with captured pages would discard them, so ask first.
    val retaking = retakeIndex in pages.indices
    val requestClose = {
        when {
            retaking -> { retakeIndex = -1; reviewing = true }
            pages.isNotEmpty() && !completed -> confirmQuit = true
            else -> onClose()
        }
    }
    BackHandler(enabled = pages.isNotEmpty() && !completed) { requestClose() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = (pageLimit - pages.size).coerceAtLeast(0)
            scope.launch {
                // Copy picks into the session folder: the picker's read grant ends with the process,
                // so pages restored after process death would otherwise be unreadable.
                val copied = withContext(Dispatchers.IO) {
                    sessionDir.mkdirs()
                    uris.take(remaining).mapNotNull { uri ->
                        runCatching {
                            val target = File(sessionDir, "pick_${UUID.randomUUID()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                target.outputStream().use { input.copyTo(it) }
                            } ?: error("Could not open image")
                            Uri.fromFile(target)
                        }.getOrNull()
                    }
                }
                pages.addAll(copied)
                status = when {
                    scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> "Front ready · capture the back"
                    pages.isNotEmpty() -> "${pages.size} page(s) ready"
                    else -> "Select a document to scan"
                }
            }
        }
    }

    val analyzer = remember {
        OpenCvDocumentAnalyzer(
            onDetection = { result, isStable ->
                detection = result
                stable = isStable
                liveGuidance = if (isStable && result.isReady) "Hold… capturing" else result.guidance
            },
            onAutoCapture = { autoCaptureSignal++ }
        )
    }
    // Taking a page by hand while the tips show means they have done their job.
    LaunchedEffect(pages.size) { if (showTips && pages.isNotEmpty()) dismissTips() }

    // QR mode reads codes from the same camera frames instead of looking for a page.
    var barcodeMode by rememberSaveable { mutableStateOf(initialMode == "QR") }
    var foundCode by remember { mutableStateOf<com.google.mlkit.vision.barcode.common.Barcode?>(null) }
    var barcodeUnavailable by remember { mutableStateOf(false) }
    val barcodeAnalyzer = remember {
        BarcodeFrameAnalyzer(
            context = context.applicationContext,
            onFound = { code ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                foundCode = code
            },
            onUnavailable = { barcodeUnavailable = true }
        )
    }
    val frameAnalyzer = remember { SwitchingAnalyzer(analyzer, barcodeAnalyzer) }
    SideEffect {
        frameAnalyzer.barcodeMode = barcodeMode
        // No auto-capture while the first-time tips are being read.
        analyzer.autoCaptureEnabled = autoCapture && !showTips && !barcodeMode && !capturing && !reviewing && (retaking || pages.size < pageLimit)
    }

    /** Opens the review step on the newest page. */
    fun finishScan() {
        if (!completed && pages.isNotEmpty()) {
            reviewIndex = pages.lastIndex
            reviewing = true
        }
    }

    /** Applies each page's confirmed crop, then hands the finished images to the importer. */
    fun saveReviewed() {
        // Ignore a second tap during the exit animation; it would import twice.
        if (completed || processing || pages.isEmpty()) return
        processing = true
        val inputs = pages.toList().map { it to quads[it.toString()] }
        val chosen = pages.map { filters[it.toString()] ?: sessionFilter ?: scanMode.defaultFilter }
        scope.launch {
            // The untouched originals are kept and the confirmed crop travels beside them, so the border can
            // still be widened later; nothing here cuts pixels out of the photo.
            val crops = withContext(Dispatchers.Default) {
                inputs.map { (uri, quad) ->
                    quad ?: runCatching { OpenCvScanProcessor.detectCrop(File(uri.path.orEmpty())) }.getOrDefault(CropQuad.DEFAULT)
                }
            }
            completed = true
            processing = false
            onComplete(inputs.map { it.first }, scanMode, crops, chosen)
        }
    }

    // Capture feedback: a quick white flash, then the photo shrinks into the page counter, which gives a bounce.
    val flash = remember { Animatable(0f) }
    val flight = remember { Animatable(0f) }
    val counterPulse = remember { Animatable(1f) }
    var flyingPage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
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
        if (capturing || pages.size >= pageLimit) return
        capturing = true
        status = "Capturing…"
        scope.launch {
            flash.snapTo(0.8f)
            flash.animateTo(0f, tween(280))
        }

        sessionDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val raw = File(sessionDir, "raw_$id.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(raw).build()

        capture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // The screen was closed while the shutter was running; launching on its cancelled
                    // scope would skip the cleanup below, so drop the raw file here.
                    if (disposed.get()) {
                        raw.delete()
                        return
                    }
                    // Keep the raw photo: cropping happens in the review step, from the full-size original.
                    val captured = Uri.fromFile(raw)
                    val replacing = retakeIndex in pages.indices
                    if (replacing) {
                        val old = pages[retakeIndex]
                        pages[retakeIndex] = captured
                        quads.remove(old.toString())
                        filters.remove(old.toString())
                        if (old.scheme == "file") File(old.path.orEmpty()).delete()
                    } else {
                        pages.add(captured)
                    }
                    capturing = false
                    // Outline the page in the background so the review screen opens ready.
                    scope.launch {
                        val quad = withContext(Dispatchers.Default) { runCatching { OpenCvScanProcessor.detectCrop(raw) }.getOrNull() }
                        if (quad != null && quads[captured.toString()] == null && pages.contains(captured)) quads[captured.toString()] = quad
                    }
                    status = when {
                        replacing -> "Page ${retakeIndex + 1} retaken"
                        scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> "Front saved · now capture the back"
                        scanMode == ScanMode.ID_CARD && pages.size == pageLimit -> "ID capture ready · tap Done"
                        else -> "Page ${pages.size} captured"
                    }
                    if (replacing) {
                        // Straight back to the page that was retaken, so it can be checked at once.
                        reviewIndex = retakeIndex
                        retakeIndex = -1
                        reviewing = true
                    } else if (retakeOfPage != null || (!batch && scanMode != ScanMode.ID_CARD)) finishScan() else flyIntoCounter(raw)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    status = exception.message ?: "Could not capture page"
                }
            }
        )
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(1.seconds)
            focusPoint = null
        }
    }

    LaunchedEffect(status) {
        if (status != null) {
            delay(3.seconds)
            status = null
        }
    }

    LaunchedEffect(autoCaptureSignal) {
        if (autoCaptureSignal > 0 && autoCapture && !capturing && !reviewing && (retaking || pages.size < pageLimit)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            capturePage()
        }
    }

    LaunchedEffect(permissionGranted, lifecycleOwner) {
        if (!permissionGranted) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // The screen may have been left before the camera provider became ready.
            if (disposed.get()) return@addListener
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                // The same 4:3 field of view for preview, capture and analysis, so what the overlay outlines is what is saved.
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setResolutionSelector(selector)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(98)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, frameAnalyzer) }

                provider.unbindAll()
                // A shared view port crops all three streams to one region, so analysis coordinates line up with
                // the preview and the saved photo. It can be unavailable before the first layout; bind without it then.
                val viewPort = previewView.viewPort
                val camera = if (viewPort != null) {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(capture).addUseCase(analysis).build()
                    )
                } else {
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                }
                imageCapture = capture
                boundCamera = camera
                camera.cameraInfo.zoomState.value?.let { zoom = it.linearZoom }
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
                liveGuidance = "Camera unavailable. Use compatibility scanner."
            }
        }, mainExecutor)
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            barcodeAnalyzer.close()
            // Leaving without Done discards the session's captures. A rotation recreates the screen
            // with the same saved pages, so keep the files then.
            val changingConfigurations = (context as? android.app.Activity)?.isChangingConfigurations == true
            if (!completed && !changingConfigurations) sessionDir.deleteRecursively()
        }
    }

    // Clear out sessions abandoned by a crash or process death more than a day ago.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            File(context.cacheDir, SESSION_ROOT).listFiles()
                ?.filter { it.name != sessionId && it.lastModified() < cutoff }
                ?.forEach { it.deleteRecursively() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(torchEnabled, boundCamera, resumeCount) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(zoom, boundCamera, resumeCount) {
        boundCamera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    LaunchedEffect(exposureIndex, exposureMin, exposureMax, boundCamera, resumeCount) {
        if (exposureMin < exposureMax) {
            boundCamera?.cameraControl?.setExposureCompensationIndex(
                exposureIndex.coerceIn(exposureMin, exposureMax)
            )
        }
    }

    if (!permissionGranted) {
        PermissionPanel(
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
            defaultFilter = sessionFilter ?: scanMode.defaultFilter,
            onDefaultFilterChange = { sessionFilter = it },
            index = reviewIndex,
            sessionDir = sessionDir,
            processing = processing,
            onIndexChange = { reviewIndex = it },
            onAdd = { retakeIndex = -1; reviewing = false },
            onBack = { retakeIndex = -1; reviewing = false },
            onDone = ::saveReviewed,
            onRetake = {
                retakeIndex = reviewIndex.coerceIn(0, pages.lastIndex)
                status = "Retaking page ${retakeIndex + 1} · the next shot replaces it"
                reviewing = false
            }
        )
        return
    }

    // The scanner's chrome uses the dark theme's surfaces whatever the app theme, so the preview reads as the content.
    val scanLine = com.example.scanpro.core.designsystem.ScanProTheme.colors.scanLine
    Box(
        Modifier
            .fillMaxSize()
            .background(CameraBackdrop)
            .onGloballyPositioned { screenOrigin = it.positionInRoot(); screenSize = it.size }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
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

        ThirdsGrid()
        if (!barcodeMode && detectMode != DetectMode.NO_DETECT && scanMode != ScanMode.ID_CARD) {
            DocumentBoundaryOverlay(detection = detection, stable = stable)
        }
        if (scanMode == ScanMode.ID_CARD) {
            IdCardFrame(label = if (scanBothSides && pages.size == 1) "Back" else "Front")
        }
        focusPoint?.let { point ->
            Box(
                Modifier
                    .offset { IntOffset((point.x - 32.dp.toPx()).toInt(), (point.y - 32.dp.toPx()).toInt()) }
                    .size(64.dp)
                    .border(2.dp, Color(0xFF22D3EE), RoundedCornerShape(4.dp))
            )
        }

        // Live guidance in the middle of the preview, where the eye already is.
        if (showTips && !barcodeMode && pages.isEmpty()) {
            // Kept clear of the controls at the bottom; on short screens the tips scroll and "Got it" stays visible.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 20.dp, end = 20.dp)
                    .widthIn(max = 420.dp)
                    .heightIn(max = 320.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.8f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quick tips", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        listOf(
                            "Hold steady over the page. Once it is outlined and still, it is captured for you.",
                            "Tap to focus. Tap \"Auto\" at the top to capture by hand.",
                            "Many pages? Choose Batch above the shutter, then tap the page count to finish.",
                            "Later, Clean up removes shadows and fingers; Curve flattens book pages."
                        ).forEach { tip ->
                            Text("• $tip", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    TextButton(
                        onClick = ::dismissTips,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Got it") }
                }
            }
        }

        if (barcodeMode && foundCode == null) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                color = CameraGlass
            ) {
                Text(
                    if (barcodeUnavailable) "The QR reader is still downloading. Connect to the internet, then try again."
                    else "Point at a QR code or barcode",
                    color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
        foundCode?.let { code ->
            val scanAgain = {
                foundCode = null
                barcodeAnalyzer.resume()
            }
            BarcodeResultSheet(code, onScanAgain = scanAgain, onDismiss = scanAgain)
        }

        if (!showTips && !barcodeMode && status == null && detectMode != DetectMode.NO_DETECT && scanMode != ScanMode.ID_CARD) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                color = CameraGlass
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text(liveGuidance, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    // Low light: one tap turns the torch on from the hint itself.
                    if (detection?.isDark == true && !torchEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = { torchEnabled = true },
                            shape = RoundedCornerShape(50),
                            color = scanLine,
                            contentColor = Color(0xFF04281E)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Turn on flash", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (retakeOfPage != null && !showTips && status == null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
                shape = RoundedCornerShape(50),
                color = CameraGlass
            ) {
                Text("Retaking page ${retakeOfPage + 1} · the shot replaces it", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                title = { Text("Quit scanning?") },
                text = { Text("If you quit now, the photos you took will be discarded.") },
                confirmButton = { TextButton(onClick = { confirmQuit = false; onClose() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Cancel") } }
            )
        }

            if (scanMode != ScanMode.ID_CARD && !barcodeMode) {
                Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) {
                    Row(
                        Modifier.clickable { detectMenuOpen = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(detectMode.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Detection mode", tint = scanLine)
                    }
                    DropdownMenu(expanded = detectMenuOpen, onDismissRequest = { detectMenuOpen = false }) {
                        DetectMode.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option.label, color = if (option == detectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                },
                                onClick = { detectMode = option; detectMenuOpen = false; prefs.edit().putString("detect", option.name).apply() }
                            )
                        }
                    }
                }
            }
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = CameraGlass) {
                IconButton(onClick = requestClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = CameraGlass) {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(if (torchEnabled) Icons.Default.Bolt else Icons.Default.FlashOff, contentDescription = "Flash", tint = Color.White)
                    }
                }
                Box {
                    Surface(shape = CircleShape, color = CameraGlass) {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                        }
                    }
                    DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (showAdjustments) "Hide zoom & exposure" else "Zoom & exposure") },
                            onClick = { showAdjustments = !showAdjustments; overflowOpen = false }
                        )
                        if (pages.isEmpty() && scanMode != ScanMode.ID_CARD && scanMode != ScanMode.TEXT) {
                            listOf(ScanMode.DOCUMENT, ScanMode.RECEIPT, ScanMode.WHITEBOARD, ScanMode.BOOK).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label, color = if (scanMode == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scanMode = type; overflowOpen = false }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Compatibility scanner") },
                            onClick = { overflowOpen = false; onFallbackScanner() }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(CameraPanel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (status != null) {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f)) {
                    Text(status.orEmpty(), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                }
                Spacer(Modifier.height(8.dp))
            }

            if (scanMode != ScanMode.ID_CARD) {
                Row(
                    Modifier.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(3.dp)
                ) {
                    listOf(false to "Single", true to "Batch").forEach { (value, text) ->
                        Surface(
                            onClick = { batch = value; prefs.edit().putBoolean("batch", value).apply() },
                            shape = RoundedCornerShape(50),
                            color = if (batch == value) Color.White.copy(alpha = 0.28f) else Color.Transparent
                        ) {
                            Text(text, color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (showAdjustments) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1×", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Slider(value = zoom, onValueChange = { zoom = it }, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text("Zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                if (exposureMin < exposureMax) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("EV", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = exposureIndex.toFloat(),
                            onValueChange = { exposureIndex = it.toInt().coerceIn(exposureMin, exposureMax) },
                            valueRange = exposureMin.toFloat()..exposureMax.toFloat(),
                            steps = (exposureMax - exposureMin - 1).coerceAtLeast(0),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                        )
                        val exposureLabel = if (exposureIndex > 0) "+$exposureIndex" else exposureIndex.toString()
                        Text(exposureLabel, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Mode tabs. Receipt, Whiteboard and Book live in the overflow menu under "Docs".
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (scanMode == ScanMode.ID_CARD) {
                    listOf(false to "Front only", true to "Both sides").forEach { (both, text) ->
                        ModeTab(text, selected = scanBothSides == both, enabled = pages.isEmpty() && retakeOfPage == null) { scanBothSides = both }
                    }
                } else {
                    listOf(ScanMode.TEXT to "Text", null to "Docs", ScanMode.ID_CARD to "ID cards").forEach { (target, text) ->
                        val selected = !barcodeMode &&
                            if (target == null) scanMode != ScanMode.TEXT && scanMode != ScanMode.ID_CARD else scanMode == target
                        ModeTab(text, selected = selected, enabled = pages.isEmpty() && retakeOfPage == null) {
                            barcodeMode = false
                            scanMode = target ?: ScanMode.DOCUMENT
                            scanBothSides = false
                        }
                    }
                    ModeTab("QR code", selected = barcodeMode, enabled = pages.isEmpty() && retakeOfPage == null) {
                        barcodeMode = true
                        scanMode = ScanMode.DOCUMENT
                        scanBothSides = false
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                    if (pages.isNotEmpty()) {
                        Column(
                            Modifier.clickable { pages.removeLastOrNull()?.let { uri -> if (uri.scheme == "file") File(uri.path.orEmpty()).delete() } }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Remove last page", tint = Color.White)
                            Text("Back", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Outer ring turns mint once the page is outlined and still; the inner disc is the button.
                Box(
                    Modifier
                        .size(84.dp)
                        .border(4.dp, if (stable) scanLine else Color.White.copy(alpha = 0.45f), CircleShape)
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
                        color = if (barcodeMode) Color.White.copy(alpha = 0.25f) else Color.White,
                        // The shutter has no visible label, so screen readers need one.
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Take photo" }
                    ) {}
                }

                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                    if (pages.isEmpty() && !barcodeMode) {
                        Column(
                            Modifier.clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Import photos", tint = Color.White)
                            Text("Import images", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(
                            enabled = !capturing && (scanMode != ScanMode.ID_CARD || pages.size == pageLimit),
                            onClick = { finishScan() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = scanLine, contentColor = Color(0xFF04281E)),
                            modifier = Modifier
                                .onGloballyPositioned { counterCenter = it.boundsInRoot().center }
                                .graphicsLayer { scaleX = counterPulse.value; scaleY = counterPulse.value }
                        ) {
                            Icon(Icons.Default.TaskAlt, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("${pages.size}")
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

/** One of the mode pills above the shutter. */
@Composable
private fun ModeTab(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent,
        contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit, onFallback: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text("Camera permission", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("The Pro scanner needs camera access only while you scan. Your documents stay on this device by default.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRequest) { Text("Allow camera") }
                // After "Don't ask again" the system dialog no longer appears; settings is the only way back.
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text("Open app settings") }
                OutlinedButton(onClick = onFallback, modifier = Modifier.padding(top = 8.dp)) { Text("Use compatibility scanner") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        }
    }
}

/** Credit-card shaped guide with corner brackets, plus a Front/Back label. */
@Composable
private fun IdCardFrame(label: String) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width * 0.88f
            val height = width * 0.63f
            val left = (size.width - width) / 2f
            val top = (size.height - height) / 2f - 40.dp.toPx()
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
        Text("Fit it inside the borders", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp))
        Surface(modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp), shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.45f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
        }
    }
}

/** Faint rule-of-thirds lines that help keep the document square to the camera. */
@Composable
private fun ThirdsGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val line = Color.White.copy(alpha = 0.28f)
        for (i in 1..2) {
            drawLine(line, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), strokeWidth = 1f)
            drawLine(line, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), strokeWidth = 1f)
        }
    }
}

@Composable
private fun DocumentBoundaryOverlay(detection: DocumentDetection?, stable: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val result = detection ?: return@Canvas
        val quad = result.quad ?: return@Canvas
        val frameWidth = result.frameWidth.toFloat().coerceAtLeast(1f)
        val frameHeight = result.frameHeight.toFloat().coerceAtLeast(1f)
        val scale = minOf(size.width / frameWidth, size.height / frameHeight)
        val left = (size.width - frameWidth * scale) / 2f
        val top = (size.height - frameHeight * scale) / 2f

        fun point(x: Float, y: Float): Offset = Offset(left + x * frameWidth * scale, top + y * frameHeight * scale)
        val points = quad.asList().map { point(it.x, it.y) }
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        drawPath(path, color = if (stable) primary else Color.White, style = Stroke(width = if (stable) 5f else 3f))
        points.forEach { drawCircle(color = if (stable) primary else Color.White, radius = 8f, center = it) }

        if (stable) {
            drawLine(primary, points[0], points[1], strokeWidth = 5f, cap = StrokeCap.Round)
        }
    }
}

private fun CropQuad.asList() = listOf(topLeft, topRight, bottomRight, bottomLeft)

/** Cache sub-folder holding one folder per scanner session; cleaned after import or when abandoned. */
internal const val SESSION_ROOT = "pro_scanner_session"
