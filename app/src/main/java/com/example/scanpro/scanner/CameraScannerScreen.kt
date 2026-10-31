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
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.PanTool
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import com.example.scanpro.data.NormalizedPoint
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import com.example.scanpro.core.ui.findActivity
import android.graphics.Bitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.example.scanpro.R
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
import kotlin.math.roundToInt
import androidx.core.content.edit

/** How the live preview helps: Auto captures by itself, Manual only outlines the page, No detect does neither. */
private enum class DetectMode(@StringRes val label: Int) { AUTO(R.string.scan_detect_auto), MANUAL(R.string.scan_detect_manual), NO_DETECT(R.string.scan_detect_no_detect) }

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
    val resources = LocalResources.current
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
    // After a refusal with "don't ask again", the system dialog no longer appears; the panel then leads to Settings.
    var permissionRefused by remember { mutableStateOf(false) }
    var detection by remember { mutableStateOf<DocumentDetection?>(null) }
    var stable by remember { mutableStateOf(false) }
    // How close the page is to being steady enough to capture; fills the ring round the shutter.
    var steadiness by remember { mutableFloatStateOf(0f) }
    // The outline as drawn: it follows the detector with a little lag so it glides instead of jittering.
    var shownQuad by remember { mutableStateOf<CropQuad?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // Off, auto, on (fires with the shutter) or torch (steady light for dark rooms).
    var flashMode by rememberSaveable { mutableStateOf(FlashMode.OFF) }
    val torchEnabled = flashMode == FlashMode.TORCH
    // A camera problem the user has to act on (camera busy, disabled, failed to open); shown in every mode.
    var cameraError by remember { mutableStateOf<String?>(null) }
    val prefs = remember { context.getSharedPreferences("scanner_prefs", android.content.Context.MODE_PRIVATE) }
    var detectMode by rememberSaveable {
        mutableStateOf(runCatching { DetectMode.valueOf(prefs.getString("detect", null).orEmpty()) }.getOrDefault(DetectMode.AUTO))
    }
    var detectMenuOpen by remember { mutableStateOf(false) }
    // A few tips the first time the camera opens; "Got it" hides them for good.
    var showTips by remember { mutableStateOf(!prefs.getBoolean("tips_seen", false)) }
    fun dismissTips() {
        showTips = false
        prefs.edit { putBoolean("tips_seen", true) }
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
    var liveGuidance by remember { mutableStateOf(resources.getString(R.string.scan_guidance_point_at_document)) }
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
    val sessionDir = remember(sessionId) { File(scannerSessionRoot(context), sessionId) }
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
        if (!it) permissionRefused = true
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
                            // Android 8 and older cannot decode HEIC; a page that can't be read is left out with a reason.
                            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            android.graphics.BitmapFactory.decodeFile(target.absolutePath, bounds)
                            if (bounds.outWidth <= 0) {
                                target.delete()
                                val heic = bounds.outMimeType?.contains("hei", ignoreCase = true) == true || uri.toString().contains(".hei", ignoreCase = true)
                                error(if (heic) "HEIC photos need Android 9 or newer; choose a JPG or PNG" else "This image could not be read")
                            }
                            Uri.fromFile(target)
                        }.getOrNull()
                    }
                }
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
    val autoCaptureActive = autoCapture && !showTips && !barcodeMode && !capturing && !reviewing &&
        (retaking || pages.size < pageLimit) && scanMode != ScanMode.ID_CARD
    val analyzer = remember {
        OpenCvDocumentAnalyzer(
            onDetection = { result, isStable, progress ->
                // The screen recomposes on this object; it is replaced only when something it shows changed, not every frame.
                val previousResult = detection
                if (previousResult == null || (previousResult.quad == null) != (result.quad == null) || previousResult.isDark != result.isDark ||
                    previousResult.isBlurry != result.isBlurry || previousResult.isReady != result.isReady ||
                    previousResult.frameWidth != result.frameWidth || previousResult.frameHeight != result.frameHeight
                ) detection = result
                // A soft tick the moment the page locks on, so the eye can stay on the paper.
                if (isStable && !stable) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                stable = isStable
                steadiness = if (result.quad == null) 0f else progress
                val target = result.quad
                val previous = shownQuad
                shownQuad = when {
                    target == null -> null
                    previous == null -> target
                    else -> previous.towards(target, 0.45f)
                }
                // "Hold… capturing" is chosen where it is shown, which knows whether auto-capture is on.
                liveGuidance = resources.getString(result.guidance.labelRes)
            },
            onAutoCapture = { autoCaptureSignal++ },
            onUnavailable = { cameraError = resources.getString(R.string.scan_error_detector_unavailable) }
        )
    }
    // Taking a page by hand while the tips show means they have done their job.
    LaunchedEffect(pages.size) { if (showTips && pages.isNotEmpty()) dismissTips() }

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
        // Nothing reads the result in No-detect mode or during review, so the frames are not analysed at all.
        analyzer.enabled = detectMode != DetectMode.NO_DETECT && !reviewing && !barcodeMode
        analyzer.autoCaptureEnabled = autoCaptureActive
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
        // The folder is made on the camera's own thread rather than here on main.
        analysisExecutor.execute { sessionDir.mkdirs() }
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
                        if (old.scheme == "file") deleteInBackground(File(old.path.orEmpty()))
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
                        replacing -> resources.getString(R.string.scan_status_page_retaken, retakeIndex + 1)
                        scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> resources.getString(R.string.scan_status_front_saved)
                        scanMode == ScanMode.ID_CARD && pages.size == pageLimit -> resources.getString(R.string.scan_status_id_ready)
                        else -> resources.getString(R.string.scan_status_page_captured, pages.size)
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

    // Re-keyed on `reviewing`: the camera is released while the review step is up, so the preview and
    // OpenCV analysis stop costing battery behind a screen that doesn't show them, and bound again after.
    LaunchedEffect(permissionGranted, lifecycleOwner, reviewing, resumeCount) {
        if (!permissionGranted) return@LaunchedEffect
        if (reviewing) {
            cameraProvider?.unbindAll()
            imageCapture = null
            boundCamera = null
            return@LaunchedEffect
        }
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
                // About 12 MP is more than the pipeline keeps (it works at 3400 px); a 50 MP sensor would only
                // add shutter lag, disk and decode time. Quality 92 is visually lossless for paper.
                val captureSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
                val capture = ImageCapture.Builder()
                    .setResolutionSelector(captureSelector)
                    // Low latency keeps the shutter responsive (the page is captured the moment it is steady);
                    // quality 95 keeps paper text crisp after the filters.
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(95)
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
                if (viewPort == null && !previewView.isLaidOut) {
                    // First bind before layout: bind again once the view has a size, so analysis lines up with the preview.
                    previewView.post { if (!disposed.get() && previewView.viewPort != null) resumeCount++ }
                }
                // Tablets and Chromebooks may only have a front or external camera; any camera beats none.
                val cameraSelector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.Builder().build()
                }
                val camera = if (viewPort != null) {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        UseCaseGroup.Builder().setViewPort(viewPort).addUseCase(preview).addUseCase(capture).addUseCase(analysis).build()
                    )
                } else {
                    provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture, analysis)
                }
                imageCapture = capture
                boundCamera = camera
                cameraError = null
                // Errors after binding (another app took the camera, device policy, a fold closing) arrive here.
                camera.cameraInfo.cameraState.observe(lifecycleOwner) { state ->
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
                cameraError = resources.getString(R.string.scan_error_camera_open)
            }
        }, mainExecutor)
    }

    // Volume keys take a photo while this screen can; presses arrive from the activity.
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
    DisposableEffect(permissionGranted) {
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
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (permissionGranted && accelerometer != null) sensors.registerListener(listener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors?.unregisterListener(listener) }
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
            // Up to a hundred large JPEGs: not on the main thread during the exit animation.
            if (!completed && !changingConfigurations) Thread { sessionDir.deleteRecursively() }.start()
            analyzer.close()
        }
    }

    // Clear out sessions abandoned by a crash or process death more than a day ago.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            scannerSessionRoot(context).listFiles()
                ?.filter { it.name != sessionId && it.lastModified() < cutoff && !File(it, SESSION_PENDING_IMPORT).exists() }
                ?.forEach { it.deleteRecursively() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeCount++
                // Granted in Settings and came back: the panel must go without a tap.
                permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(flashMode, boundCamera, resumeCount) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
        imageCapture?.flashMode = when (flashMode) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
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
                status = resources.getString(R.string.scan_status_retaking_page, retakeIndex + 1)
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
        if (!barcodeMode && !reviewing && scanMode != ScanMode.ID_CARD) {
            LevelBubble(tilt = tilt, accent = scanLine, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 16.dp))
        }
        if (!barcodeMode && detectMode != DetectMode.NO_DETECT && scanMode != ScanMode.ID_CARD) {
            DocumentBoundaryOverlay(detection = detection, quad = shownQuad, stable = stable, accent = scanLine)
        }
        if (scanMode == ScanMode.ID_CARD) {
            IdCardFrame(label = stringResource(if (scanBothSides && pages.size == 1) R.string.scan_id_side_back else R.string.scan_id_side_front))
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
                    Text(stringResource(R.string.scan_tips_title), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        listOf(
                            R.string.scan_tip_hold_steady,
                            R.string.scan_tip_tap_focus,
                            R.string.scan_tip_batch,
                            R.string.scan_tip_cleanup
                        ).forEach { tip ->
                            Text("• ${stringResource(tip)}", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    TextButton(
                        onClick = ::dismissTips,
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(stringResource(R.string.scan_tips_got_it)) }
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
                    if (barcodeUnavailable) stringResource(R.string.scan_qr_downloading)
                    else stringResource(R.string.scan_qr_point),
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
                    val state = detection
                    // "Capturing" only when the shutter really is about to fire on its own; in Manual mode the page is just outlined.
                    val guidance = if (stable && state?.isReady == true && autoCaptureActive) stringResource(R.string.scan_guidance_hold_capturing) else liveGuidance
                    val ready = stable && state?.isReady == true
                    val hintIcon = when {
                        ready -> Icons.Default.TaskAlt
                        state?.isDark == true -> Icons.Default.Bolt
                        state?.quad != null && state.isBlurry -> Icons.Default.PanTool
                        state?.quad != null -> Icons.Default.CenterFocusWeak
                        else -> Icons.Default.DocumentScanner
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(hintIcon, contentDescription = null, tint = if (ready) scanLine else Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        androidx.compose.animation.AnimatedContent(
                            targetState = guidance,
                            transitionSpec = {
                                (androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.slideInVertically(tween(160)) { it / 3 })
                                    .togetherWith(androidx.compose.animation.fadeOut(tween(120)))
                            },
                            label = "guidance"
                        ) { text ->
                            Text(text, color = if (ready) scanLine else Color.White, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    // Low light: one tap turns the torch on from the hint itself.
                    if (detection?.isDark == true && !torchEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            onClick = { flashMode = FlashMode.TORCH },
                            shape = RoundedCornerShape(50),
                            color = scanLine,
                            contentColor = Color(0xFF04281E)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.scan_turn_on_flash), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
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
                        TextButton(onClick = { cameraError = null; resumeCount++ }) { Text(stringResource(R.string.common_retry), color = Color.White) }
                        TextButton(onClick = onFallbackScanner) { Text(stringResource(R.string.scan_compat_scanner), color = scanLine) }
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
                Text(stringResource(R.string.scan_retaking_page, retakeOfPage + 1), color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                title = { Text(stringResource(R.string.scan_quit_title)) },
                text = { Text(stringResource(R.string.scan_quit_body)) },
                confirmButton = { TextButton(onClick = { confirmQuit = false; onClose() }) { Text(stringResource(R.string.scan_quit_discard), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text(stringResource(R.string.common_cancel)) } }
            )
        }

            if (scanMode != ScanMode.ID_CARD && !barcodeMode) {
                Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) {
                    Row(
                        Modifier.clickable { detectMenuOpen = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(detectMode.label), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.scan_detection_mode), tint = scanLine)
                    }
                    DropdownMenu(expanded = detectMenuOpen, onDismissRequest = { detectMenuOpen = false }) {
                        DetectMode.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(option.label), color = if (option == detectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                },
                                onClick = { detectMode = option; detectMenuOpen = false; prefs.edit { putString("detect", option.name) } }
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
                IconButton(onClick = requestClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = CameraGlass) {
                    // Each tap moves to the next flash mode; a short label under the icon says which.
                    IconButton(onClick = {
                        flashMode = FlashMode.entries[(flashMode.ordinal + 1) % FlashMode.entries.size]
                        status = resources.getString(R.string.scan_flash_state, resources.getString(flashMode.labelRes))
                    }) {
                        Icon(
                            when (flashMode) {
                                FlashMode.OFF -> Icons.Default.FlashOff
                                FlashMode.AUTO -> Icons.Default.FlashAuto
                                FlashMode.ON -> Icons.Default.FlashOn
                                FlashMode.TORCH -> Icons.Default.Bolt
                            },
                            contentDescription = stringResource(R.string.scan_flash_state, stringResource(flashMode.labelRes)),
                            tint = if (flashMode == FlashMode.OFF) Color.White else scanLine
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
                        if (pages.isEmpty() && scanMode != ScanMode.ID_CARD && scanMode != ScanMode.TEXT) {
                            listOf(ScanMode.DOCUMENT, ScanMode.RECEIPT, ScanMode.WHITEBOARD, ScanMode.BOOK).forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(type.labelRes), color = if (scanMode == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                    onClick = { scanMode = type; overflowOpen = false }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_compat_scanner)) },
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
            // Batch: the pages so far, newest last; tap one to check it in the review step.
            if (pages.size > 1 || (batch && pages.isNotEmpty())) {
                CapturedStrip(
                    pages = pages,
                    onOpen = { index ->
                        reviewIndex = index
                        reviewing = true
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
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
                    listOf(false to R.string.scan_mode_single, true to R.string.scan_mode_batch).forEach { (value, text) ->
                        Surface(
                            onClick = { batch = value; prefs.edit { putBoolean("batch", value) } },
                            shape = RoundedCornerShape(50),
                            color = if (batch == value) Color.White.copy(alpha = 0.28f) else Color.Transparent
                        ) {
                            Text(stringResource(text), color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (showAdjustments) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.scan_zoom_min), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Slider(value = zoom, onValueChange = { zoom = it }, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Text(stringResource(R.string.scan_zoom), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                if (exposureMin < exposureMax) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.scan_ev), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = exposureIndex.toFloat(),
                            // Slider ticks are not exact in float (2.9999998), so round rather than truncate.
                            onValueChange = { exposureIndex = it.roundToInt().coerceIn(exposureMin, exposureMax) },
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
            // ID cards add a Front only / Both sides row; the mode tabs stay, so a wrong tap is undone with another tap.
            if (scanMode == ScanMode.ID_CARD) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(false to R.string.scan_id_front_only, true to R.string.scan_id_both_sides).forEach { (both, text) ->
                        ModeTab(stringResource(text), selected = scanBothSides == both, enabled = pages.isEmpty() && retakeOfPage == null) { scanBothSides = both }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(ScanMode.TEXT to R.string.scan_tab_text, null to R.string.scan_tab_docs, ScanMode.ID_CARD to R.string.scan_tab_id_cards).forEach { (target, text) ->
                    val selected = !barcodeMode &&
                        if (target == null) scanMode != ScanMode.TEXT && scanMode != ScanMode.ID_CARD else scanMode == target
                    ModeTab(stringResource(text), selected = selected, enabled = pages.isEmpty() && retakeOfPage == null) {
                        barcodeMode = false
                        scanMode = target ?: ScanMode.DOCUMENT
                        scanBothSides = false
                    }
                }
                ModeTab(stringResource(R.string.scan_qr_code), selected = barcodeMode, enabled = pages.isEmpty() && retakeOfPage == null) {
                    barcodeMode = true
                    scanMode = ScanMode.DOCUMENT
                    scanBothSides = false
                }
            }

            val takePhotoDescription = stringResource(R.string.scan_take_photo)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterStart) {
                    if (pages.isNotEmpty()) {
                        Column(
                            Modifier.clickable { pages.removeLastOrNull()?.let { uri -> if (uri.scheme == "file") deleteInBackground(File(uri.path.orEmpty())) } }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.scan_remove_last_page), tint = Color.White)
                            Text(stringResource(R.string.common_back), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Outer ring fills clockwise as the page settles and turns solid mint once it is steady; the
                // inner disc is the button.
                val ringFill by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (stable) 1f else steadiness,
                    animationSpec = if (steadiness == 0f) tween(200) else spring(stiffness = 700f),
                    label = "ring"
                )
                val shutterScale by androidx.compose.animation.core.animateFloatAsState(if (capturing) 0.86f else 1f, spring(stiffness = 900f), label = "shutter")
                Box(
                    Modifier
                        .size(84.dp)
                        .graphicsLayer { scaleX = shutterScale; scaleY = shutterScale }
                        .drawBehind {
                            val strokeWidth = 4.dp.toPx()
                            val inset = strokeWidth / 2
                            drawCircle(Color.White.copy(alpha = 0.35f), radius = size.minDimension / 2 - inset, style = Stroke(strokeWidth))
                            if (ringFill > 0f) {
                                drawArc(
                                    color = scanLine,
                                    startAngle = -90f,
                                    sweepAngle = 360f * ringFill,
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
                        color = if (barcodeMode) Color.White.copy(alpha = 0.25f) else Color.White,
                        // The shutter has no visible label, so screen readers need one.
                        modifier = Modifier.fillMaxSize().semantics { contentDescription = takePhotoDescription }
                    ) {}
                }

                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                    if (pages.isEmpty() && !barcodeMode) {
                        Column(
                            Modifier.clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.scan_import_photos), tint = Color.White)
                            Text(stringResource(R.string.scan_import_images), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (pages.isNotEmpty()) {
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
private fun PermissionPanel(askable: Boolean, onRequest: () -> Unit, onFallback: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp)) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.scan_permission_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (askable) R.string.scan_permission_body else R.string.scan_permission_denied_body))
                Spacer(Modifier.height(16.dp))
                // After "Don't ask again" the system dialog no longer appears; settings is the only way back.
                if (askable) Button(onClick = onRequest) { Text(stringResource(R.string.scan_permission_allow)) }
                else Button(onClick = openSettings) { Text(stringResource(R.string.scan_permission_open_settings)) }
                if (askable) TextButton(onClick = openSettings) { Text(stringResource(R.string.scan_permission_open_settings)) }
                OutlinedButton(onClick = onFallback, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.scan_permission_use_compat)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.common_cancel)) }
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
        Text(stringResource(R.string.scan_id_fit_inside), color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp))
        Surface(modifier = Modifier.align(Alignment.Center).offset(y = (-40).dp), shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.45f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
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
private fun LevelBubble(tilt: Offset, accent: Color, modifier: Modifier = Modifier) {
    val level = kotlin.math.abs(tilt.x) < 6f && kotlin.math.abs(tilt.y) < 6f
    val ring by androidx.compose.animation.animateColorAsState(if (level) accent else Color.White.copy(alpha = 0.7f), tween(180), label = "level")
    Canvas(modifier.size(44.dp)) {
        val radius = size.minDimension / 2f
        drawCircle(Color.Black.copy(alpha = 0.35f), radius)
        drawCircle(ring, radius - 1.5.dp.toPx(), style = Stroke(2.dp.toPx()))
        drawCircle(ring.copy(alpha = 0.5f), 5.dp.toPx(), style = Stroke(1.dp.toPx()))
        // Up to 25 degrees of lean moves the bubble to the rim.
        val reach = radius - 7.dp.toPx()
        val dx = (tilt.x / 25f).coerceIn(-1f, 1f) * reach
        val dy = (-tilt.y / 25f).coerceIn(-1f, 1f) * reach
        drawCircle(ring, 5.dp.toPx(), center = center + Offset(dx, dy))
    }
}

/** Thumbnails of the pages captured so far, decoded small and kept while the screen lives. */
@Composable
private fun CapturedStrip(pages: List<Uri>, onOpen: (Int) -> Unit) {
    val cache = remember { HashMap<String, Bitmap>() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(pages.size) { if (pages.isNotEmpty()) listState.animateScrollToItem(pages.lastIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(pages, key = { _, uri -> uri.toString() }) { index, uri ->
            val key = uri.toString()
            val pageLabel = stringResource(R.string.scan_review_page_n, index + 1)
            val bitmap by produceState<Bitmap?>(initialValue = cache[key], key) {
                if (value == null) {
                    value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 160) }
                    value?.let { cache[key] = it }
                }
            }
            Box(
                Modifier
                    .size(44.dp, 58.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .clickable { onOpen(index) }
                    .semantics { contentDescription = pageLabel },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
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

/**
 * The page outline over the preview: a faint fill, an edge line and bracket corners. White while the page
 * is being tracked; mint, thicker and filled a touch more once it is steady. [quad] is the smoothed outline;
 * [detection] supplies the frame size the outline is expressed in.
 */
@Composable
private fun DocumentBoundaryOverlay(detection: DocumentDetection?, quad: CropQuad?, stable: Boolean, accent: Color) {
    val lineColor by androidx.compose.animation.animateColorAsState(if (stable) accent else Color.White, tween(220), label = "outline")
    val fillAlpha by androidx.compose.animation.core.animateFloatAsState(if (stable) 0.16f else 0.07f, tween(220), label = "fill")
    val weight by androidx.compose.animation.core.animateFloatAsState(if (stable) 5f else 3f, tween(220), label = "weight")
    Canvas(Modifier.fillMaxSize()) {
        val result = detection ?: return@Canvas
        val outline = quad ?: return@Canvas
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
        drawPath(path, color = lineColor.copy(alpha = 0.9f), style = Stroke(width = weight, cap = StrokeCap.Round))

        // Bracket corners: a short run along each edge away from the corner, like a viewfinder.
        val armLength = (size.minDimension * 0.045f).coerceIn(18f, 44f)
        for (i in points.indices) {
            val corner = points[i]
            val next = points[(i + 1) % 4]
            val previous = points[(i + 3) % 4]
            fun arm(toward: Offset) {
                val dx = toward.x - corner.x
                val dy = toward.y - corner.y
                val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                val end = Offset(corner.x + dx / length * armLength, corner.y + dy / length * armLength)
                drawLine(lineColor, corner, end, strokeWidth = weight + 3f, cap = StrokeCap.Round)
            }
            arm(next)
            arm(previous)
        }
    }
}

/** This outline moved [fraction] of the way towards [target]; the bends are taken from the target. */
private fun CropQuad.towards(target: CropQuad, fraction: Float): CropQuad {
    fun step(a: NormalizedPoint, b: NormalizedPoint) = NormalizedPoint(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction)
    return target.copy(
        topLeft = step(topLeft, target.topLeft),
        topRight = step(topRight, target.topRight),
        bottomRight = step(bottomRight, target.bottomRight),
        bottomLeft = step(bottomLeft, target.bottomLeft)
    )
}

private fun CropQuad.asList() = listOf(topLeft, topRight, bottomRight, bottomLeft)

/** Sub-folder holding one folder per scanner session; cleaned after import or when abandoned. */
internal const val SESSION_ROOT = "pro_scanner_session"

/** Marker in a session folder whose photos are queued for import: cleanup must leave the folder alone. */
internal const val SESSION_PENDING_IMPORT = ".pending_import"

/**
 * Where scanner sessions live: in the app's files, not its cache. Android may clear the cache whenever storage
 * runs low, and a finished scan waiting for its import exists nowhere else.
 */
internal fun scannerSessionRoot(context: android.content.Context) = File(context.filesDir, SESSION_ROOT)
