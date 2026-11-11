package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.data.CropQuad
import com.example.scanpro.feature.document.CropEditorDialog
import com.example.scanpro.feature.document.DiscardEditsDialog
import com.example.scanpro.imaging.ImageProcessor
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One picked photo in the Images to PDF editor. [file] is what goes into the PDF; [original] is the photo untouched. */
internal data class OrderedImage(
    val id: String = UUID.randomUUID().toString(),
    val file: File,
    val name: String,
    /** The picked photo, untouched: Reset goes back to this. */
    val original: File = file,
    val rotation: Int = 0
) {
    val isEdited: Boolean get() = file != original || rotation != 0
}

internal val OrderedImageListSaver = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<OrderedImage>, String>(
    save = { list -> list.map { "${it.id}|||${it.file.absolutePath}|||${it.name}|||${it.original.absolutePath}|||${it.rotation}" } },
    restore = { saved ->
        mutableStateListOf<OrderedImage>().apply {
            saved.forEach { line ->
                val parts = line.split("|||")
                if (parts.size >= 5) add(OrderedImage(parts[0], File(parts[1]), parts[2], File(parts[3]), parts[4].toIntOrNull() ?: 0))
            }
        }
    }
)

/**
 * Arranges the photos before they become a PDF. Each photo is a portrait page card that shows its real shape (so a
 * crop is visible), press-and-hold picks it up to drag, and a tap opens everything else (crop, rotate, move, reset,
 * remove) in one sheet. Working on a photo shows a progress veil over the grid instead of replacing it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagesToPdfEditor(
    initialUris: List<Uri>,
    displayName: (Uri) -> String,
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stagingDirPath = rememberSaveable { File(context.cacheDir, "img2pdf_order_${UUID.randomUUID()}").apply { mkdirs() }.absolutePath }
    val stagingDir = remember(stagingDirPath) { File(stagingDirPath).apply { mkdirs() } }

    val order = rememberSaveable(saver = OrderedImageListSaver) { mutableStateListOf<OrderedImage>() }
    var isLoading by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var croppingItem by remember { mutableStateOf<OrderedImage?>(null) }
    var cropFailed by remember { mutableStateOf(false) }
    var actionItem by remember { mutableStateOf<OrderedImage?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Anything moved, cropped, rotated, added or removed: closing then asks first.
    var changed by rememberSaveable { mutableStateOf(false) }

    suspend fun stage(uri: Uri, tag: Int): OrderedImage? = withContext(Dispatchers.IO) {
        runCatching {
            val name = displayName(uri)
            val ext = if (name.contains('.')) name.substringAfterLast('.', "jpg") else "jpg"
            val file = File(stagingDir, "item_${tag}_${System.currentTimeMillis()}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            if (file.length() > 0 && ImageProcessor.imageAspect(file.absolutePath) != null) OrderedImage(file = file, name = name)
            else { file.delete(); null }
        }.getOrNull()
    }

    val addImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            scope.launch {
                val base = order.size
                val added = uris.mapIndexedNotNull { i, uri -> stage(uri, base + i) }
                order.addAll(added)
                if (added.isNotEmpty()) changed = true
                isProcessing = false
            }
        }
    }

    LaunchedEffect(initialUris) {
        if (order.isEmpty()) {
            isLoading = true
            val loaded = initialUris.mapIndexedNotNull { i, uri -> stage(uri, i) }
            order.clear()
            order.addAll(loaded)
            isLoading = false
        }
    }

    val finishAndClean = { stagingDir.deleteRecursively(); onDismiss() }
    val requestClose = { if (changed) confirmDiscard = true else finishAndClean() }

    fun replaceFile(id: String, transform: (OrderedImage, File) -> OrderedImage, makeFile: (File) -> File) {
        val item = order.firstOrNull { it.id == id } ?: return
        isProcessing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { makeFile(item.file) } }
            isProcessing = false
            result.onSuccess { dest ->
                val idx = order.indexOfFirst { it.id == id }
                if (idx != -1) {
                    val previous = order[idx].file
                    order[idx] = transform(order[idx], dest)
                    changed = true
                    if (previous != order[idx].original && previous != dest) previous.delete()
                } else dest.delete()
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                cropFailed = true
            }
        }
    }

    fun rotate(id: String) = replaceFile(id, { it, dest -> it.copy(file = dest, rotation = (it.rotation + 90) % 360) }) { source ->
        File(stagingDir, "rotated_${System.currentTimeMillis()}_${source.nameWithoutExtension}.jpg").also { ImageProcessor.rotateImageFile(source, it, 90) }
    }

    fun reset(id: String) {
        val idx = order.indexOfFirst { it.id == id }
        val item = order.getOrNull(idx) ?: return
        if (!item.isEdited) return
        val previous = item.file
        order[idx] = item.copy(file = item.original, rotation = 0)
        if (previous != item.original) previous.delete()
        changed = true
    }

    fun remove(id: String) {
        val idx = order.indexOfFirst { it.id == id }
        if (idx < 0) return
        val removed = order.removeAt(idx)
        if (removed.file != removed.original) removed.file.delete()
        removed.original.delete()
        changed = true
    }

    fun move(id: String, to: (Int, Int) -> Int) {
        val from = order.indexOfFirst { it.id == id }
        if (from < 0) return
        val target = to(from, order.lastIndex).coerceIn(0, order.lastIndex)
        if (target != from) { order.add(target, order.removeAt(from)); changed = true }
    }

    croppingItem?.let { current ->
        CropEditorDialog(
            sourcePath = current.file.absolutePath,
            initial = CropQuad(),
            onDismiss = { croppingItem = null },
            onConfirm = { quad ->
                croppingItem = null
                replaceFile(current.id, { it, dest -> it.copy(file = dest) }) { source ->
                    File(stagingDir, "cropped_${System.currentTimeMillis()}_${source.nameWithoutExtension}.jpg").also { ImageProcessor.cropSourceToNewFile(source, it, quad) }
                }
            }
        )
    }

    if (cropFailed) {
        AlertDialog(
            onDismissRequest = { cropFailed = false },
            title = { Text(stringResource(R.string.tools_img2pdf_crop_failed_title)) },
            text = { Text(stringResource(R.string.tools_img2pdf_crop_failed)) },
            confirmButton = { TextButton(onClick = { cropFailed = false }) { Text(stringResource(R.string.common_close)) } }
        )
    }
    if (confirmDiscard) {
        DiscardEditsDialog(onKeepEditing = { confirmDiscard = false }, onDiscard = { confirmDiscard = false; finishAndClean() })
    }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(stringResource(R.string.tools_order_pages_title), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    stringResource(R.string.tools_img2pdf_pages_count, order.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = requestClose) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) }
                        },
                        actions = {
                            IconButton(onClick = { addImages.launch("image/*") }, enabled = !isLoading && !isProcessing) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tools_img2pdf_add_images))
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }, enabled = order.isNotEmpty() && !isProcessing) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more))
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tools_img2pdf_rotate_all)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null) },
                                        onClick = { menuOpen = false; order.map { it.id }.forEach { rotate(it) } }
                                    )
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                        Button(
                            onClick = { onConfirm(order.map { Uri.fromFile(it.file) }) },
                            enabled = !isLoading && !isProcessing && order.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                        ) { Text(stringResource(R.string.common_continue), fontWeight = FontWeight.SemiBold) }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    when {
                        isLoading -> CircularProgressIndicator(Modifier.padding(top = 80.dp))
                        order.isEmpty() -> Column(
                            Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.tools_err_no_images), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            OutlinedButton(onClick = { addImages.launch("image/*") }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.tools_img2pdf_add_images))
                            }
                        }
                        else -> Column(Modifier.fillMaxSize().widthIn(max = 1200.dp)) {
                            OrganizerBanner(stringResource(R.string.tools_img2pdf_drag_hint), warning = false)
                            ReorderablePageGrid(
                                items = order,
                                key = { it.id },
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                columns = GridCells.Adaptive(minSize = 150.dp),
                                onMove = { from, to -> order.add(to, order.removeAt(from)) },
                                onDragFinished = { changed = true }
                            ) { item, index, dragging ->
                                ImageCard(
                                    item = item,
                                    position = index + 1,
                                    isDragging = dragging,
                                    onClick = { actionItem = item }
                                )
                            }
                        }
                    }
                    if (isProcessing) {
                        // A veil over the grid, not a replacement for it: the pages stay where they are while one is worked on.
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)).pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }

    actionItem?.let { target ->
        val index = order.indexOfFirst { it.id == target.id }
        if (index >= 0) {
            val item = order[index]
            PageActionsSheet(
                title = item.name,
                subtitle = stringResource(R.string.tools_page_position, index + 1, order.size),
                onDismiss = { actionItem = null },
                header = {
                    Box(Modifier.size(width = 40.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        PageImage(item, Modifier.fillMaxSize(), maxDimension = 160)
                    }
                },
                actions = listOf(
                    PageAction(Icons.Default.Crop, stringResource(R.string.tools_img2pdf_crop_button)) { croppingItem = item },
                    PageAction(Icons.AutoMirrored.Filled.RotateRight, stringResource(R.string.tools_img2pdf_rotate_button)) { rotate(item.id) },
                    PageAction(Icons.Default.VerticalAlignTop, stringResource(R.string.tools_page_move_start), index > 0) { move(item.id) { _, _ -> 0 } },
                    PageAction(Icons.Default.KeyboardArrowUp, stringResource(R.string.tools_page_move_earlier), index > 0) { move(item.id) { from, _ -> from - 1 } },
                    PageAction(Icons.Default.KeyboardArrowDown, stringResource(R.string.tools_page_move_later), index < order.lastIndex) { move(item.id) { from, _ -> from + 1 } },
                    PageAction(Icons.Default.VerticalAlignBottom, stringResource(R.string.tools_page_move_end), index < order.lastIndex) { move(item.id) { _, last -> last } },
                    PageAction(Icons.Default.RestartAlt, stringResource(R.string.tools_img2pdf_reset), item.isEdited) { reset(item.id) },
                    PageAction(Icons.Default.Delete, stringResource(R.string.tools_img2pdf_remove), destructive = true) { remove(item.id) }
                )
            )
        }
    }
}


/** The photo as it will appear on the page, loaded off the main thread and reloaded when the file changes (a crop or turn). */
@Composable
private fun PageImage(item: OrderedImage, modifier: Modifier, maxDimension: Int = 420) {
    val bitmap by produceState<Bitmap?>(null, item.file.absolutePath, item.file.lastModified()) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(item.file.absolutePath, maxDimension) }
    }
    val shown = bitmap
    if (shown != null) {
        Image(shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
    }
}

@Composable
private fun ImageCard(item: OrderedImage, position: Int, isDragging: Boolean, onClick: () -> Unit) {
    val name = stringResource(R.string.tools_page_number, position)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "$name, ${item.name}" }
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.74f).padding(10.dp), contentAlignment = Alignment.Center) {
            PageImage(item, Modifier.fillMaxSize())
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.82f),
                modifier = Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)
            ) {
                Text(
                    "$position",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (item.isEdited) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.BottomStart)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.tools_img2pdf_edited_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                modifier = Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp).size(18.dp))
            }
        }
    }
}
