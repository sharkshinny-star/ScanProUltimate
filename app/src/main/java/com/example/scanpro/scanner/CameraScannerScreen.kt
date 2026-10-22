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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    onClose: () -> Unit,
    onComplete: (List<Uri>, ScanMode) -> Unit,
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
    var scanMode by rememberSaveable { mutableStateOf(ScanMode.DOCUMENT) }
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
    var processing by remember { mutableStateOf(false) }
    val quads = rememberSaveable(
        saver = listSaver(
            save = { map -> encodeQuads(map) },
            restore = { saved -> mutableStateMapOf<String, CropQuad>().apply { saved.mapNotNull(::decodeQuad).forEach { (k, v) -> put(k, v) } } }
        )
    ) { mutableStateMapOf<String, CropQuad>() }
    // Bumped on every resume: CameraX resets torch, zoom and exposure when the camera restarts.
    var resumeCount by remember { mutableIntStateOf(0) }
    val pageLimit = if (scanMode == ScanMode.ID_CARD) (if (scanBothSides) 2 else 1) else 100

    var confirmQuit by remember { mutableStateOf(false) }
    // Closing with captured pages would discard them, so ask first.
    val requestClose = { if (pages.isNotEmpty() && !completed) confirmQuit = true else onClose() }
    BackHandler(enabled = pages.isNotEmpty() && !completed) { confirmQuit = true }

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
    SideEffect { analyzer.autoCaptureEnabled = autoCapture && !capturing && !reviewing && pages.size < pageLimit }

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
        scope.launch {
            val outputs = withContext(Dispatchers.Default) {
                inputs.map { (uri, quad) ->
                    val source = File(uri.path.orEmpty())
                    val target = File(sessionDir, "scan_${UUID.randomUUID()}.jpg")
                    runCatching { OpenCvScanProcessor.finalizePage(source, target, quad) }
                        .onFailure { runCatching { source.copyTo(target, overwrite = true) } }
                    Uri.fromFile(target)
                }
            }
            completed = true
            processing = false
            onComplete(outputs, scanMode)
        }
    }

    fun capturePage() {
        val capture = imageCapture ?: return
        if (capturing || pages.size >= pageLimit) return
        capturing = true
        status = "Capturing…"

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
                    pages.add(captured)
                    capturing = false
                    // Outline the page in the background so the review screen opens ready.
                    scope.launch {
                        val quad = withContext(Dispatchers.Default) { runCatching { OpenCvScanProcessor.detectCrop(raw) }.getOrNull() }
                        if (quad != null && quads[captured.toString()] == null && pages.contains(captured)) quads[captured.toString()] = quad
                    }
                    status = when {
                        scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> "Front saved · now capture the back"
                        scanMode == ScanMode.ID_CARD && pages.size == pageLimit -> "ID capture ready · tap Done"
                        else -> "Page ${pages.size} captured"
                    }
                    if (!batch && scanMode != ScanMode.ID_CARD) finishScan()
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
        if (autoCaptureSignal > 0 && autoCapture && !capturing && !reviewing && pages.size < pageLimit) {
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
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(98)
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer) }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )
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
            index = reviewIndex,
            sessionDir = sessionDir,
            processing = processing,
            onIndexChange = { reviewIndex = it },
            onAdd = { reviewing = false },
            onBack = { reviewing = false },
            onDone = ::saveReviewed
        )
        return
    }

    // Dark blue-grey scanner backdrop from the approved design (not pure black).
    Box(Modifier.fillMaxSize().background(Color(0xFF0E141D))) {
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
        if (detectMode != DetectMode.NO_DETECT && scanMode != ScanMode.ID_CARD) {
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
        if (status == null && detectMode != DetectMode.NO_DETECT && scanMode != ScanMode.ID_CARD) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Text(liveGuidance, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }

        if (confirmQuit) {
            AlertDialog(
                onDismissRequest = { confirmQuit = false },
                title = { Text("Quit scanning?") },
                text = { Text("If you quit now, the photos you took will be discarded.") },
                confirmButton = { TextButton(onClick = { confirmQuit = false; onClose() }) { Text("Discard", color = Color(0xFFEF4444)) } },
                dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Cancel") } }
            )
        }

            if (scanMode != ScanMode.ID_CARD) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 18.dp)) {
                    Row(
                        Modifier.clickable { detectMenuOpen = true }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(detectMode.label, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Detection mode", tint = MaterialTheme.colorScheme.primary)
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
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = requestClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(if (torchEnabled) Icons.Default.Bolt else Icons.Default.FlashOff, contentDescription = "Flash", tint = Color.White)
                    }
                }
                Box {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
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
                .background(Color(0xFF0A0F18))
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
            val blue = MaterialTheme.colorScheme.primary
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (scanMode == ScanMode.ID_CARD) {
                    listOf(false to "Front only", true to "Both sides").forEach { (both, text) ->
                        Text(
                            text,
                            color = if (scanBothSides == both) blue else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = pages.isEmpty()) { scanBothSides = both }.padding(8.dp)
                        )
                    }
                } else {
                    listOf(ScanMode.TEXT to "Extract text", null to "Docs", ScanMode.ID_CARD to "ID cards").forEach { (target, text) ->
                        val selected = if (target == null) scanMode != ScanMode.TEXT && scanMode != ScanMode.ID_CARD else scanMode == target
                        Text(
                            text,
                            color = if (selected) blue else Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = pages.isEmpty()) {
                                scanMode = target ?: ScanMode.DOCUMENT
                                scanBothSides = false
                            }.padding(8.dp)
                        )
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

                Surface(
                    onClick = { capturePage() },
                    enabled = !capturing && pages.size < pageLimit,
                    shape = CircleShape,
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(5.dp, if (stable) blue else Color.White.copy(alpha = 0.5f)),
                    modifier = Modifier.size(78.dp)
                ) {}

                Box(Modifier.width(96.dp), contentAlignment = Alignment.CenterEnd) {
                    if (pages.isEmpty()) {
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
                            onClick = { finishScan() }
                        ) {
                            Icon(Icons.Default.TaskAlt, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("${pages.size}")
                        }
                    }
                }
            }
        }
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
