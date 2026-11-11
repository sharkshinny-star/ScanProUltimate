package com.example.scanpro.feature.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.InsetDivider
import com.example.scanpro.core.designsystem.TileTone
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.intelligence.DocumentKind
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.scanpro.core.designsystem.ScanProTheme

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
/**
 * What the scan turned out to be, worth one tap right away (Adobe Scan and Microsoft Lens offer the same): a business
 * card to add to contacts, or a receipt or invoice with its total. Every value is an exact OCR substring.
 */
@Immutable
sealed interface ScanHighlight {
    data class Contact(val name: String?, val detail: String?) : ScanHighlight
    data class Bill(val kind: DocumentKind, val total: String, val date: String?) : ScanHighlight
}

/**
 * Shown once, right after a scan is saved, like the "scan saved" moment of the leading scanners: what was saved (pages,
 * size, whether its text was read), its name, a smart action when the scan is a business card or a bill, one-tap
 * Share / Copy text / Add pages, the other ways to save it, and Scan another to keep going.
 * A changed name is kept however the sheet is closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSavedSheet(
    document: ScanDocument,
    busy: Boolean,
    onRename: (String) -> Unit,
    onSharePdf: (title: String) -> Unit,
    onShareJpg: (title: String) -> Unit,
    onSaveToDevice: (title: String) -> Unit,
    onSaveToDrive: (title: String) -> Unit,
    onPrint: (title: String) -> Unit,
    /** Writes the recognised text to a Word file and shares it (Adobe Scan and iLovePDF both offer Word in one tap); null hides the row. */
    onShareWord: ((title: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    /** A business card or a bill found in the text; null for anything else (or before the text is read). */
    highlight: ScanHighlight? = null,
    /** Opens the new-contact form for a [ScanHighlight.Contact]. */
    onAddContact: () -> Unit = {},
    /** Copies a string (the bill's total, the page text); the sheet stays open. Null hides Copy text. */
    onCopy: ((String) -> Unit)? = null,
    /** Back to the camera to add pages to this document. */
    onAddPages: (() -> Unit)? = null,
    /** Straight to the camera for the next document. */
    onScanAnother: (() -> Unit)? = null
) {
    var name by rememberSaveable(document.id) { mutableStateOf(document.title) }
    val focus = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val text = document.ocrText

    /** The name to use from now on: the edited one if it is valid, else the current title. */
    fun commitName(): String {
        val clean = name.trim()
        if (clean.isNotEmpty() && clean != document.title) onRename(clean)
        return clean.ifEmpty { document.title }
    }
    // Every action that leaves the sheet renames first and gets the new name, so a shared or saved file carries it.
    fun then(action: (String) -> Unit): () -> Unit = {
        focus.clearFocus()
        val title = commitName()
        onDismiss()
        action(title)
    }

    ModalBottomSheet(
        onDismissRequest = { commitName(); onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                // The name field must stay above the keyboard.
                .imePadding()
        ) {
          // The options scroll; Scan another and Done are pinned below them so the way out is always on screen.
          Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageThumbnail(
                    path = document.pages.minByOrNull { it.position }?.imagePath,
                    modifier = Modifier.size(width = 60.dp, height = 78.dp),
                    maxDimension = THUMBNAIL_SMALL,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.doc_scan_saved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val pages = pluralStringResource(R.plurals.doc_page_count, document.pageCount, document.pageCount)
                    Text(
                        if (document.sizeBytes > 0) stringResource(R.string.doc_pages_and_size, pages, formatBytes(LocalContext.current, document.sizeBytes)) else pages,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // The text was read: it is a searchable PDF and every word can be found later.
                    if (text.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.doc_searchable_pdf), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text(stringResource(R.string.doc_name)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); commitName() }),
                modifier = Modifier.fillMaxWidth()
            )

            when (highlight) {
                is ScanHighlight.Contact -> HighlightCard(
                    icon = Icons.Default.Badge,
                    tone = TileTone.Teal,
                    title = stringResource(DocumentKind.BUSINESS_CARD.labelRes),
                    detail = listOfNotNull(highlight.name, highlight.detail).joinToString(" · ").ifEmpty { null },
                    action = stringResource(R.string.doc_add_to_contacts),
                    enabled = !busy,
                    onAction = onAddContact
                )
                is ScanHighlight.Bill -> HighlightCard(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    tone = TileTone.Amber,
                    title = stringResource(highlight.kind.labelRes),
                    detail = listOfNotNull(stringResource(R.string.doc_saved_total, highlight.total), highlight.date).joinToString(" · "),
                    action = stringResource(R.string.doc_saved_copy_total),
                    enabled = onCopy != null,
                    onAction = { onCopy?.invoke(highlight.total) }
                )
                null -> Unit
            }

            // The three things people do next, one tap each.
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                QuickAction(Icons.Default.PictureAsPdf, stringResource(R.string.doc_share_as_pdf), primary = true, enabled = !busy, modifier = Modifier.weight(1f), onClick = then(onSharePdf))
                if (onCopy != null) {
                    QuickAction(Icons.Default.ContentCopy, stringResource(R.string.doc_saved_copy_text), enabled = text.isNotBlank(), modifier = Modifier.weight(1f)) { onCopy(text) }
                }
                if (onAddPages != null) {
                    QuickAction(Icons.Default.AddAPhoto, stringResource(R.string.doc_saved_add_pages), enabled = !busy, modifier = Modifier.weight(1f), onClick = then { onAddPages() })
                }
            }
            // A greyed Copy text says nothing by itself: this says when it unlocks (it stays off for a page with no text).
            if (onCopy != null && text.isBlank()) {
                Text(
                    stringResource(R.string.doc_saved_copy_text_waiting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.doc_saved_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column {
                    SheetAction(Icons.Default.Image, TileTone.Sky, stringResource(R.string.doc_share_as_jpg), stringResource(R.string.doc_share_as_jpg_hint), !busy, then(onShareJpg))
                    if (onShareWord != null) {
                        InsetDivider()
                        // Editable text is the recognised words, so it waits for them rather than sharing an empty file.
                        SheetAction(
                            Icons.Default.Description, TileTone.Indigo, stringResource(R.string.doc_share_as_word),
                            stringResource(if (text.isBlank()) R.string.doc_share_as_word_waiting else R.string.doc_share_as_word_hint),
                            !busy && text.isNotBlank(), then(onShareWord)
                        )
                    }
                    InsetDivider()
                    SheetAction(Icons.Default.AddToDrive, TileTone.Amber, stringResource(R.string.doc_save_to_google_drive), stringResource(R.string.doc_save_to_google_drive_hint), !busy, then(onSaveToDrive))
                    InsetDivider()
                    SheetAction(Icons.Default.Download, TileTone.Indigo, stringResource(R.string.doc_save_to_device), stringResource(R.string.doc_save_to_device_hint), !busy, then(onSaveToDevice))
                    InsetDivider()
                    SheetAction(Icons.Default.Print, TileTone.Slate, stringResource(R.string.doc_print), null, !busy, then(onPrint))
                }
            }
          }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                if (onScanAnother != null) {
                    OutlinedButton(onClick = then { onScanAnother() }, enabled = !busy) { Text(stringResource(R.string.scan_code_scan_another)) }
                }
                Button(onClick = then { }, enabled = !busy) { Text(stringResource(R.string.common_done)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The smart action: what the scan is, the fact that matters, and one button. */
@Composable
private fun HighlightCard(icon: ImageVector, tone: TileTone, title: String, detail: String?, action: String, enabled: Boolean, onAction: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Surface(shape = RoundedCornerShape(18.dp), color = tone.container(), modifier = Modifier.fillMaxWidth()) {
        // At large font sizes the button would squeeze the detail text to a sliver, so it drops below it.
        val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
        val texts: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tone.content())
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = if (stacked) 4 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (stacked) {
            Column(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    texts(Modifier.weight(1f))
                }
                FilledTonalButton(onClick = onAction, enabled = enabled, modifier = Modifier.align(Alignment.End)) { Text(action, maxLines = 2) }
            }
        } else {
            Row(Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                texts(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onAction, enabled = enabled) { Text(action, maxLines = 1) }
            }
        }
    }
}

/** A big square action: icon over label. The primary one is on the brand gradient. */
@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val brand = ScanProTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val content = if (primary) brand.onGradient else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            // A minimum, not a fixed height: a two-line label at a large font size needs more room.
            .heightIn(min = 84.dp)
            .then(
                if (primary) Modifier.background(Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd)), shape)
                else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            )
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 6.dp)) {
            Icon(icon, contentDescription = null, tint = if (primary) content else MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, tone: TileTone, title: String, subtitle: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tone, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
