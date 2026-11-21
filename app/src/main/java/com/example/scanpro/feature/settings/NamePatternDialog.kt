package com.example.scanpro.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.ScanMode
import com.example.scanpro.imaging.ScanTitle

/** The tokens offered as chips, with the plain words they are shown as. */
private val TOKEN_CHIPS: List<Pair<String, Int>> = listOf(
    "{text}" to R.string.name_pattern_tag_text,
    "{mode}" to R.string.name_pattern_tag_mode,
    "{date}" to R.string.name_pattern_tag_date,
    "{time}" to R.string.name_pattern_tag_time
)

/** Names a pattern would give, worked out from a sample page so Settings can show results rather than braces. */
internal class NamePatternSample(private val text: String, private val mode: String, private val created: Long) {
    fun name(pattern: String): String =
        ScanTitle.build(pattern, text, mode, created, fallback = ScanTitle.build("{mode} {date}", null, mode, created, mode))
}

@Composable
internal fun rememberNamePatternSample(): NamePatternSample {
    val text = stringResource(R.string.name_pattern_sample_text)
    val mode = stringResource(ScanMode.DOCUMENT.labelRes)
    return remember(text, mode) { NamePatternSample(text, mode, System.currentTimeMillis()) }
}

/**
 * The name pattern editor (R34). Tokens are added with a tap instead of typed in braces, a few suggestions are
 * offered as the names they give, and the line under the field shows what the next scan would be called. Empty
 * means automatic. The keyboard is not opened by itself, so the chips stay in view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NamePatternDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by rememberSaveable(initial, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    val sample = rememberNamePatternSample()
    val example = sample.name(value.text)
    val unknown = remember(value.text) { ScanTitle.unknownTokens(value.text) }
    fun replaceWith(pattern: String) { value = TextFieldValue(pattern, TextRange(pattern.length)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_name_pattern_title)) },
        text = {
            // Scrolls, so everything stays reachable with a big font, in landscape or above the keyboard.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_name_pattern_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.settings_name_pattern_label)) },
                    placeholder = { Text(stringResource(R.string.settings_default_filter_automatic)) },
                    supportingText = {
                        Text(
                            if (unknown.isEmpty()) stringResource(R.string.name_pattern_example, example)
                            else stringResource(R.string.name_pattern_unknown, unknown.joinToString(", ")),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    isError = unknown.isNotEmpty(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(value.text) }),
                    modifier = Modifier.fillMaxWidth()
                )
                ChipGroup(R.string.name_pattern_add_tag) {
                    TOKEN_CHIPS.forEach { (token, label) ->
                        AssistChip(
                            onClick = {
                                val (text, cursor) = ScanTitle.insertToken(value.text, value.selection.min, value.selection.max, token)
                                value = TextFieldValue(text, TextRange(cursor))
                            },
                            label = { Text(stringResource(label)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
                ChipGroup(R.string.name_pattern_suggestions) {
                    FilterChip(
                        selected = value.text.isBlank(),
                        onClick = { replaceWith("") },
                        label = { Text(stringResource(R.string.settings_default_filter_automatic)) }
                    )
                    ScanTitle.PRESETS.forEach { preset ->
                        FilterChip(
                            selected = value.text.trim() == preset,
                            onClick = { replaceWith(preset) },
                            label = { Text(sample.name(preset), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.text) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(@StringRes heading: Int, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(heading), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}
