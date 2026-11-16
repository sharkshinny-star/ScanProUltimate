package com.example.scanpro.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.TextInputDialog
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ReviewMode
import com.example.scanpro.data.ScanMode

/** Scanning: how the camera starts, how scans are saved, and (under Advanced) the name pattern. */
@Composable
internal fun ScanningPage(
    defaultScanMode: String,
    onDefaultScanModeChange: (String) -> Unit,
    defaultFilter: PageFilter?,
    onDefaultFilterChange: (PageFilter?) -> Unit,
    reviewMode: ReviewMode,
    onReviewModeChange: (ReviewMode) -> Unit,
    pdfQuality: PdfQuality,
    onPdfQualityChange: (PdfQuality) -> Unit,
    autoTitle: Boolean,
    onAutoTitleChange: (Boolean) -> Unit,
    namePattern: String,
    onNamePatternChange: (String) -> Unit,
    defaultOcrScript: OcrScript,
    onDefaultOcrScriptChange: (OcrScript) -> Unit
) = SettingsPageColumn {
    SettingsGroup(header = stringResource(R.string.settings_group_camera)) {
        // Camera start mode: every mode in the camera's strip, QR code last.
        val qrLabel = stringResource(R.string.scan_qr_code)
        SettingsMenuRow(
            title = stringResource(R.string.settings_default_mode_title),
            icon = Icons.Rounded.CameraAlt,
            options = ScanMode.entries.map { it.name } + "QR",
            selected = defaultScanMode.takeIf { it == "QR" || ScanMode.entries.any { m -> m.name == it } } ?: ScanMode.DOCUMENT.name,
            onSelect = onDefaultScanModeChange
        ) { name -> if (name == "QR") qrLabel else stringResource(ScanMode.valueOf(name).labelRes) }
        // Starting filter: "Automatic" keeps each mode's own (Auto Clean, Whiteboard, Original for ID cards).
        val automatic = stringResource(R.string.settings_default_filter_automatic)
        SettingsMenuRow(
            title = stringResource(R.string.settings_default_filter_title),
            icon = Icons.Rounded.AutoFixHigh,
            options = listOf<PageFilter?>(null) + PageFilter.entries,
            selected = defaultFilter,
            onSelect = onDefaultFilterChange
        ) { filter -> filter?.let { stringResource(it.labelRes) } ?: automatic }
        // Fast save (R26): Done in the camera saves straight away unless a page needs a look.
        SettingsChoiceDialogRow(
            title = stringResource(R.string.settings_review_mode_title),
            icon = Icons.Rounded.Checklist,
            options = ReviewMode.entries,
            selected = reviewMode,
            onSelect = onReviewModeChange,
            explanation = stringResource(R.string.settings_review_mode_subtitle)
        ) { stringResource(it.labelRes) }
    }

    SettingsGroup(header = stringResource(R.string.settings_group_saving)) {
        SettingsChoiceDialogRow(
            title = stringResource(R.string.settings_pdf_quality_title),
            icon = Icons.Rounded.PictureAsPdf,
            options = PdfQuality.entries,
            selected = pdfQuality,
            onSelect = onPdfQualityChange,
            explanation = stringResource(R.string.settings_pdf_quality_body)
        ) { stringResource(it.labelRes) }
        SettingsSwitchRow(
            title = stringResource(R.string.settings_smart_titles_title),
            icon = Icons.Rounded.Title,
            summary = stringResource(R.string.settings_smart_titles_body),
            checked = autoTitle,
            onCheckedChange = onAutoTitleChange
        )
        SettingsMenuRow(
            title = stringResource(R.string.settings_ocr_language_title),
            icon = Icons.Rounded.Translate,
            options = OcrScript.entries,
            selected = defaultOcrScript,
            onSelect = onDefaultOcrScriptChange,
            summary = stringResource(R.string.settings_ocr_language_subtitle, stringResource(defaultOcrScript.labelRes))
        ) { stringResource(it.labelRes) }
    }

    SettingsGroup(header = stringResource(R.string.settings_group_advanced)) {
        // Name pattern (R34): tokens {text} {mode} {date} {time}; blank keeps the automatic name.
        var patternOpen by rememberSaveable { mutableStateOf(false) }
        SettingsRow(
            title = stringResource(R.string.settings_name_pattern_title),
            icon = Icons.Rounded.DriveFileRenameOutline,
            summary = namePattern.ifBlank { stringResource(R.string.settings_default_filter_automatic) },
            summaryMaxLines = 1,
            onClick = { patternOpen = true }
        )
        if (patternOpen) {
            // The explanation that used to be a three-line hint under the row now sits above the field.
            TextInputDialog(
                title = stringResource(R.string.settings_name_pattern_title),
                initial = namePattern,
                label = stringResource(R.string.settings_name_pattern_label),
                allowBlank = true,
                message = stringResource(R.string.settings_name_pattern_body),
                onDismiss = { patternOpen = false }
            ) { onNamePatternChange(it); patternOpen = false }
        }
    }
}
