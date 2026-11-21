package com.example.scanpro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.scanpro.MainActivity
import com.example.scanpro.R
import com.example.scanpro.data.ScanMode

/**
 * The home-screen widget (R33, resizable since F197). Its layout follows its size: two cells give one large Scan
 * button; a row of three or more gives Scan, ID card, Import and Tools; two rows or more add the app's name, a wide
 * Scan button and Files. Every button is an intent MainActivity already handles. Plain RemoteViews, no new dependency;
 * on Android 12 and newer the colours follow the wallpaper (values-v31/widget_colors.xml).
 */
class ScanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views(context, appWidgetManager.getAppWidgetOptions(id))) }
    }

    /** Resized on the home screen: below Android 12 the layout is picked here from the new size. */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        appWidgetManager.updateAppWidget(appWidgetId, views(context, newOptions))
    }

    private fun views(context: Context, options: Bundle?): RemoteViews {
        val small = bind(context, R.layout.widget_scan_small)
        val medium = bind(context, R.layout.widget_scan)
        val large = bind(context, R.layout.widget_scan_large)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher picks the largest layout that fits the widget's current size, and switches as it is resized.
            return RemoteViews(mapOf(SizeF(100f, 40f) to small, SizeF(200f, 40f) to medium, SizeF(200f, 130f) to large))
        }
        // Older launchers report the size range: portrait uses the minimum width and the maximum height.
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        return when {
            width in 1 until 200 -> small
            height >= 130 -> large
            else -> medium
        }
    }

    /** [layout] with each button it has wired to its intent; ids a layout lacks are skipped. */
    private fun bind(context: Context, layout: Int): RemoteViews {
        fun pending(code: Int, action: String, mode: String? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (mode != null) intent.putExtra(MainActivity.EXTRA_SCAN_MODE, mode)
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(R.id.widget_scan, pending(1, MainActivity.ACTION_SCAN_NOW))
            if (layout != R.layout.widget_scan_small) {
                setOnClickPendingIntent(R.id.widget_id_card, pending(2, MainActivity.ACTION_SCAN_NOW, ScanMode.ID_CARD.name))
                setOnClickPendingIntent(R.id.widget_import, pending(3, MainActivity.ACTION_IMPORT_IMAGES))
                setOnClickPendingIntent(R.id.widget_tools, pending(4, MainActivity.ACTION_OPEN_TOOLS))
            }
            if (layout == R.layout.widget_scan_large) {
                setOnClickPendingIntent(R.id.widget_files, pending(5, MainActivity.ACTION_OPEN_FILES))
            }
        }
    }
}
