package com.example.scanpro.core.designsystem

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/** Decode size for small list/strip thumbnails. */
const val THUMBNAIL_SMALL = 256
/** Decode size for grid cards. */
const val THUMBNAIL_MEDIUM = 640
/** Decode size for the full-width page preview. */
const val THUMBNAIL_LARGE = 1600

@Composable
fun PageThumbnail(path: String?, modifier: Modifier = Modifier, maxDimension: Int = THUMBNAIL_MEDIUM) {
    val fileVersion = path?.let { runCatching { File(it).lastModified() }.getOrDefault(0L) } ?: 0L
    val cacheKey = path?.let { "$it|$fileVersion|$maxDimension" }
    val bitmap by produceState(initialValue = cacheKey?.let { thumbnailCache.get(it) }, key1 = cacheKey) {
        if (path == null || cacheKey == null) {
            value = null
            return@produceState
        }
        value = thumbnailCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            ImageProcessor.decodeScaled(path, maxDimension)?.also { thumbnailCache.put(cacheKey, it) }
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
            if (path != null) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
    maxLines: Int = 3,
    allowBlank: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = maxLines
            )
        },
        confirmButton = {
            TextButton(
                enabled = allowBlank || value.isNotBlank(),
                onClick = { onConfirm(value) }
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
