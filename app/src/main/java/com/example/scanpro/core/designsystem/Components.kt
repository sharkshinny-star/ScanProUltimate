package com.example.scanpro.core.designsystem

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.scanpro.imaging.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Decoded thumbnails keyed by path, file version and decode size, so scrolling back
// through the library reuses bitmaps instead of hitting the disk again.
private val thumbnailCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/** The full cache key last used for each thumbnail request, so a revisit can draw from the cache at once. */
private val latestCacheKey = LruCache<String, String>(512)

/** Decode size for small list/strip thumbnails. */
const val THUMBNAIL_SMALL = 256
/** Decode size for grid cards. */
const val THUMBNAIL_MEDIUM = 640
/** Decode size for the full-width page preview. */
const val THUMBNAIL_LARGE = 1600

@Composable
fun PageThumbnail(
    path: String?,
    modifier: Modifier = Modifier,
    maxDimension: Int = THUMBNAIL_MEDIUM,
    /**
     * Anything that changes when the image is re-rendered in place, normally the page itself. Edits
     * overwrite the same file, and with unchanged arguments this call would be skipped and keep showing
     * the old cached bitmap.
     */
    version: Any? = null
) {
    // The file's timestamp is part of the cache key, but reading it is disk I/O, so it happens on the
    // IO thread. Meanwhile the last bitmap shown for this request is drawn straight away, so coming
    // back to a tab shows its thumbnails instantly instead of blank placeholders.
    val requestKey = remember(path, version, maxDimension) { path?.let { "$it|${version.hashCode()}|$maxDimension" } }
    val bitmap by produceState(initialValue = requestKey?.let { latestCacheKey[it] }?.let { thumbnailCache[it] }, key1 = requestKey) {
        if (path == null || requestKey == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val cacheKey = "$requestKey|${File(path).lastModified()}"
            (thumbnailCache.get(cacheKey) ?: ImageProcessor.decodeScaled(path, maxDimension)?.also {
                thumbnailCache.put(cacheKey, it)
            })?.also { latestCacheKey.put(requestKey, cacheKey) }
        }
    }

    Box(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(12.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image == null) {
            // Lists show only the placeholder tint while thumbnails load; a spinner per card flickers on scroll.
            if (path != null && maxDimension >= THUMBNAIL_LARGE) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            val imageBitmap = remember(image) { image.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = "Scanned page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String,
    confirmLabel: String = "Save",
    maxLines: Int = 1,
    allowBlank: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Single-line values (names, tags) start selected so typing replaces them; longer text such as OCR
    // starts with the cursor at the end so a stray keypress can't wipe it.
    val initialSelection = if (maxLines == 1) TextRange(0, initial.length) else TextRange(initial.length)
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial, initialSelection)) }
    val focusRequester = remember { FocusRequester() }
    val canConfirm = allowBlank || value.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // Inside the dialog's own window, where the field exists by the time the effect runs.
            LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                maxLines = maxLines,
                singleLine = maxLines == 1,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (maxLines == 1) ImeAction.Done else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(onDone = { if (canConfirm) onConfirm(value.text) })
            )
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(value.text) }
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PasswordInputDialog(
    title: String,
    message: String,
    minimumLength: Int = 6,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text(message)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Minimum $minimumLength characters") }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = value.length >= minimumLength,
                onClick = { onConfirm(value) }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Wraps a top app bar with a progress strip that always reserves its 4dp, so starting or finishing a
 * task never shifts the screen's content up or down.
 */
@Composable
fun TopBarWithProgress(busy: Boolean, topBar: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column {
        topBar()
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** App bar title with an optional one-line status underneath, used to show what a running task is doing. */
@Composable
fun TitleWithStatus(title: String, status: String?) {
    androidx.compose.foundation.layout.Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!status.isNullOrBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
