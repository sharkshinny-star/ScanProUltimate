package com.example.scanpro.feature.settings

import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.InsetDivider
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Where each setting sits in the scrolling content, so a search result can scroll to the real row and flash it.
 * Rows report their position by title; the scrolling column reports where its viewport starts.
 */
@Stable
internal class SettingsLocator(val scroll: ScrollState) {
    val tops = mutableStateMapOf<String, Float>()
    var viewportTop by mutableFloatStateOf(0f)
    var highlighted by mutableStateOf<String?>(null)

    /** Scrolls the row with this [title] to near the top and flashes it for a moment. */
    suspend fun jumpTo(title: String) {
        // The list is being rebuilt after the search closes: let it lay out before reading where the row is.
        repeat(3) { withFrameNanos { } }
        val y = tops[title] ?: return
        scroll.animateScrollTo((y - 96f).roundToInt().coerceAtLeast(0))
        highlighted = title
        delay(1600)
        if (highlighted == title) highlighted = null
    }
}

internal val LocalSettingsLocator = staticCompositionLocalOf<SettingsLocator?> { null }

private class SettingEntry(@StringRes val title: Int, @StringRes val subtitle: Int?, @StringRes val section: Int)

/** Every setting people can look for: its name, the line under it, and the section it lives in. */
private val SETTING_ENTRIES = listOf(
    SettingEntry(R.string.settings_backup_all, R.string.settings_backup_all_subtitle, R.string.settings_section_backup),
    SettingEntry(R.string.settings_restore_backup, R.string.settings_restore_backup_subtitle, R.string.settings_section_backup),
    SettingEntry(R.string.settings_recover_library, R.string.settings_recover_library_subtitle, R.string.settings_section_backup),
    SettingEntry(R.string.settings_theme_title, R.string.settings_theme_subtitle, R.string.settings_section_appearance),
    SettingEntry(R.string.settings_palette_title, R.string.settings_palette_subtitle, R.string.settings_section_appearance),
    SettingEntry(R.string.settings_dynamic_color_title, R.string.settings_dynamic_color_subtitle, R.string.settings_section_appearance),
    SettingEntry(R.string.settings_app_language, R.string.settings_app_language_subtitle, R.string.settings_section_appearance),
    SettingEntry(R.string.settings_home_tips_title, R.string.settings_home_tips_subtitle, R.string.settings_section_appearance),
    SettingEntry(R.string.settings_default_mode_title, R.string.settings_default_mode_subtitle, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_default_filter_title, R.string.settings_default_filter_subtitle, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_review_mode_title, R.string.settings_review_mode_subtitle, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_pdf_quality_title, R.string.settings_pdf_quality_body, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_smart_titles_title, R.string.settings_smart_titles_body, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_name_pattern_title, R.string.settings_name_pattern_body, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_ocr_language_title, null, R.string.settings_section_scanning),
    SettingEntry(R.string.settings_trash_retention_title, R.string.settings_trash_retention_subtitle, R.string.settings_section_recycle_bin),
    SettingEntry(R.string.settings_app_lock_title, R.string.settings_app_lock_body, R.string.settings_section_privacy),
    SettingEntry(R.string.settings_privacy_title, R.string.settings_privacy_subtitle, R.string.settings_section_privacy),
    SettingEntry(R.string.settings_privacy_policy, null, R.string.settings_section_privacy),
    SettingEntry(R.string.settings_terms_of_use, null, R.string.settings_section_privacy),
    SettingEntry(R.string.settings_notify_finished_title, R.string.settings_notify_finished_body, R.string.settings_section_notifications),
    SettingEntry(R.string.settings_notify_failures_title, R.string.settings_notify_failures_body, R.string.settings_section_notifications),
    SettingEntry(R.string.settings_notify_trash_title, R.string.settings_notify_trash_body, R.string.settings_section_notifications),
    SettingEntry(R.string.settings_notify_backup_title, R.string.settings_notify_backup_body, R.string.settings_section_notifications),
    SettingEntry(R.string.settings_whats_new_title, R.string.settings_whats_new_subtitle, R.string.settings_section_support),
    SettingEntry(R.string.settings_rate_title, R.string.settings_rate_subtitle, R.string.settings_section_support),
    SettingEntry(R.string.settings_feedback, R.string.settings_feedback_subtitle, R.string.settings_section_support),
    SettingEntry(R.string.settings_report_issue_title, R.string.settings_report_issue_subtitle, R.string.settings_section_support),
    SettingEntry(R.string.settings_request_features, null, R.string.settings_section_support),
    SettingEntry(R.string.settings_share_with_friends, R.string.settings_share_subtitle, R.string.settings_section_support)
)

internal class SettingHit(val title: String, val section: String, val subtitle: String?)

/**
 * How well [words] (already lower-cased) match one setting: 0 when every word is in its name, 1 when each is in the
 * name, the description or the section, null when some word is nowhere. Pure, so it can be tested without a screen.
 */
internal fun settingMatchRank(words: List<String>, title: String, subtitle: String?, section: String): Int? {
    if (words.isEmpty()) return null
    val name = title.lowercase()
    val rest = (subtitle.orEmpty() + " " + section).lowercase()
    return when {
        words.all { it in name } -> 0
        words.all { it in name || it in rest } -> 1
        else -> null
    }
}

/** Splits what was typed into lower-case words. */
internal fun searchWords(query: String): List<String> = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

/** Settings whose name, description or section contains every word typed; name matches first. */
@Composable
internal fun rememberSettingHits(query: String): List<SettingHit> {
    val resources = LocalResources.current
    return remember(query, resources) {
        val words = searchWords(query)
        if (words.isEmpty()) return@remember emptyList()
        SETTING_ENTRIES.mapNotNull { entry ->
            val title = resources.getString(entry.title)
            val subtitle = entry.subtitle?.let { resources.getString(it) }
            val section = resources.getString(entry.section)
            settingMatchRank(words, title, subtitle, section)?.let { it to SettingHit(title, section, subtitle) }
        }.sortedBy { it.first }.map { it.second }
    }
}

/** The search box that heads Settings. */
@Composable
internal fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.settings_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

/** Matching settings as a list: tapping one closes the search and takes you to the real setting. */
@Composable
internal fun SettingsSearchResults(query: String, hits: List<SettingHit>, onPick: (SettingHit) -> Unit) {
    if (hits.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            Spacer(Modifier.heightIn(min = 12.dp))
            Text(stringResource(R.string.settings_search_no_match, query.trim()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.heightIn(min = 4.dp))
            Text(
                stringResource(R.string.settings_search_no_match_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column {
            hits.forEachIndexed { index, hit ->
                if (index > 0) InsetDivider(startIndent = 16.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { onPick(hit) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(hit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(hit.section, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One line at the top of Backup: when the library was last backed up, or a nudge if it never was. */
@Composable
internal fun BackupStatus(lastBackupAt: Long, hasDocuments: Boolean) {
    val context = LocalContext.current
    val done = lastBackupAt > 0L
    val text = if (done) {
        stringResource(R.string.settings_backup_last, DateUtils.formatDateTime(context, lastBackupAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH))
    } else {
        stringResource(R.string.settings_backup_never)
    }
    // A nudge only makes sense when there is something to lose.
    val warn = !done && hasDocuments
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (warn) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
    InsetDivider()
}

/** What a backup is, before the folder picker: the picker alone says nothing about encryption, the password or where to save. */
@Composable
internal fun BackupIntroDialog(onChoose: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_backup_intro_title)) },
        text = { Text(stringResource(R.string.settings_backup_intro_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onChoose) { Text(stringResource(R.string.settings_backup_choose_folder)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** The Documents folder on the phone's own storage: the picker starts here, because it refuses the storage root ("Can't use this folder"). */
internal fun documentsFolderUri(): Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
