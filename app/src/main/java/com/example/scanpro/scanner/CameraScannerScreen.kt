package com.example.scanpro.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun CameraScannerScreen(
    onClose: () -> Unit,
    onComplete: (List<Uri>, ScanMode) -> Unit,
    onFallbackScanner: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
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
    var autoCapture by remember { mutableStateOf(true) }
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
    // Bumped on every resume: CameraX resets torch, zoom and exposure when the camera restarts.
    var resumeCount by remember { mutableIntStateOf(0) }
    val pageLimit = if (scanMode == ScanMode.ID_CARD) (if (scanBothSides) 2 else 1) else 100

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
    SideEffect { analyzer.autoCaptureEnabled = autoCapture && !capturing && pages.size < pageLimit }

    fun capturePage() {
        val capture = imageCapture ?: return
        if (capturing || pages.size >= pageLimit) return
        capturing = true
        status = "Capturing…"

        sessionDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val raw = File(sessionDir, "raw_$id.jpg")
        val corrected = File(sessionDir, "scan_$id.jpg")
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
                    scope.launch {
                        // Always release the shutter and clean up, even if processing fails.
                        try {
                            val result = withContext(Dispatchers.Default) {
                                OpenCvScanProcessor.correctCapturedPage(raw, corrected)
                            }
                            pages.add(Uri.fromFile(result.output))
                            status = when {
                                scanMode == ScanMode.ID_CARD && scanBothSides && pages.size == 1 -> "Front saved · now capture the back"
                                scanMode == ScanMode.ID_CARD && pages.size == pageLimit -> "ID capture ready · tap Done"
                                result.perspectiveCorrected -> "Page ${pages.size} captured"
                                else -> "Page ${pages.size} captured · adjust crop if needed"
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            // Throwable: a failed OpenCV native load throws UnsatisfiedLinkError, not an Exception.
                            status = "Could not process page: ${error.message ?: "try again"}"
                        } finally {
                            raw.delete()
                            capturing = false
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    status = exception.message ?: "Could not capture page"
                }
            }
        )
    }

    LaunchedEffect(status) {
        if (status != null) {
            delay(3.seconds)
            status = null
        }
    }

    LaunchedEffect(autoCaptureSignal) {
        if (autoCaptureSignal > 0 && autoCapture && !capturing && pages.size < pageLimit) capturePage()
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

        DocumentBoundaryOverlay(detection = detection, stable = stable)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                    IconButton(onClick = { autoCapture = !autoCapture }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Auto capture", tint = if (autoCapture) MaterialTheme.colorScheme.primary else Color.White)
                    }
                }
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
                    IconButton(onClick = { torchEnabled = !torchEnabled }) {
                        Icon(if (torchEnabled) Icons.Default.Bolt else Icons.Default.FlashOff, contentDescription = "Flash", tint = Color.White)
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.14f)) {
                Text(status ?: liveGuidance, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }

            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanMode.entries, key = { it.name }) { mode ->
                    FilterChip(
                        selected = scanMode == mode,
                        onClick = { scanMode = mode; scanBothSides = false },
                        enabled = pages.isEmpty(),
                        label = { Text(mode.label) }
                    )
                }
            }

            if (scanMode == ScanMode.ID_CARD) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !scanBothSides,
                        enabled = pages.isEmpty(),
                        onClick = { scanBothSides = false },
                        label = { Text("Front only") }
                    )
                    FilterChip(
                        selected = scanBothSides,
                        enabled = pages.isEmpty(),
                        onClick = { scanBothSides = true },
                        label = { Text("Front + back") }
                    )
                }
                Text(
                    when {
                        scanBothSides && pages.isEmpty() -> "Capture the front of your ID first"
                        scanBothSides && pages.size == 1 -> "Front captured. Capture the back or undo to retake."
                        scanBothSides -> "Both sides captured. Ready to save and share."
                        pages.isEmpty() -> "Capture the front of your ID"
                        else -> "Front captured. Ready to save and share."
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("1×", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Import photos", tint = Color.White)
                }

                Surface(
                    onClick = { capturePage() },
                    enabled = !capturing && pages.size < pageLimit,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(78.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Surface(shape = CircleShape, color = if (stable) MaterialTheme.colorScheme.primary else Color.Black, modifier = Modifier.size(58.dp)) {}
                    }
                }

                if (pages.isEmpty()) {
                    IconButton(onClick = onFallbackScanner) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = "Google Scanner", tint = Color.White)
                    }
                } else {
                    Button(
                        enabled = !capturing && (scanMode != ScanMode.ID_CARD || pages.size == pageLimit),
                        onClick = {
                            // Ignore a second tap during the exit animation; it would import twice.
                            if (!completed) {
                                completed = true
                                onComplete(pages.toList(), scanMode)
                            }
                        }
                    ) {
                        Icon(Icons.Default.TaskAlt, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Done ${pages.size}")
                    }
                }
            }

            if (pages.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Collections, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${pages.size} page${if (pages.size == 1) "" else "s"} in this scan", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { pages.removeLastOrNull()?.let { uri -> if (uri.scheme == "file") File(uri.path.orEmpty()).delete() } }) {
                        Text("Undo last")
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
