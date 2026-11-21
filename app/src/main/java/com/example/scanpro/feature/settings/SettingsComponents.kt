package com.example.scanpro.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.TileTone

/*
 * Settings row anatomy, the same on every page: an icon, the title, one line under it that says the current value (or
 * what the row does), and at most one trailing control. Rows sit on tonal cards, one card per group, with no dividers;
 * a group may have a small heading above its card. Long explanations live in the dialog a row opens, not under it.
 */

/** One group of rows on a tonal card, with an optional heading above it (a heading for TalkBack). */
@Composable
internal fun SettingsGroup(header: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { heading() }
            )
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/**
 * The one row every setting is built from. [tone] draws the icon on a coloured tile (main page only); without it the
 * icon is plain, as on the pages. A row registers where it sits so a search result can scroll to it and flash it.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    tone: TileTone? = null,
    enabled: Boolean = true,
    summaryMaxLines: Int = 2,
    role: Role = Role.Button,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val locator = LocalSettingsLocator.current
    // Only the row whose title is lit recomposes when the highlight moves, not every row on the page.
    val lit by remember(locator, title) { derivedStateOf { locator?.highlighted == title } }
    val flash by animateColorAsState(
        if (lit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        label = "settingFlash"
    )
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (locator != null) {
                    // Where this row sits in the scrolling content, so a search result can scroll to it.
                    Modifier.onGloballyPositioned { coords -> locator.tops[title] = coords.positionInRoot().y - locator.viewportTop + locator.scroll.value }
                } else Modifier
            )
            .background(flash)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            if (tone != null) {
                IconTile(icon, tone, size = 40.dp, corner = 12.dp, iconSize = 20.dp)
                Spacer(Modifier.width(16.dp))
            } else {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha))
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A row with a switch. The whole row is one control for a screen reader, title, summary and state together, so the
 * switch itself takes no clicks of its own.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    icon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null
) {
    Box(Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        SettingsRow(title = title, icon = icon, summary = summary, trailing = { Switch(checked = checked, onCheckedChange = null) })
    }
}

/** A row whose summary is its current value; a tap opens a short menu of the choices, the current one ticked. */
@Composable
internal fun <T> SettingsMenuRow(
    title: String,
    icon: ImageVector?,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    /** Overrides the summary when the value alone needs a word of context; it should still name the value. */
    summary: String? = null,
    label: @Composable (T) -> String
) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingsRow(title = title, icon = icon, summary = summary ?: label(selected), role = Role.DropdownList, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, offset = DpOffset(60.dp, 0.dp)) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else null,
                    onClick = { open = false; onSelect(option) }
                )
            }
        }
    }
}

/**
 * A row whose summary is its current value; a tap opens a dialog with the explanation (what used to be a paragraph
 * under the row) and the choices as radio buttons. Picking one applies it and closes the dialog.
 */
@Composable
internal fun <T> SettingsChoiceDialogRow(
    title: String,
    icon: ImageVector?,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    explanation: String? = null,
    label: @Composable (T) -> String
) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsRow(title = title, icon = icon, summary = label(selected), onClick = { open = true })
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (explanation != null) {
                        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                    }
                    Column(Modifier.selectableGroup()) {
                        options.forEach { option ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { open = false; onSelect(option) }),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = option == selected, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

/**
 * A short pick-one list right under its row. Side by side as segments normally; as a vertical list of radio rows when
 * the text is large, because three segments a third of a phone wide break words in the middle ("Alway/s", R43).
 */
@Composable
internal fun <T> SettingsSegmentedChoice(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    if (LocalDensity.current.fontScale > 1.15f) {
        Column(Modifier.fillMaxWidth().selectableGroup().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    // Every segment as tall as the tallest, so a label that wraps ("System default" on a narrow phone)
                    // doesn't leave the outline stepped.
                    modifier = Modifier.fillMaxHeight(),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text(label(option)) }
            }
        }
    }
}

/** The page's groups, evenly spaced. */
@Composable
internal fun SettingsPageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
}

@Composable
internal fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
