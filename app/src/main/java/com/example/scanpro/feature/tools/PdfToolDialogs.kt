package com.example.scanpro.feature.tools

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import com.example.scanpro.core.designsystem.FileLoadingState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.scanpro.imaging.PageNumberFormat
import com.example.scanpro.imaging.PageNumberPosition
import com.example.scanpro.imaging.PageNumbersConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.imaging.ImageExportFormat
import com.example.scanpro.imaging.PageRanges
import com.example.scanpro.imaging.PdfSecurity
import com.example.scanpro.imaging.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PdfToImagesDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (format: ImageExportFormat, pages: List<Int>?, scale: Float) -> Unit
) {
    val context = LocalContext.current
    var format by rememberSaveable { mutableStateOf(ImageExportFormat.JPEG) }
    var scale by rememberSaveable { mutableStateOf(2.5f) }
    var allPages by rememberSaveable { mutableStateOf(true) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uri) {
        pageCount = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { android.graphics.pdf.PdfRenderer(it).use { r -> r.pageCount } }
            }.getOrNull()
        }
    }

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(R.string.tools_pdf2img_dialog_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_pdf2img_export_button),
        onAction = {
            if (allPages) onConfirm(format, null, scale)
            else PageRanges.parse(rangesText, pageCount ?: Int.MAX_VALUE)
                .onSuccess { ranges -> onConfirm(format, ranges.flatMap { it.toList() }, scale) }
                .onFailure { error = it.message }
        },
        actionEnabled = true,
        actionSupport = null
    ) {
            Column {
                PickedFileRow(uri)
                Spacer(modifier = Modifier.height(12.dp))
                Text(stringResource(R.string.tools_pdf2img_format), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ImageExportFormat.entries.forEach { item ->
                        FilterChip(selected = format == item, onClick = { format = item }, label = { Text(item.extension.uppercase()) })
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.tools_pdf2img_resolution), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(R.string.tools_pdf2img_screen to 1.5f, R.string.tools_pdf2img_sharp to 2.5f, R.string.tools_pdf2img_print to 4f).forEach { (label, value) ->
                        FilterChip(selected = scale == value, onClick = { scale = value }, label = { Text(stringResource(label)) })
                    }
                }
                Text(
                    stringResource(
                        when (scale) {
                            1.5f -> R.string.tools_pdf2img_screen_hint
                            4f -> R.string.tools_pdf2img_print_hint
                            else -> R.string.tools_pdf2img_sharp_hint
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(stringResource(R.string.tools_pdf2img_pages), style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = allPages,
                        onClick = { allPages = true; error = null },
                        label = { Text(pageCount?.let { stringResource(R.string.tools_pdf2img_all_count, it) } ?: stringResource(R.string.tools_pdf2img_all)) }
                    )
                    FilterChip(selected = !allPages, onClick = { allPages = false }, label = { Text(stringResource(R.string.tools_pdf2img_some)) })
                }
                if (!allPages) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rangesText,
                        onValueChange = { rangesText = it; error = null },
                        label = { Text(stringResource(R.string.tools_pdf2img_pages_label)) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
}

/** Page size and quality for Images to PDF, the two settings the engine already takes. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ImagesToPdfDialog(count: Int, onDismiss: () -> Unit, onConfirm: (PdfPageSize, PdfQuality) -> Unit) {
    var size by rememberSaveable { mutableStateOf(PdfPageSize.AUTO) }
    var quality by rememberSaveable { mutableStateOf(PdfQuality.BALANCED) }
    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = pluralStringResource(R.plurals.tools_img2pdf_title, count, count),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_img2pdf_create),
        onAction = {
            onConfirm(size, quality)
        },
        actionEnabled = true,
        actionSupport = null
    ) {
            Column {
                Text(stringResource(R.string.tools_img2pdf_page_size), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PdfPageSize.entries.forEach { item ->
                        FilterChip(
                            selected = size == item,
                            onClick = { size = item },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
                Text(
                    if (size == PdfPageSize.AUTO) stringResource(R.string.tools_img2pdf_auto_hint) else stringResource(R.string.tools_img2pdf_fitted_hint, stringResource(size.labelRes)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.tools_img2pdf_quality), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PdfQuality.entries.forEach { item ->
                        FilterChip(
                            selected = quality == item,
                            onClick = { quality = item },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        }
}

@Composable
internal fun PasswordDialog(
    isProtect: Boolean,
    initialError: String? = null,
    /** Set when Merge asks for the password of this file: the dialog names it and the button carries on merging. */
    mergeFileName: String? = null,
    /** The file being protected or unlocked, shown above the question; not set when Merge asks. */
    fileUri: Uri? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorText by remember(initialError) { mutableStateOf(initialError) }
    // Unlocking must accept whatever password the file already has, however short.
    val minimum = if (isProtect) PdfSecurity.MIN_PASSWORD_LENGTH else 1
    // Read here: the confirm button's onClick is not a composable scope.
    val tooShortError = pluralStringResource(R.plurals.tools_password_too_short, minimum, minimum)
    val enterPasswordError = stringResource(R.string.tools_password_enter)
    val mismatchError = stringResource(R.string.tools_password_mismatch)

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(
                    when {
                        mergeFileName != null -> R.string.ui_pdf_password_title
                        isProtect -> R.string.tools_password_protect_title
                        else -> R.string.tools_unlock_pdf
                    }
                ),
        onClose = onDismiss,
        actionLabel = stringResource(
            when {
                mergeFileName != null -> R.string.common_continue
                isProtect -> R.string.tools_protect_pdf
                else -> R.string.tools_unlock_pdf
            }
        ),
        onAction = {
            if (password.length < minimum) {
                errorText = if (isProtect) tooShortError else enterPasswordError
            } else if (isProtect && confirm != password) {
                errorText = mismatchError
            } else {
                onConfirm(password)
            }
        },
        actionEnabled = true,
        actionSupport = null
    ) {
            Column {
                if (fileUri != null) {
                    PickedFileRow(fileUri)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    when {
                        mergeFileName != null -> stringResource(R.string.tools_merge_password_prompt, mergeFileName)
                        isProtect -> pluralStringResource(R.plurals.tools_password_protect_warning, PdfSecurity.MIN_PASSWORD_LENGTH, PdfSecurity.MIN_PASSWORD_LENGTH)
                        else -> stringResource(R.string.tools_password_unlock_prompt)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (mergeFileName != null) {
                    // The pages leave the protected file behind: say so before the user carries on.
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tools_merge_password_unprotected_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorText = null },
                    label = { Text(stringResource(R.string.tools_password_label)) },
                    singleLine = true,
                    isError = errorText != null,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    // Password type even while shown, so the keyboard never learns or suggests it.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(R.string.tools_password_toggle_visibility)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isProtect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; errorText = null },
                        label = { Text(stringResource(R.string.tools_password_confirm_label)) },
                        singleLine = true,
                        isError = errorText != null,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (errorText != null) {
                    Text(
                        errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
}

@Composable
internal fun SplitDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (SplitMode, String, Int) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var mode by rememberSaveable { mutableStateOf(SplitMode.SINGLE_PAGES) }
    var rangesText by rememberSaveable { mutableStateOf("") }
    var chunkSizeText by rememberSaveable { mutableStateOf("2") }
    var error by remember { mutableStateOf<String?>(null) }
    // The page count makes the ranges checkable before the split starts.
    var pageCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uri) {
        pageCount = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { android.graphics.pdf.PdfRenderer(it).use { r -> r.pageCount } }
            }.getOrNull()
        }
        if (rangesText.isEmpty()) pageCount?.let { rangesText = if (it > 1) "1-${(it + 1) / 2}, ${(it + 1) / 2 + 1}-$it" else "1" }
    }

    if (pageCount == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.tools_split_title)) },
            text = {
                FileLoadingState(stringResource(R.string.tools_organizer_opening), Modifier.fillMaxWidth(), size = 72.dp)
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
        )
        return
    }

    if (pageCount == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            title = {
                Text(
                    stringResource(R.string.tools_split_single_page_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.tools_split_single_page_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.tools_split_single_page_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_done))
                }
            }
        )
        return
    }

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(R.string.tools_split_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_split_pdf),
        onAction = {
            val n = chunkSizeText.toIntOrNull()
            val pages = pageCount ?: Int.MAX_VALUE
            when (mode) {
                SplitMode.PAGE_RANGES -> PageRanges.parse(rangesText, pages)
                    .onSuccess { onConfirm(mode, rangesText, 1) }
                    .onFailure { error = it.message }
                SplitMode.EVERY_N_PAGES -> when {
                    n == null || n < 1 -> error = resources.getString(R.string.tools_split_enter_count)
                    pageCount != null && n >= pageCount!! -> error = resources.getQuantityString(R.plurals.tools_split_whole_document, pageCount!!, pageCount!!)
                    else -> onConfirm(mode, rangesText, n)
                }
                SplitMode.SINGLE_PAGES -> onConfirm(mode, rangesText, 1)
            }
        },
        actionEnabled = true,
        actionSupport = null
    ) {
            Column {
                PickedFileRow(uri)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    pageCount?.let { pluralStringResource(R.plurals.tools_split_has_pages, it, it) } ?: stringResource(R.string.tools_split_choose),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                SplitMode.entries.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { mode = item }
                            .padding(vertical = 6.dp)
                    ) {
                        RadioButton(selected = mode == item, onClick = { mode = item })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(stringResource(item.labelRes), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(item.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (mode == SplitMode.PAGE_RANGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rangesText,
                        onValueChange = { rangesText = it; error = null },
                        label = { Text(stringResource(R.string.tools_split_ranges_example)) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (mode == SplitMode.EVERY_N_PAGES) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chunkSizeText,
                        onValueChange = { chunkSizeText = it.filter(Char::isDigit); error = null },
                        label = { Text(stringResource(R.string.tools_split_pages_per_file)) },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PageNumbersPdfDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (PageNumbersConfig) -> Unit
) {
    var selectedPosition by rememberSaveable { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var selectedFormat by rememberSaveable { mutableStateOf(PageNumberFormat.PAGE_N_OF_M) }
    var skipCover by rememberSaveable { mutableStateOf(true) }

    // A full tool page (F146) instead of a pop-up: options on cards, the button always in reach at the bottom.
    ToolPage(
        title = stringResource(R.string.tools_page_numbers_title),
        onClose = onDismiss,
        actionLabel = stringResource(R.string.tools_page_numbers_action),
        onAction = {
            onConfirm(
                PageNumbersConfig(
                    position = selectedPosition,
                    format = selectedFormat,
                    skipFirstPage = skipCover
                )
            )
        },
        actionEnabled = true,
        actionSupport = null
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PickedFileRow(uri)
                Text(
                    stringResource(R.string.tools_page_numbers_position_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val posList = listOf(
                        PageNumberPosition.BOTTOM_CENTER to stringResource(R.string.tools_page_numbers_pos_bottom_center),
                        PageNumberPosition.BOTTOM_RIGHT to stringResource(R.string.tools_page_numbers_pos_bottom_right),
                        PageNumberPosition.BOTTOM_LEFT to stringResource(R.string.tools_page_numbers_pos_bottom_left),
                        PageNumberPosition.TOP_CENTER to stringResource(R.string.tools_page_numbers_pos_top_center),
                        PageNumberPosition.TOP_RIGHT to stringResource(R.string.tools_page_numbers_pos_top_right)
                    )
                    posList.forEach { (pos, label) ->
                        FilterChip(
                            selected = selectedPosition == pos,
                            onClick = { selectedPosition = pos },
                            label = { Text(label) }
                        )
                    }
                }

                Text(
                    stringResource(R.string.tools_page_numbers_format_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val formats = listOf(
                        PageNumberFormat.PAGE_N_OF_M to stringResource(R.string.tools_page_numbers_fmt_page_n_of_m),
                        PageNumberFormat.N_SLASH_M to stringResource(R.string.tools_page_numbers_fmt_n_slash_m),
                        PageNumberFormat.DASH to stringResource(R.string.tools_page_numbers_fmt_dash),
                        PageNumberFormat.SIMPLE to stringResource(R.string.tools_page_numbers_fmt_simple)
                    )
                    formats.forEach { (fmt, label) ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { skipCover = !skipCover },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = skipCover,
                        onCheckedChange = { skipCover = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.tools_page_numbers_skip_cover),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
}
