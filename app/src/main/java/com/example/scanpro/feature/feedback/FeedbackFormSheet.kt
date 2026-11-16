package com.example.scanpro.feature.feedback

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.scanpro.R
import com.example.scanpro.core.ui.SupportDelivery
import com.example.scanpro.core.ui.SupportMessage
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A screenshot copied into the app's share cache, so the email app can be granted it: its shareable URI and a thumbnail. */
class FeedbackAttachment(val uri: Uri, val thumbnail: ImageBitmap?)

/** Where picked screenshots are copied; under the FileProvider's "exports" cache path. */
private fun attachmentDir(context: Context) = File(context.cacheDir, "exports/feedback")

/**
 * Copies a picked image into the share cache and returns it as this app's content URI (the photo picker's own URI
 * cannot be passed on to the email app). Null if it can't be read. Runs on the IO dispatcher.
 */
private suspend fun copyAttachment(context: Context, source: Uri, index: Int): FeedbackAttachment? = withContext(Dispatchers.IO) {
    runCatching {
        val type = context.contentResolver.getType(source).orEmpty()
        val extension = when {
            type.endsWith("png") -> "png"
            type.endsWith("webp") -> "webp"
            type.endsWith("gif") -> "gif"
            else -> "jpg"
        }
        val file = File(attachmentDir(context).apply { mkdirs() }, "screenshot_${System.currentTimeMillis()}_$index.$extension")
        context.contentResolver.openInputStream(source)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 192) sample *= 2
        val thumb = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        FeedbackAttachment(FileProvider.getUriForFile(context, "${context.packageName}.files", file), thumb?.asImageBitmap())
    }.getOrNull()
}

/**
 * "Report an issue" or "Suggest a feature": category chips (issues only), a description of at least six characters,
 * optional screenshots from the photo picker (no storage permission), then Submit, which opens the email app with all
 * of it filled in. An issue report also carries the diagnostic report, as before. [onSent] gets how it was delivered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackFormSheet(kind: FeedbackFormKind, onSent: (SupportDelivery) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var categories by rememberSaveable { mutableStateOf(emptyList<IssueCategory>()) }
    var text by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(emptyList<FeedbackAttachment>()) }
    var attachFailed by remember { mutableStateOf(false) }
    // Screenshots from earlier reports are left for a day, so an email app still sending one can read it.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            attachmentDir(context).listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS)) { uris ->
        val room = MAX_SCREENSHOTS - attachments.size
        if (uris.isEmpty() || room <= 0) return@rememberLauncherForActivityResult
        scope.launch {
            val added = uris.take(room).mapIndexedNotNull { i, uri -> copyAttachment(context, uri, i) }
            attachFailed = added.size < uris.take(room).size
            attachments = attachments + added
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        FeedbackFormContent(
            kind = kind,
            categories = categories.toSet(),
            text = text,
            attachments = attachments,
            attachFailed = attachFailed,
            sending = sending,
            onToggleCategory = { c -> categories = if (c in categories) categories - c else categories + c },
            onTextChange = { text = it },
            onAttach = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveAttachment = { index -> attachments = attachments.filterIndexed { i, _ -> i != index } },
            onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } },
            onSubmit = {
                sending = true
                scope.launch {
                    val chosen = IssueCategory.entries.filter { it in categories }.joinToString { context.getString(it.label) }
                    val message = if (kind == FeedbackFormKind.ISSUE) {
                        SupportMessage(
                            subject = context.getString(R.string.feedback_email_issue_subject, chosen),
                            text = context.getString(R.string.feedback_email_categories, chosen) + "\n\n" + text.trim(),
                            attachments = attachments.map { it.uri }
                        )
                    } else {
                        SupportMessage(text = text.trim(), attachments = attachments.map { it.uri })
                    }
                    val topic = if (kind == FeedbackFormKind.ISSUE) SupportTopic.ISSUE else SupportTopic.FEATURE
                    val delivery = contactSupport(context, topic, message)
                    sending = false
                    onSent(delivery)
                    onClose()
                }
            }
        )
    }
}

/** The form without its sheet, so the screenshot tests can draw it. */
@Composable
fun FeedbackFormContent(
    kind: FeedbackFormKind,
    categories: Set<IssueCategory>,
    text: String,
    attachments: List<FeedbackAttachment>,
    attachFailed: Boolean = false,
    sending: Boolean = false,
    onToggleCategory: (IssueCategory) -> Unit,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit
) {
    val issue = kind == FeedbackFormKind.ISSUE
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (issue) R.string.settings_report_issue_title else R.string.settings_request_features),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.feedback_close))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (issue) R.string.feedback_form_issue_heading else R.string.feedback_form_feature_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (issue) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IssueCategory.entries.forEach { category ->
                    val on = category in categories
                    FilterChip(
                        selected = on,
                        onClick = { onToggleCategory(category) },
                        label = { Text(stringResource(category.label)) },
                        leadingIcon = if (on) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(stringResource(if (issue) R.string.feedback_form_issue_hint else R.string.feedback_form_feature_hint)) },
            minLines = 5,
            maxLines = 10,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAttach, enabled = attachments.size < MAX_SCREENSHOTS) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(if (attachments.isEmpty()) R.string.feedback_attach else R.string.feedback_attach_more))
            }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.feedback_attach_note, MAX_SCREENSHOTS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        if (attachFailed) {
            Text(
                stringResource(R.string.feedback_attach_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (attachments.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                attachments.forEachIndexed { index, attachment -> Thumbnail(attachment, index, onRemoveAttachment) }
            }
        }
        if (issue) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.feedback_form_issue_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSubmit,
            enabled = !sending && canSubmitForm(kind, categories, text),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (sending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.feedback_form_submit), fontWeight = FontWeight.Bold)
            }
        }
        Text(
            stringResource(R.string.feedback_submit_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
}

@Composable
private fun Thumbnail(attachment: FeedbackAttachment, index: Int, onRemove: (Int) -> Unit) {
    Box(Modifier.size(72.dp)) {
        val shape = RoundedCornerShape(12.dp)
        val thumb = attachment.thumbnail
        if (thumb != null) {
            Image(
                thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(shape)
            )
        } else {
            Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape))
        }
        IconButton(
            onClick = { onRemove(index) },
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
        ) {
            Box(
                Modifier.size(22.dp).background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.feedback_attach_remove, index + 1),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
