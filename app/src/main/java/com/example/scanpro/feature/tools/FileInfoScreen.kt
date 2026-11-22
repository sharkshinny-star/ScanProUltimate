package com.example.scanpro.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.imaging.metadata.FileFamily
import com.example.scanpro.imaging.metadata.FileFormat
import com.example.scanpro.imaging.metadata.MetaField
import com.example.scanpro.imaging.metadata.MetaGroup
import com.example.scanpro.imaging.metadata.MetadataEdit
import com.example.scanpro.imaging.metadata.Sensitivity

/** Groups with more rows than this start folded, showing the first few. */
private const val FOLDED_ROWS = 8

/**
 * File info and metadata (F243): any file's details, grouped, searchable, with the ones this format can change marked
 * with a pencil (tap to edit) and the rest with a lock. Privacy actions at the top (Remove location, Remove personal
 * details, Remove all), export as JSON or CSV, and Save a copy once something is edited. The original is never changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FileInfoScreen(
    session: FileInfoSession,
    onDismiss: () -> Unit,
    onSave: (MetadataEdit) -> Unit,
    onExport: (json: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val meta = session.meta
    val abilities = meta.abilities
    // Edits waiting to be saved, by field id: a value, or "" to remove.
    var pending by remember(session.uri) { mutableStateOf(mapOf<String, String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MetaField?>(null) }
    val shown = meta.search(query)

    ToolPage(
        title = stringResource(R.string.tools_card_pdf_info),
        onClose = onDismiss,
        actionLabel = if (pending.isEmpty()) stringResource(R.string.meta_action_save) else pluralStringResource(R.plurals.meta_action_save_changes, pending.size, pending.size),
        onAction = { onSave(MetadataEdit(values = pending.mapValues { it.value.ifEmpty { null } })) },
        actionEnabled = pending.isNotEmpty(),
        actionSupport = if (pending.isEmpty() && abilities.canEdit) stringResource(R.string.meta_action_hint) else null,
        demoToolId = null
    ) {
        FileHeader(meta.fileName, meta.format.name, formatBytes(context, meta.sizeBytes), meta.format.family)

        if (abilities.readOnlyReason != 0) {
            ImageToolNotice(stringResource(abilities.readOnlyReason))
        }

        val removeLocation = abilities.canRemoveLocation && meta.hasLocation
        val removePersonal = abilities.canRemovePersonal && meta.hasPersonal
        if (removeLocation || removePersonal || abilities.canRemoveAll) {
            ToolSection(stringResource(R.string.meta_section_privacy)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (removeLocation) AssistChip(
                        onClick = { onSave(MetadataEdit(removeLocation = true)) },
                        label = { Text(stringResource(R.string.meta_remove_location)) },
                        leadingIcon = { Icon(Icons.Default.LocationOff, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (removePersonal) AssistChip(
                        onClick = { onSave(MetadataEdit(removePersonal = true)) },
                        label = { Text(stringResource(R.string.meta_remove_personal)) },
                        leadingIcon = { Icon(Icons.Default.PersonOff, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    if (abilities.canRemoveAll) AssistChip(
                        onClick = { onSave(MetadataEdit(removeAll = true)) },
                        label = { Text(stringResource(R.string.meta_remove_all)) },
                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                ToolHint(stringResource(R.string.meta_privacy_hint))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.meta_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.meta_clear_search)) } }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (shown.isEmpty()) ToolHint(stringResource(R.string.meta_search_none))

        MetaGroup.entries.forEach { group ->
            val rows = shown.filter { it.group == group }
            if (rows.isNotEmpty()) {
                FieldGroup(
                    title = stringResource(groupTitle(group)),
                    rows = rows,
                    // While searching, every match shows.
                    foldable = query.isBlank(),
                    pending = pending,
                    showLocks = abilities.canEdit,
                    onEdit = { editing = it }
                )
            }
        }

        ToolSection(stringResource(R.string.meta_section_export)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onExport(true) }, label = { Text("JSON") }, leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(18.dp)) })
                AssistChip(onClick = { onExport(false) }, label = { Text("CSV") }, leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) })
            }
            ToolHint(stringResource(R.string.meta_export_hint, meta.fields.size))
        }
        ToolHint(stringResource(R.string.meta_formats_note, FileFormat.knownExtensionCount))
    }

    editing?.let { field ->
        TextInputDialog(
            title = field.name,
            initial = pending[field.id] ?: field.value,
            label = field.name,
            allowBlank = true,
            message = stringResource(R.string.meta_edit_hint),
            onDismiss = { editing = null }
        ) { value ->
            pending = if (value == field.value) pending - field.id else pending + (field.id to value.trim())
            editing = null
        }
    }
}

@Composable
private fun FileHeader(name: String, format: String, size: String, family: FileFamily) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(familyIcon(family), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(10.dp).size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$format · $size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun familyIcon(family: FileFamily): ImageVector = when (family) {
    FileFamily.PDF -> Icons.Default.PictureAsPdf
    FileFamily.IMAGE, FileFamily.RAW_IMAGE, FileFamily.VECTOR_IMAGE -> Icons.Default.Image
    FileFamily.AUDIO -> Icons.Default.Audiotrack
    FileFamily.VIDEO -> Icons.Default.Movie
    FileFamily.OFFICE, FileFamily.OFFICE_LEGACY, FileFamily.OPEN_DOCUMENT, FileFamily.EBOOK, FileFamily.TEXT -> Icons.Default.Description
    FileFamily.ARCHIVE -> Icons.Default.FolderZip
    FileFamily.OTHER -> Icons.Default.InsertDriveFile
}

private fun groupTitle(group: MetaGroup): Int = when (group) {
    MetaGroup.FILE -> R.string.meta_group_file
    MetaGroup.DOCUMENT -> R.string.meta_group_document
    MetaGroup.IMAGE -> R.string.meta_group_image
    MetaGroup.CAMERA -> R.string.meta_group_camera
    MetaGroup.LOCATION -> R.string.meta_group_location
    MetaGroup.AUDIO -> R.string.meta_group_audio
    MetaGroup.VIDEO -> R.string.meta_group_video
    MetaGroup.CONTENTS -> R.string.meta_group_contents
    MetaGroup.TAGS -> R.string.meta_group_tags
}

/** One group of details in a card; long groups fold to their first rows, with "Show all" under them. */
@Composable
private fun FieldGroup(
    title: String,
    rows: List<MetaField>,
    foldable: Boolean,
    pending: Map<String, String>,
    showLocks: Boolean,
    onEdit: (MetaField) -> Unit,
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val folded = foldable && !open && rows.size > FOLDED_ROWS
    val visible = if (folded) rows.take(FOLDED_ROWS) else rows
    ToolSection(title) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column {
                visible.forEachIndexed { i, field ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    FieldRow(field, pending[field.id], showLocks, onEdit)
                }
                if (foldable && rows.size > FOLDED_ROWS) {
                    TextButton(onClick = { open = !open }, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(if (open) stringResource(R.string.meta_show_fewer) else pluralStringResource(R.plurals.meta_show_all, rows.size, rows.size))
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(field: MetaField, pendingValue: String?, showLocks: Boolean, onEdit: (MetaField) -> Unit) {
    val changed = pendingValue != null
    val value = pendingValue ?: field.value
    val readOnly = stringResource(R.string.meta_read_only)
    val editLabel = stringResource(R.string.meta_edit_field, field.name)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (field.editable) Modifier.clickable { onEdit(field) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(field.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // Why the privacy actions would take it out.
                when (field.sensitivity) {
                    Sensitivity.LOCATION -> Icon(Icons.Default.LocationOn, stringResource(R.string.meta_is_location), tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                    Sensitivity.PERSONAL -> Icon(Icons.Default.Person, stringResource(R.string.meta_is_personal), tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 4.dp).size(14.dp))
                    Sensitivity.NONE -> Unit
                }
                if (field.source.isNotEmpty()) {
                    Text(" · ${field.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
            }
            Text(
                when {
                    changed && value.isEmpty() -> stringResource(R.string.meta_will_be_removed)
                    value.isEmpty() -> stringResource(R.string.meta_empty)
                    else -> value
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (changed) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    changed -> MaterialTheme.colorScheme.primary
                    value.isEmpty() -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            field.editable -> Icon(Icons.Default.Edit, editLabel, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp).size(18.dp))
            showLocks -> Icon(Icons.Default.Lock, readOnly, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 8.dp).size(16.dp).semantics { contentDescription = readOnly })
        }
    }
}
