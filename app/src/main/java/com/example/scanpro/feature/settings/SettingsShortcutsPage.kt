package com.example.scanpro.feature.settings

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutManagerCompat
import com.example.scanpro.MainActivity
import com.example.scanpro.R
import com.example.scanpro.widget.ScanWidgetProvider

/**
 * Ways into the app from outside it, all user-initiated: making ScanPro the PDF app (explained, then Android's own
 * settings page), and pinning the widget or the Scan shortcut. A pin row is hidden when the launcher can't pin.
 */
@Composable
internal fun ShortcutsPage() = SettingsPageColumn {
    val context = LocalContext.current
    var explainDefault by rememberSaveable { mutableStateOf(false) }
    val notAccepted = stringResource(R.string.settings_pin_not_accepted)
    val settingsUnavailable = stringResource(R.string.settings_android_settings_unavailable)
    if (explainDefault) {
        AlertDialog(
            onDismissRequest = { explainDefault = false },
            title = { Text(stringResource(R.string.settings_open_by_default_title)) },
            text = { Text(stringResource(R.string.settings_open_by_default_body)) },
            confirmButton = {
                TextButton(onClick = {
                    explainDefault = false
                    if (!HomeScreenPins.openDefaultAppSettings(context)) Toast.makeText(context, settingsUnavailable, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.settings_open_android_settings)) }
            },
            dismissButton = { TextButton(onClick = { explainDefault = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.settings_open_by_default_title),
            icon = Icons.Rounded.PictureAsPdf,
            summary = stringResource(R.string.settings_open_by_default_subtitle),
            onClick = { explainDefault = true }
        )
    }

    // Asked once per visit: both are binder calls to the launcher.
    val canPinWidget = remember(context) { HomeScreenPins.canPinWidget(context) }
    val canPinShortcut = remember(context) { HomeScreenPins.canPinShortcut(context) }
    if (canPinWidget || canPinShortcut) {
        SettingsGroup(header = stringResource(R.string.settings_group_home_screen)) {
            if (canPinWidget) {
                SettingsRow(
                    title = stringResource(R.string.settings_widget_title),
                    icon = Icons.Rounded.Widgets,
                    summary = stringResource(R.string.settings_widget_subtitle),
                    onClick = { if (!HomeScreenPins.pinWidget(context)) Toast.makeText(context, notAccepted, Toast.LENGTH_SHORT).show() }
                )
            }
            if (canPinShortcut) {
                SettingsRow(
                    title = stringResource(R.string.settings_scan_shortcut_title),
                    icon = Icons.AutoMirrored.Rounded.AddToHomeScreen,
                    summary = stringResource(R.string.settings_scan_shortcut_subtitle),
                    onClick = { if (!HomeScreenPins.pinScanShortcut(context)) Toast.makeText(context, notAccepted, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

/** The system requests behind the Shortcuts page. Each one only asks: the launcher or Android shows its own confirmation. */
internal object HomeScreenPins {
    fun canPinWidget(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            runCatching { AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)

    /** Asks the launcher to place the Scan widget; false when it refused or can't. */
    fun pinWidget(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && runCatching {
            AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, ScanWidgetProvider::class.java), null, null)
        }.getOrDefault(false)

    fun canPinShortcut(context: Context): Boolean =
        runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }.getOrDefault(false)

    /** Asks the launcher to pin the "Scan now" shortcut; false when it refused or can't. */
    fun pinScanShortcut(context: Context): Boolean =
        runCatching { ShortcutManagerCompat.requestPinShortcut(context, MainActivity.scanNowShortcut(context), null) }.getOrDefault(false)

    /**
     * ScanPro's "Open by default" page in Android settings (Android 12 and later), else its app info page, where older
     * versions keep the same switch. False when neither opens.
     */
    fun openDefaultAppSettings(context: Context): Boolean {
        val pkg = Uri.fromParts("package", context.packageName, null)
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
        return intents.any { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
