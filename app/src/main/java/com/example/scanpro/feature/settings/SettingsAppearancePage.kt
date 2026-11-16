package com.example.scanpro.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.PalettePicker
import com.example.scanpro.core.text.AppLocale
import com.example.scanpro.core.ui.findActivity
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.ui.LanguagePickerDialog

/** Appearance: theme, colour theme, wallpaper colours, app language and the tips on Home. */
@Composable
internal fun AppearancePage(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentPalette: AccentPalette,
    onAccentPaletteChange: (AccentPalette) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    homeTips: Boolean,
    onHomeTipsChange: (Boolean) -> Unit
) = SettingsPageColumn {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val wallpaperColoursOn = dynamicColor && supportsDynamicColor
    SettingsGroup {
        SettingsRow(title = stringResource(R.string.settings_theme_title), icon = Icons.Rounded.Palette)
        SettingsSegmentedChoice(options = ThemeMode.entries, selected = themeMode, onSelect = onThemeModeChange) { stringResource(it.labelRes) }
        SettingsRow(
            title = stringResource(R.string.settings_palette_title),
            icon = Icons.Rounded.ColorLens,
            // Only worth a line when the choice below has no effect.
            summary = if (wallpaperColoursOn) stringResource(R.string.settings_palette_subtitle_wallpaper) else null
        )
        PalettePicker(
            // With wallpaper colours on, no theme applies, so none is marked.
            selected = accentPalette.takeUnless { wallpaperColoursOn },
            onSelect = onAccentPaletteChange,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        )
        if (supportsDynamicColor) {
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                icon = Icons.Rounded.Wallpaper,
                summary = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = dynamicColor,
                onCheckedChange = onDynamicColorChange
            )
        }
    }

    SettingsGroup {
        // Read once per visit, not on every recomposition: on Android 12 and older it is a preferences read.
        val currentTag = remember(context) { AppLocale.current(context) ?: AppLocale.suggested() }
        var languageDialogOpen by rememberSaveable { mutableStateOf(false) }
        SettingsRow(
            title = stringResource(R.string.settings_app_language),
            icon = Icons.Rounded.Language,
            summary = AppLocale.SUPPORTED.firstOrNull { it.first == currentTag }?.second
                ?: stringResource(R.string.settings_app_language_subtitle),
            onClick = { languageDialogOpen = true }
        )
        if (languageDialogOpen) {
            LanguagePickerDialog(
                current = currentTag,
                onDismiss = { languageDialogOpen = false },
                onChosen = { tag ->
                    languageDialogOpen = false
                    context.findActivity()?.let { AppLocale.apply(it, tag) }
                }
            )
        }
        SettingsSwitchRow(
            title = stringResource(R.string.settings_home_tips_title),
            icon = Icons.Rounded.TipsAndUpdates,
            summary = stringResource(R.string.settings_home_tips_subtitle),
            checked = homeTips,
            onCheckedChange = onHomeTipsChange
        )
    }
}
