package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.feature.document.SignaturePadDialog
import com.example.scanpro.imaging.ImageProcessor
import com.example.scanpro.imaging.SignPlacement
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pages are drawn at this width in pixels: sharp on a phone, light on memory (one page at a time). */
private const val PAGE_RENDER_WIDTH = 1080

/**
 * Sign PDF: pick a page, add a drawn or saved signature or a typed stamp such as a date, drag it into place and
 * size or turn it, then save a new PDF. [onSign] starts the save; [busy] is true while the Tools screen is running
 * it (its progress and result dialogs sit on top of this screen).
 */
@Composable
fun SignPdfScreen(
    viewModel: SignPdfViewModel,
    busy: Boolean,
    onSign: (source: File, placements: List<SignPlacement>, name: String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved by viewModel.savedSignatures.collectAsStateWithLifecycle()
    if (!state.visible) return

    var confirmClose by rememberSaveable { mutableStateOf(false) }
    var padOpen by rememberSaveable { mutableStateOf(false) }
    var textOpen by rememberSaveable { mutableStateOf(false) }

    fun requestClose() {
        if (state.placements.isEmpty()) viewModel.close() else confirmClose = true
    }
    BackHandler(enabled = !busy) { requestClose() }

    val file = state.file
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { requestClose() }, enabled = !busy) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
                Text(
                    stringResource(R.string.tools_card_sign),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = file != null && state.placements.isNotEmpty() && !busy,
                    onClick = { if (file != null) onSign(file, state.placements, state.sourceName) },
                    modifier = Modifier.padding(end = 8.dp)
                ) { Text(stringResource(R.string.common_save)) }
            }

            when {
                state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.failed -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.sign_open_failed), style = MaterialTheme.typography.bodyLarge)
                }
                file != null -> {
                    if (state.wasProtected) {
                        Text(
                            stringResource(R.string.sign_unprotected_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    if (state.pageCount > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.showPage(state.page - 1) }, enabled = state.page > 0) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.sign_previous_page))
                            }
                            Text(
                                stringResource(R.string.sign_page_of, state.page + 1, state.pageCount),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            IconButton(onClick = { viewModel.showPage(state.page + 1) }, enabled = state.page < state.pageCount - 1) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.sign_next_page))
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SignPagePreview(viewModel, state, file)
                    }
                    val selected = state.selected
                    val placement = state.placements.getOrNull(selected)
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (placement == null) {
                            Text(
                                stringResource(R.string.sign_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.sign_size), modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = placement.widthFraction,
                                    onValueChange = { viewModel.resize(selected, it) },
                                    valueRange = SignPdfViewModel.MIN_WIDTH..SignPdfViewModel.MAX_WIDTH,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.sign_rotate), modifier = Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = placement.rotationDegrees.coerceIn(-180f, 180f),
                                    onValueChange = { viewModel.rotate(selected, it) },
                                    valueRange = -180f..180f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            TextButton(onClick = { viewModel.remove(selected) }) { Text(stringResource(R.string.sign_remove)) }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { padOpen = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sign_add_signature))
                        }
                        OutlinedButton(onClick = { textOpen = true }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sign_add_text))
                        }
                    }
                }
            }
        }
    }

    val passwordUri = state.passwordFor
    if (passwordUri != null) {
        SignPasswordDialog(
            wrong = state.wrongPassword,
            onDismiss = { viewModel.close() },
            onConfirm = { password -> viewModel.open(passwordUri, password) }
        )
    }
    if (padOpen) {
        SignaturePadDialog(
            saved = saved,
            onUseSaved = { chosen ->
                viewModel.addSavedSignature(chosen)
                padOpen = false
            },
            onDeleteSaved = { viewModel.deleteSavedSignature(it) },
            onDismiss = { padOpen = false }
        ) { bitmap, keep ->
            viewModel.addDrawnSignature(bitmap, keep)
            padOpen = false
        }
    }
    if (textOpen) {
        SignTextDialog(
            onDismiss = { textOpen = false },
            onConfirm = { text ->
                viewModel.addText(text)
                textOpen = false
            }
        )
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.sign_discard_title)) },
            text = { Text(stringResource(R.string.sign_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    viewModel.close()
                }) { Text(stringResource(R.string.sign_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.sign_keep_editing)) }
            }
        )
    }
}

/** The page being signed, with the stamps placed on it; dragging a stamp moves it. */
@Composable
private fun SignPagePreview(viewModel: SignPdfViewModel, state: SignUiState, file: File) {
    val page = state.page
    val rendered by produceState<Bitmap?>(null, file, page) {
        value = null
        value = viewModel.renderPage(file, page, PAGE_RENDER_WIDTH)
    }
    val image = rendered
    if (image == null) {
        CircularProgressIndicator()
        return
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val aspect = image.width.toFloat() / image.height.toFloat()
        val pageWidth = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
        val pageHeight = pageWidth / aspect
        val pageWidthPx = with(density) { pageWidth.toPx() }
        val pageHeightPx = with(density) { pageHeight.toPx() }
        Box(
            modifier = Modifier
                .size(pageWidth, pageHeight)
                .pointerInput(Unit) { detectTapGestures { viewModel.select(-1) } }
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            val description = stringResource(R.string.sign_stamp_description, page + 1)
            state.placements.forEachIndexed { index, placement ->
                if (placement.pageIndex == page) {
                    SignStamp(
                        placement = placement,
                        selected = index == state.selected,
                        pageWidthPx = pageWidthPx,
                        pageHeightPx = pageHeightPx,
                        description = description,
                        onSelect = { viewModel.select(index) },
                        onMove = { dx, dy -> viewModel.move(index, dx / pageWidthPx, dy / pageHeightPx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignStamp(
    placement: SignPlacement,
    selected: Boolean,
    pageWidthPx: Float,
    pageHeightPx: Float,
    description: String,
    onSelect: () -> Unit,
    onMove: (dx: Float, dy: Float) -> Unit
) {
    val picture by produceState<Bitmap?>(null, placement.imageFile) {
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeScaled(placement.imageFile.absolutePath, 600) }
    }
    val bitmap = picture ?: return
    val density = LocalDensity.current
    // The gesture blocks below outlive a recomposition; they must call the latest callbacks, not the first ones.
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMove by rememberUpdatedState(onMove)
    val widthPx = placement.widthFraction * pageWidthPx
    val heightPx = widthPx * bitmap.height.toFloat() / bitmap.width.toFloat()
    val left = placement.centerX * pageWidthPx - widthPx / 2f
    val top = placement.centerY * pageHeightPx - heightPx / 2f
    val outline = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { currentOnSelect() } }
            .pointerInput(pageWidthPx, pageHeightPx) {
                detectDragGestures(onDragStart = { currentOnSelect() }) { change, drag ->
                    change.consume()
                    currentOnMove(drag.x, drag.y)
                }
            }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = placement.rotationDegrees }
                .then(if (selected) Modifier.border(1.5.dp, outline) else Modifier)
        )
    }
}

@Composable
private fun SignPasswordDialog(wrong: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_password_title)) },
        text = {
            Column {
                Text(stringResource(R.string.sign_password_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sign_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    isError = wrong,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                if (wrong) {
                    Text(
                        stringResource(R.string.sign_password_wrong),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty(), onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.sign_password_open))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun SignTextDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // Starts as today's date, the usual thing to put beside a signature.
    var text by remember { mutableStateOf(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_text_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                singleLine = true,
                label = { Text(stringResource(R.string.sign_text_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.sign_text_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
