package com.example.scanpro.feature.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.scanpro.imaging.PdfProtection
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.core.ui.shareFile
import java.io.File
import java.io.FileOutputStream

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
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableStateOf(0) }
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

    val currentPage by remember {
        derivedStateOf {
            if (pageCount == 0) 1
            else (listState.firstVisibleItemIndex + 1).coerceIn(1, pageCount)
        }
    }

    // PdfRenderer can have only one page open at a time, so every page render and the final close share this lock.
    val renderLock = remember { Mutex() }
    // Copies made for viewing (including decrypted ones) are removed when the viewer closes.
    val tempFiles = remember { mutableListOf<File>() }
    var needsPassword by remember { mutableStateOf(false) }
    // The file actually on screen: an unlocked or unrestricted copy when the original is protected.
    var openedFile by remember { mutableStateOf<File?>(null) }
    var lockedFile by remember { mutableStateOf<File?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Set when the viewer closes; a renderer that finishes opening after that is closed at once instead of leaking.
    val viewerClosed = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var unlocking by remember { mutableStateOf(false) }

    fun openRenderer(file: File) {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(descriptor)
            val stale = runBlocking {
                renderLock.withLock {
                    if (viewerClosed.get()) true else {
                        // Replace (and close) any renderer already open, e.g. after a second password attempt.
                        runCatching { pdfRenderer?.close() }
                        runCatching { pfd?.close() }
                        false
                    }
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
                    val bmp = if (pdfUri.scheme == "file" && pdfUri.path != null) {
                        BitmapFactory.decodeFile(pdfUri.path)
                    } else {
                        context.contentResolver.openInputStream(pdfUri)?.use { input ->
                            BitmapFactory.decodeStream(input)
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
                    } ?: throw IllegalStateException("Could not open file")
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
                errorMessage = e.localizedMessage ?: "Failed to load document"
            } finally {
                isLoading = false
            }
        }

        onDispose {
            viewerClosed.set(true)
            job.cancel()
            runBlocking {
                renderLock.withLock {
                    runCatching { pdfRenderer?.close() }
                    runCatching { pfd?.close() }
                    pdfRenderer = null
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
        kotlinx.coroutines.delay(350)
        isSearchingText = true
        try {
            val query = searchQuery
            val matches = withContext(Dispatchers.IO) {
                val found = mutableListOf<Int>()
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
                    if (stream.exists()) {
                        PDDocument.load(stream).use { pdDoc ->
                            val stripper = PDFTextStripper()
                            for (i in 0 until pdDoc.numberOfPages) {
                                // Stop as soon as a newer query replaces this one.
                                ensureActive()
                                stripper.startPage = i + 1
                                stripper.endPage = i + 1
                                if (stripper.getText(pdDoc).contains(query, ignoreCase = true)) found.add(i)
                            }
                        }
                    }
                } finally {
                    // Each search may copy the document; never leave those copies behind.
                    tempPdf.delete()
                }
                found
            }
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
                            placeholder = { Text("Search keyword in PDF…") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            trailingIcon = {
                                if (isSearchingText) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
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
                                    text = if (isImageMode) "Image Preview" else "Page $currentPage of $pageCount",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        if (matchingPageIndices.isNotEmpty()) {
                            Text(
                                "${currentMatchIdx + 1}/${matchingPageIndices.size}",
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
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Previous match")
                            }
                            IconButton(
                                onClick = {
                                    if (matchingPageIndices.isNotEmpty()) {
                                        currentMatchIdx = (currentMatchIdx + 1) % matchingPageIndices.size
                                        scope.launch { listState.scrollToItem(matchingPageIndices[currentMatchIdx]) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Next match")
                            }
                        } else if (searchQuery.isNotBlank() && !isSearchingText) {
                            Text(
                                "0 matches",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        if (!isImageMode) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search PDF")
                            }
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        if (!isImageMode) {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (pageCount > 1) {
                                    DropdownMenuItem(
                                        text = { Text("Go to Page") },
                                        onClick = { showMenu = false; showJumpDialog = true },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (scale > 1f) "Reset Zoom" else "Zoom In") },
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
                                        text = { Text("Compress PDF") },
                                        onClick = { showMenu = false; onCompress() },
                                        leadingIcon = { Icon(Icons.Default.Compress, contentDescription = null) }
                                    )
                                }
                                if (onProtect != null) {
                                    DropdownMenuItem(
                                        text = { Text("Protect with Password") },
                                        onClick = { showMenu = false; onProtect() },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Print / Save as PDF") },
                                    onClick = { showMenu = false; shareFile(context, pdfUri.path ?: "", "application/pdf", title) },
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
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading document…", style = MaterialTheme.typography.bodyMedium)
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
                        Text(
                            text = "Error Loading File",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pageCount) { index ->
                                PdfPageItem(
                                    renderer = pdfRenderer!!,
                                    renderLock = renderLock,
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
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                            shadowElevation = 4.dp
                        ) {
                            Text(
                                text = "$currentPage / $pageCount",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
                        } catch (e: Exception) {
                            passwordError = "That password didn't work"
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
    renderer: PdfRenderer,
    renderLock: Mutex,
    pageIndex: Int,
    pageCount: Int
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPageLoading by remember { mutableStateOf(true) }
    var aspectRatio by remember { mutableFloatStateOf(0.707f) }

    LaunchedEffect(pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                isPageLoading = true
                // One page at a time: PdfRenderer throws if a second page is opened before the first is closed,
                // which is what left most pages blank when several were on screen.
                renderLock.withLock {
                    renderer.openPage(pageIndex).use { page ->
                        aspectRatio = page.width.toFloat() / page.height.toFloat()
                        val targetW = 1440
                        val targetH = (targetW / aspectRatio).toInt().coerceAtLeast(1)
                        val bmp = createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap = bmp
                    }
                }
            } catch (e: Exception) {
                bitmap = null
            } finally {
                isPageLoading = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    filterQuality = FilterQuality.High
                )
            } else if (isPageLoading) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
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
        title = { Text("Go to Page") },
        text = {
            Column {
                Text("Enter page number (1 to $pageCount):", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        isError = false
                    },
                    isError = isError,
                    singleLine = true,
                    label = { Text("Page Number") }
                )
                if (isError) {
                    Text(
                        "Please enter a number between 1 and $pageCount",
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
                Text("Jump")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PdfPasswordDialog(error: String?, onCancel: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Password required") },
        text = {
            Column {
                Text("This PDF is protected. Enter its password to open it.", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    isError = error != null,
                    label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { onSubmit(password) }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

