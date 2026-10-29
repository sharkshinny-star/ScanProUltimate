package com.example.scanpro.feature.document

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.InsetDivider
import com.example.scanpro.core.designsystem.TileTone
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.data.ScanDocument

/**
 * Shown once, right after a scan is saved: name it, then share or save it without hunting through tabs.
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
    onDismiss: () -> Unit
) {
    var name by rememberSaveable(document.id) { mutableStateOf(document.title) }
    val focus = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** The name to use from now on: the edited one if it is valid, else the current title. */
    fun commitName(): String {
        val clean = name.trim()
        if (clean.isNotEmpty() && clean != document.title) onRename(clean)
        return clean.ifEmpty { document.title }
    }
    // Every action renames first and gets the new name, so a shared or saved file already carries it.
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PageThumbnail(
                    path = document.pages.minByOrNull { it.position }?.imagePath,
                    modifier = Modifier.size(width = 56.dp, height = 72.dp),
                    maxDimension = THUMBNAIL_SMALL,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan saved", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    val pages = if (document.pageCount == 1) "1 page" else "${document.pageCount} pages"
                    Text(
                        if (document.sizeBytes > 0) "$pages · ${formatBytes(document.sizeBytes)}" else pages,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text("Name") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); commitName() }),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column {
                    SheetAction(Icons.Default.PictureAsPdf, TileTone.Coral, "Share as PDF", "Email, WhatsApp, Gmail and other apps", !busy, then(onSharePdf))
                    InsetDivider()
                    SheetAction(Icons.Default.Image, TileTone.Sky, "Share as JPG", "One image per page", !busy, then(onShareJpg))
                    InsetDivider()
                    SheetAction(Icons.Default.AddToDrive, TileTone.Amber, "Save to Google Drive", "Or pick Dropbox, OneDrive or another cloud app", !busy, then(onSaveToDrive))
                    InsetDivider()
                    SheetAction(Icons.Default.Download, TileTone.Indigo, "Save to device", "Choose a folder such as Downloads", !busy, then(onSaveToDevice))
                    InsetDivider()
                    SheetAction(Icons.Default.Print, TileTone.Slate, "Print", null, !busy, then(onPrint))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = then { }, enabled = !busy) { Text("Done") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, tone: TileTone, title: String, subtitle: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
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
