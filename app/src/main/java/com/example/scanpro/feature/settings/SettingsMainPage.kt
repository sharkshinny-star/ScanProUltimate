package com.example.scanpro.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.BuildConfig
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.BrandMark
import com.example.scanpro.core.designsystem.TileTone
import com.example.scanpro.core.ui.formatBytes
import com.example.scanpro.core.ui.sharePlayStoreListing
import com.example.scanpro.data.OversizedPdfs

/**
 * Settings pages (F140). The main page lists them in [group]s, each with a one-line summary: the current state where
 * there is one worth seeing (backup, recycle bin, notifications), otherwise the names of the settings inside.
 */
internal enum class SettingsPage(@StringRes val title: Int, val icon: ImageVector, val tone: TileTone, val group: Int, val summary: List<Int>) {
    SCANNING(R.string.settings_section_scanning, Icons.Rounded.CameraAlt, TileTone.Indigo, 0,
        listOf(R.string.settings_default_mode_title, R.string.settings_pdf_quality_title, R.string.settings_ocr_language_title)),
    APPEARANCE(R.string.settings_section_appearance, Icons.Rounded.Palette, TileTone.Violet, 0,
        listOf(R.string.settings_theme_title, R.string.settings_palette_title, R.string.settings_app_language)),
    NOTIFICATIONS(R.string.settings_section_notifications, Icons.Rounded.Notifications, TileTone.Amber, 0,
        listOf(R.string.settings_notify_finished_title, R.string.settings_notify_backup_title)),
    BACKUP(R.string.settings_section_backup, Icons.Rounded.Backup, TileTone.Teal, 1,
        listOf(R.string.settings_backup_all, R.string.settings_restore_backup)),
    RECYCLE_BIN(R.string.settings_section_recycle_bin, Icons.Rounded.Delete, TileTone.Slate, 1,
        listOf(R.string.settings_trash_retention_title)),
    PRIVACY(R.string.settings_section_privacy, Icons.Rounded.Fingerprint, TileTone.Rose, 1,
        listOf(R.string.settings_app_lock_title, R.string.settings_privacy_title)),
    SHORTCUTS(R.string.settings_section_shortcuts, Icons.Rounded.Widgets, TileTone.Sky, MORE_GROUP,
        listOf(R.string.settings_shortcuts_summary)),
    SUPPORT(R.string.settings_section_support, Icons.Rounded.Feedback, TileTone.Coral, MORE_GROUP,
        listOf(R.string.settings_whats_new_title, R.string.settings_report_issue_title, R.string.settings_rate_title)),
    ABOUT(R.string.settings_section_about, Icons.Rounded.Info, TileTone.Slate, MORE_GROUP,
        listOf(R.string.settings_about_open_source))
}

/** The main page's last group: shortcuts, support and About, then Share ScanPro straight from the list. */
private const val MORE_GROUP = 2

/** The name over each group on the main page (2026-11-19): three named groups read faster than four unnamed ones. */
private val GROUP_HEADERS = listOf(R.string.settings_group_general, R.string.settings_group_data, R.string.settings_group_more)

/** What the main page shows besides the list of pages. */
internal class SettingsOverview(
    val documentCount: Int,
    val trashCount: Int,
    val trashRetentionDays: Int,
    val storageBytes: Long,
    val lastBackupAt: Long,
    val canPostNotifications: Boolean,
    /** PDFs from older versions that are far larger than needed; null when there are none. */
    val reclaimable: OversizedPdfs?,
    val busy: Boolean,
    val freeingUp: Boolean,
    /** What the Scanning page is set to, read back on its row. */
    val defaultScanMode: String = com.example.scanpro.data.ScanMode.DOCUMENT.name,
    val pdfQuality: com.example.scanpro.data.PdfQuality = com.example.scanpro.data.PdfQuality.BALANCED,
    val defaultOcrScript: com.example.scanpro.data.OcrScript = com.example.scanpro.data.OcrScript.LATIN
)

/** The calm first page: the library on one row, then the pages in groups (the last, More, ends with Share ScanPro), then the version. */
@Composable
internal fun SettingsMainPage(
    overview: SettingsOverview,
    onOpenPage: (SettingsPage) -> Unit,
    onOpenFiles: () -> Unit,
    onFreeUpSpace: () -> Unit,
    onDismissFreeSpace: () -> Unit
) = SettingsPageColumn {
    LibraryCard(overview, onOpenFiles, onFreeUpSpace, onDismissFreeSpace)
    val context = LocalContext.current
    SettingsPage.entries.groupBy { it.group }.forEach { (group, pages) ->
        SettingsGroup(header = GROUP_HEADERS.getOrNull(group)?.let { stringResource(it) }) {
            pages.forEach { page ->
                val state = pageSummary(page, overview)
                SettingsRow(
                    title = stringResource(page.title),
                    icon = page.icon,
                    tone = page.tone,
                    summary = state ?: page.summary.map { stringResource(it) }.joinToString(" · "),
                    summaryMaxLines = if (state != null) 2 else 1,
                    trailing = { Chevron() },
                    onClick = { onOpenPage(page) }
                )
            }
            if (group == MORE_GROUP) {
                SettingsRow(
                    title = stringResource(R.string.settings_share_with_friends),
                    icon = Icons.Rounded.Share,
                    tone = TileTone.Coral,
                    summary = stringResource(R.string.settings_share_subtitle),
                    summaryMaxLines = 1,
                    onClick = { sharePlayStoreListing(context) }
                )
            }
        }
    }
    AboutFooter()
}

/** A page's current state, when it has one worth reading from the main page; null to list its settings instead. */
@Composable
private fun pageSummary(page: SettingsPage, overview: SettingsOverview): String? = when (page) {
    SettingsPage.BACKUP -> backupStatusText(overview.lastBackupAt)
    SettingsPage.RECYCLE_BIN -> stringResource(
        R.string.settings_recycle_bin_summary,
        recycleBinCountText(overview.trashCount),
        pluralStringResource(R.plurals.settings_trash_retention_days, overview.trashRetentionDays, overview.trashRetentionDays)
    )
    SettingsPage.NOTIFICATIONS -> if (overview.canPostNotifications) null else stringResource(R.string.settings_notifications_off_title)
    SettingsPage.ABOUT -> stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)
    SettingsPage.SCANNING -> scanningSummary(overview.defaultScanMode, overview.pdfQuality, overview.defaultOcrScript)
    else -> null
}

/** "Document · Balanced · Latin": what the Scanning page is set to, which reads better than three cut-off setting names. */
@Composable
private fun scanningSummary(scanMode: String, quality: com.example.scanpro.data.PdfQuality, script: com.example.scanpro.data.OcrScript): String {
    val mode = com.example.scanpro.data.ScanMode.entries.firstOrNull { it.name == scanMode }?.let { stringResource(it.labelRes) }
        ?: if (scanMode == "QR") stringResource(R.string.scan_qr_code) else stringResource(com.example.scanpro.data.ScanMode.DOCUMENT.labelRes)
    return listOf(mode, stringResource(quality.labelRes), stringResource(script.labelRes)).joinToString(" · ")
}

/** The library on one row (it opens Files), with the "free up space" offer under it when there is one. */
@Composable
private fun LibraryCard(overview: SettingsOverview, onOpenFiles: () -> Unit, onFreeUpSpace: () -> Unit, onDismissFreeSpace: () -> Unit) {
    val context = LocalContext.current
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_library_title),
            icon = Icons.Rounded.Storage,
            tone = TileTone.Indigo,
            trailing = { Chevron() },
            onClick = onOpenFiles
        )
        // The library in three figures (2026-11-19); it was one line of small text under the title.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryStat(overview.documentCount.toString(), stringResource(R.string.settings_stat_documents), Modifier.weight(1f))
            LibraryStat(formatBytes(context, overview.storageBytes), stringResource(R.string.settings_stat_storage), Modifier.weight(1f))
            LibraryStat(overview.trashCount.toString(), stringResource(R.string.settings_stat_recycle_bin), Modifier.weight(1f))
        }
        val reclaimable = overview.reclaimable
        // Scans from older versions were saved as lossless PDFs, many times larger than they need to be.
        if (reclaimable != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            ) {
                Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 14.dp)) {
                    // A close button (48 dp touch target): not everyone wants this, and it should not nag.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.settings_free_space_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismissFreeSpace, enabled = !overview.freeingUp) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close))
                        }
                    }
                    Text(
                        stringResource(R.string.settings_free_space_subtitle, formatBytes(context, reclaimable.estimatedBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onFreeUpSpace, enabled = !overview.busy, modifier = Modifier.align(Alignment.End).padding(end = 10.dp)) {
                        if (overview.freeingUp) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_free_space_running))
                        } else {
                            Text(stringResource(R.string.settings_free_space_action))
                        }
                    }
                }
            }
        }
    }
}

/** One figure about the library and what it counts. The pair is read out as one phrase. */
@Composable
private fun LibraryStat(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** Brand sign-off at the end of the main page. */
@Composable
private fun AboutFooter() {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BrandMark(size = 32.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.core_recommend_app_subject) + " · " + stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.settings_footer_privacy),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
