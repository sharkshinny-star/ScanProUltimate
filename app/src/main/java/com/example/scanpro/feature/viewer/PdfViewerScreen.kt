package com.example.scanpro.feature.viewer

import androidx.compose.ui.layout.onSizeChanged
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.printPdfFile
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    title: String,
    pdfUri: Uri,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onProtect: (() -> Unit)? = null,
    onCompress: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isImageMode by remember { mutableStateOf(false) }

    var showJumpDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Search state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchingPageIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentMatchIdx by remember { mutableIntStateOf(0) }
    var isSearchingText by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // The zoom once the fingers have stopped: pages are redrawn sharp at this level (not on every frame of a pinch).
    var settledScale by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(scale) {
        kotlinx.coroutines.delay(250.milliseconds)
        settledScale = scale
    }

    val currentPage by remember {
        derivedStateOf {
            if (pageCount == 0) 1
            else (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount)
        }
    }

    // PdfRenderer can have only one page open at a time, so every page render and the final close share this lock.
    val renderLock = remember { Mutex() }
    val closeScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    // Rendered pages, about 24 MB of them, kept so scrolling back doesn't render again. Evicted pages are not
    // recycled: a page on screen can be evicted while it is still drawn, and the garbage collector frees it once
    // nothing shows it.
    val pageCache = remember {
        object : android.util.LruCache<Int, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
        }
    }
    DisposableEffect(Unit) { onDispose { pageCache.evictAll() } }
    // The text of every page, read once per file; each search then scans these strings.
    var pageTexts by remember { mutableStateOf<List<String>?>(null) }
    // Copies made for viewing (including decrypted ones) are removed when the viewer closes.
    val tempFiles = remember { mutableListOf<File>() }
    var needsPassword by remember { mutableStateOf(false) }
    // The file actually on screen: an unlocked or unrestricted copy when the original is protected.
    var openedFile by remember { mutableStateOf<File?>(null) }
    LaunchedEffect(openedFile) { pageTexts = null }
    var lockedFile by remember { mutableStateOf<File?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Set when the viewer closes; a renderer that finishes opening after that is closed at once instead of leaking.
    val viewerClosed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var unlocking by remember { mutableStateOf(false) }

    suspend fun openRenderer(file: File) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            val stale = renderLock.withLock {
                if (viewerClosed.get()) true else {
                    // Replace (and close) any renderer already open, e.g. after a second password attempt.
                    runCatching { pdfRenderer?.close() }
                    runCatching { pfd?.close() }
                    // Pages rendered from the previous file must never show for this one.
                    pageCache.evictAll()
                    false
                }
            }
            if (stale) {
                renderer.close()
                descriptor.close()
                return
            }
            pfd = descriptor
            pdfRenderer = renderer
            pageCount = renderer.pageCount
            openedFile = file
        } catch (error: Throwable) {
            runCatching { descriptor.close() }
            throw error
        }
    }

    DisposableEffect(pdfUri) {
        val job = scope.launch(Dispatchers.IO) {
            try {
                isLoading = true
                errorMessage = null
                val path = pdfUri.path?.lowercase() ?: ""
                val uriStr = pdfUri.toString().lowercase()
                val isImage = path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
                        uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".png")

                if (isImage) {
                    isImageMode = true
                    // Decoded at screen size and turned by its EXIF tag: a full 50 MP photo would need
                    // hundreds of megabytes and show sideways.
                    val bmp = if (pdfUri.scheme == "file" && pdfUri.path != null) {
                        ImageProcessor.decodeOrientedScaled(pdfUri.path!!, 2400)
                    } else {
                        val copy = File(context.cacheDir, "view_${java.util.UUID.randomUUID()}.img")
                        try {
                            context.contentResolver.openInputStream(pdfUri)?.use { input -> copy.outputStream().use { input.copyTo(it) } }
                            ImageProcessor.decodeOrientedScaled(copy.absolutePath, 2400)
                        } finally {
                            copy.delete()
                        }
                    }
                    if (bmp != null) {
                        imageBitmap = bmp
                        pageCount = 1
                        return@launch
                    }
                }

                isImageMode = false
                val local = pdfUri.path?.let { File(it) }?.takeIf { pdfUri.scheme == "file" && it.exists() }
                val file = local ?: File(context.cacheDir, "view_temp_${System.currentTimeMillis()}.pdf").also { temp ->
                    tempFiles += temp
                    context.contentResolver.openInputStream(pdfUri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException(resources.getString(R.string.viewer_could_not_open_file))
                }
                try {
                    openRenderer(file)
                } catch (first: Exception) {
                    // The renderer's error for a protected file differs between Android versions, so ask
                    // PDFBox what is wrong instead of relying on the exception type.
                    when (runCatching { PdfSecurity.protection(file) }.getOrNull()) {
                        PdfProtection.PASSWORD_REQUIRED -> {
                            lockedFile = file
                            needsPassword = true
                        }
                        PdfProtection.RESTRICTED_ONLY -> {
                            // Opens without a password but with restrictions some renderers refuse.
                            val open = File(context.cacheDir, "view_unrestricted_${System.currentTimeMillis()}.pdf")
                            tempFiles += open
                            PdfSecurity.unlock(file, open, "")
                            openRenderer(open)
                        }
                        else -> throw first
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: resources.getString(R.string.viewer_load_failed)
            } finally {
                isLoading = false
            }
        }

        onDispose {
            viewerClosed.set(true)
            job.cancel()
            // Closing waits for any page render holding the lock, so it runs off the main thread on a scope
            // that outlives this screen; the captured values are the ones to close.
            val renderer = pdfRenderer
            val descriptor = pfd
            pdfRenderer = null
            closeScope.launch {
                renderLock.withLock {
                    runCatching { renderer?.close() }
                    runCatching { descriptor?.close() }
                }
            }
            imageBitmap?.recycle()
            tempFiles.forEach { it.delete() }
        }
    }

    LaunchedEffect(searchQuery, openedFile) {
        if (searchQuery.isBlank() || isImageMode) {
            matchingPageIndices = emptyList()
            currentMatchIdx = 0
            isSearchingText = false
            return@LaunchedEffect
        }

        // Wait until typing pauses; a new keystroke cancels this effect before any work starts.
        kotlinx.coroutines.delay(350.milliseconds)
        isSearchingText = true
        try {
            val query = searchQuery
            val texts = pageTexts ?: withContext(Dispatchers.IO) {
                // Read once per file (and once per protected copy); later queries only scan the strings.
                val tempPdf = File(context.cacheDir, "search_temp_${System.currentTimeMillis()}.pdf")
                try {
                    // Search the file being shown; for a protected PDF that is the unlocked copy, not the original.
                    val stream = openedFile ?: if (pdfUri.scheme == "file" && pdfUri.path != null) {
                        File(pdfUri.path!!)
                    } else {
                        context.contentResolver.openInputStream(pdfUri)?.use { input ->
                            FileOutputStream(tempPdf).use { output -> input.copyTo(output) }
                        }
                        tempPdf
                    }
                    if (!stream.exists()) emptyList()
                    else PDDocument.load(stream, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { pdDoc ->
                        val stripper = PDFTextStripper()
                        (0 until pdDoc.numberOfPages).map { i ->
                            // Stop as soon as a newer query replaces this one.
                            ensureActive()
                            stripper.startPage = i + 1
                            stripper.endPage = i + 1
                            runCatching { stripper.getText(pdDoc) }.getOrDefault("")
                        }
                    }
                } finally {
                    // The copy is read once; never leave it behind.
                    tempPdf.delete()
                }
            }.also { pageTexts = it }
            val matches = texts.withIndex().filter { (_, text) -> text.contains(query, ignoreCase = true) }.map { it.index }
            matchingPageIndices = matches
            currentMatchIdx = 0
            if (matches.isNotEmpty()) listState.scrollToItem(matches.first())
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            matchingPageIndices = emptyList()
        } finally {
            isSearchingText = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.viewer_search_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            trailingIcon = {
                                if (isSearchingText) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.viewer_clear))
                                    }
                                }
                            }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (pageCount > 0) {
                                Text(
                                    text = if (isImageMode) stringResource(R.string.viewer_image_preview) else stringResource(R.string.viewer_page_of, currentPage, pageCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (matchingPageIndices.isNotEmpty()) {
                            Text(
                                stringResource(R.string.viewer_match_position, currentMatchIdx + 1, matchingPageIndices.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (matchingPageIndices.isNotEmpty()) {
                                        currentMatchIdx = (currentMatchIdx - 1 + matchingPageIndices.size) % matchingPageIndices.size
                                        scope.launch { listState.scrollToItem(matchingPageIndices[currentMatchIdx]) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.viewer_previous_match))
                            }
                            IconButton(
                                onClick = {
                                    if (matchingPageIndices.isNotEmpty()) {
                                        currentMatchIdx = (currentMatchIdx + 1) % matchingPageIndices.size
                                        scope.launch { listState.scrollToItem(matchingPageIndices[currentMatchIdx]) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.viewer_next_match))
                            }
                        } else if (searchQuery.isNotBlank() && !isSearchingText) {
                            Text(
                                stringResource(R.string.viewer_no_matches),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.viewer_close_search))
                        }
                    } else {
                        if (!isImageMode) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.viewer_search_pdf))
                            }
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.common_share))
                        }
                        if (!isImageMode) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (pageCount > 1) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.viewer_go_to_page)) },
                                        onClick = { showMenu = false; showJumpDialog = true },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (scale > 1f) R.string.viewer_reset_zoom else R.string.viewer_zoom_in)) },
                                    onClick = {
                                        showMenu = false
                                        scale = if (scale > 1f) 1f else 1.8f
                                        offsetX = 0f
                                        offsetY = 0f
                                    },
                                    leadingIcon = {
                                        Icon(if (scale > 1f) Icons.Default.ZoomOut else Icons.Default.ZoomIn, contentDescription = null)
                                    }
                                )
                                if (onCompress != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.viewer_compress_pdf)) },
                                        onClick = { showMenu = false; onCompress() },
                                        leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) }
                                    )
                                }
                                if (onProtect != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.viewer_protect_with_password)) },
                                        onClick = { showMenu = false; onProtect() },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.viewer_print_save_as_pdf)) },
                                    // The file on screen: a picked (content://) PDF has no path of its own, and a
                                    // protected one is shown from its unlocked copy. Before it is open, share instead.
                                    onClick = { showMenu = false; openedFile?.let { printPdfFile(context, it, title) } ?: onShare() },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.viewer_loading_document), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconTile(
                            androidx.compose.material.icons.Icons.Rounded.ErrorOutline,
                            TileTone.Rose,
                            size = 72.dp, corner = 24.dp, iconSize = 36.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.viewer_file_couldnt_open),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: stringResource(R.string.viewer_unknown_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                isImageMode && imageBitmap != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 3.5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap!!.asImageBitmap(),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                pageCount > 0 && pdfRenderer != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 3.5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // Pages sit as sheets on the tonal backdrop, with room for the page counter at the end.
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 56.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pageCount) { index ->
                                PdfPageItem(
                                    zoom = settledScale,
                                    renderer = pdfRenderer!!,
                                    renderLock = renderLock,
                                    pageCache = pageCache,
                                    pageIndex = index,
                                    pageCount = pageCount
                                )
                            }
                        }
                    }

                    if (pageCount > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                            shadowElevation = 6.dp
                        ) {
                            Text(
                                text = stringResource(R.string.viewer_page_counter, currentPage, pageCount),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }
        }

        if (needsPassword) {
            PdfPasswordDialog(
                error = passwordError,
                onCancel = onBack,
                onSubmit = { password ->
                    val source = lockedFile ?: return@PdfPasswordDialog
                    // A second tap while the first attempt runs would open a second renderer.
                    if (unlocking) return@PdfPasswordDialog
                    unlocking = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val unlocked = File(context.cacheDir, "view_unlocked_${System.currentTimeMillis()}.pdf")
                            tempFiles += unlocked
                            PdfSecurity.unlock(source, unlocked, password)
                            openRenderer(unlocked)
                            passwordError = null
                            needsPassword = false
                        } catch (_: Exception) {
                            passwordError = resources.getString(R.string.viewer_wrong_password)
                        } finally {
                            unlocking = false
                        }
                    }
                }
            )
        }

        if (showJumpDialog) {
            JumpToPageDialog(
                pageCount = pageCount,
                currentPage = currentPage,
                onDismiss = { showJumpDialog = false },
                onJump = { pageNum ->
                    showJumpDialog = false
                    scope.launch {
                        listState.scrollToItem((pageNum - 1).coerceIn(0, pageCount - 1))
                    }
                }
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    zoom: Float,
    renderer: PdfRenderer,
    renderLock: Mutex,
    pageCache: android.util.LruCache<Int, Bitmap>,
    pageIndex: Int,
    pageCount: Int
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sharp by remember { mutableStateOf<Bitmap?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var aspectRatio by remember { mutableFloatStateOf(0.707f) }
    var widthPx by remember { mutableIntStateOf(0) }

    // Keyed on the renderer too: after a password unlock the same page index belongs to a different file.
    // Drawn at 1.5x the page's width on screen (a fixed 1440 px left small print soft on high-density phones).
    LaunchedEffect(renderer, pageIndex, widthPx) {
        if (widthPx == 0) return@LaunchedEffect
        val targetW = (widthPx * 1.5f).toInt().coerceIn(720, 2400)
        pageCache[pageIndex]?.takeIf { it.width >= targetW }?.let { cached ->
            bitmap = cached
            aspectRatio = cached.width.toFloat() / cached.height.toFloat()
            isPageLoading = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                isPageLoading = bitmap == null
                // One page at a time: PdfRenderer throws if a second page is opened before the first is closed,
                // which is what left most pages blank when several were on screen.
                val bmp = renderPage(renderer, renderLock, pageIndex, targetW) { aspectRatio = it }
                pageCache.put(pageIndex, bmp)
                bitmap = bmp
            } catch (e: Exception) {
                bitmap = null
            } finally {
                isPageLoading = false
            }
        }
    }

    // Zoomed in: redraw this page at the zoomed resolution so text stays crisp, within a memory budget. Not cached;
    // dropped again when zoomed back out or the page scrolls away.
    LaunchedEffect(renderer, pageIndex, widthPx, zoom) {
        if (zoom < 1.2f || widthPx == 0) {
            sharp = null
            return@LaunchedEffect
        }
        val wanted = (widthPx * zoom * 1.25f).toInt()
        val maxW = kotlin.math.sqrt(ZOOM_PIXEL_BUDGET * aspectRatio).toInt()
        val targetW = minOf(wanted, maxW)
        if (targetW <= (bitmap?.width ?: 0)) {
            sharp = null
            return@LaunchedEffect
        }
        sharp = withContext(Dispatchers.IO) {
            runCatching { renderPage(renderer, renderLock, pageIndex, targetW) {} }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(Color.White)
                .onSizeChanged { widthPx = it.width },
            contentAlignment = Alignment.Center
        ) {
            val shown = sharp ?: bitmap
            if (shown != null) {
                Image(
                    bitmap = shown.asImageBitmap(),
                    contentDescription = stringResource(R.string.viewer_page_of, pageIndex + 1, pageCount),
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = FilterQuality.High
                )
            } else if (isPageLoading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}

/** Largest zoomed page bitmap, in pixels (about 40 MB as ARGB). */
private const val ZOOM_PIXEL_BUDGET = 10_000_000f

/** Renders one page [targetW] px wide on white; reports the page's aspect ratio. */
private suspend fun renderPage(renderer: PdfRenderer, renderLock: Mutex, pageIndex: Int, targetW: Int, onAspect: (Float) -> Unit): Bitmap =
    renderLock.withLock {
        renderer.openPage(pageIndex).use { page ->
            val aspect = page.width.toFloat() / page.height.toFloat()
            onAspect(aspect)
            val targetH = (targetW / aspect).toInt().coerceAtLeast(1)
            val bmp = createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    }

@Composable
private fun JumpToPageDialog(
    pageCount: Int,
    currentPage: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentPage.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_go_to_page)) },
        text = {
            Column {
                Text(stringResource(R.string.viewer_enter_page_number, pageCount), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        isError = false
                    },
                    isError = isError,
                    singleLine = true,
                    label = { Text(stringResource(R.string.viewer_page_number_label)) }
                )
                if (isError) {
                    Text(
                        stringResource(R.string.viewer_page_number_error, pageCount),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val num = textValue.toIntOrNull()
                    if (num != null && num in 1..pageCount) {
                        onJump(num)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text(stringResource(R.string.viewer_jump))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun PdfPasswordDialog(error: String?, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.viewer_password_required)) },
        text = {
            Column {
                Text(stringResource(R.string.viewer_password_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = error != null,
                    label = { Text(stringResource(R.string.viewer_password_label)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    // A password type keeps the keyboard from learning the password or suggesting it later.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) { Text(stringResource(R.string.viewer_open)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) } }
    )
}

