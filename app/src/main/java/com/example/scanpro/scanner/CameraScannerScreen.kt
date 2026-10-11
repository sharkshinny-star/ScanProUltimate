package com.example.scanpro.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.ScanMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

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
    var status by remember { mutableStateOf("Point the camera at a document") }
    var autoCaptureSignal by remember { mutableIntStateOf(0) }
    var scanMode by remember { mutableStateOf(ScanMode.DOCUMENT) }
    val pages = remember { mutableStateListOf<Uri>() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) pages.addAll(uris)
    }

    val analyzer = remember {
        OpenCvDocumentAnalyzer(
            onDetection = { result, isStable ->
                detection = result
                stable = isStable
                status = if (isStable && result.isReady) "Hold… capturing" else result.guidance
            },
            onAutoCapture = { autoCaptureSignal++ }
        )
    }
    SideEffect { analyzer.autoCaptureEnabled = autoCapture && !capturing }

    fun capturePage() {
        val capture = imageCapture ?: return
        if (capturing) return
        capturing = true
        status = "Capturing…"

        val sessionDir = File(context.cacheDir, "pro_scanner_session").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val raw = File(sessionDir, "raw_$id.jpg")
        val corrected = File(sessionDir, "scan_$id.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(raw).build()

        capture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            OpenCvScanProcessor.correctCapturedPage(raw, corrected)
                        }
                        raw.delete()
                        pages.add(Uri.fromFile(result.output))
                        capturing = false
                        status = if (result.perspectiveCorrected) "Page ${pages.size} captured" else "Page ${pages.size} captured · adjust crop if needed"
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    status = exception.message ?: "Could not capture page"
                }
            }
        )
    }

    LaunchedEffect(autoCaptureSignal) {
        if (autoCaptureSignal > 0 && autoCapture && !capturing) capturePage()
    }

    LaunchedEffect(permissionGranted, lifecycleOwner) {
        if (!permissionGranted) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
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
                status = "Camera unavailable. Use compatibility scanner."
            }
        }, mainExecutor)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(torchEnabled, boundCamera) {
        boundCamera?.cameraControl?.enableTorch(torchEnabled)
    }

    LaunchedEffect(zoom, boundCamera) {
        boundCamera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    LaunchedEffect(exposureIndex, exposureMin, exposureMax, boundCamera) {
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
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
                Text(status, color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
            }

            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ScanMode.entries, key = { it.name }) { mode ->
                    FilterChip(
                        selected = scanMode == mode,
                        onClick = { scanMode = mode },
                        label = { Text(mode.label) }
                    )
                }
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
                    enabled = !capturing,
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
                        Icon(Icons.Default.DocumentScanner, contentDescription = "Compatibility scanner", tint = Color.White)
                    }
                } else {
                    Button(onClick = { onComplete(pages.toList(), scanMode) }) {
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
