package com.example.scanpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.BrandLockup
import com.example.scanpro.core.text.AppLocale

/**
 * First launch: pick the app's language before anything else. The phone's language is preselected when the app
 * has it. Every option is written in its own language, so it can be read whatever the current one is.
 */
@Composable
fun LanguagePickerScreen(initial: String, onChosen: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            BrandLockup(markSize = 36.dp)
            Spacer(Modifier.height(28.dp))
            // Bilingual on purpose: the user may not read the current language yet.
            Text(
                stringResource(R.string.language_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (stringResource(R.string.language_picker_title) != "Choose your language") {
                Text("Choose your language", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            LanguageOptions(
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.weight(1f, fill = false).widthIn(max = 480.dp).verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onChosen(selected) }, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).height(52.dp)) {
                Text(stringResource(R.string.common_continue), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The same choice from Settings > App language. */
@Composable
fun LanguagePickerDialog(current: String, onDismiss: () -> Unit, onChosen: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_app_language)) },
        text = { LanguageOptions(selected, { selected = it }, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { onChosen(selected) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun LanguageOptions(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppLocale.SUPPORTED.forEach { (tag, name) ->
            val on = tag == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.shapes.medium
                    )
                    .border(
                        if (on) 2.dp else 0.dp,
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.shapes.medium
                    )
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(tag) })
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (on) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
