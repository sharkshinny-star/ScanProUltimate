package com.example.scanpro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.scanpro.MainActivity
import com.example.scanpro.R
import com.example.scanpro.data.ScanMode

/**
 * The home-screen widget (R33): three buttons, Scan, ID card and Import, each an intent MainActivity already
 * handles for its launcher shortcuts. Plain RemoteViews, no new dependency.
 */
class ScanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views(context)) }
    }

    private fun views(context: Context): RemoteViews {
        fun pending(code: Int, action: String, mode: String? = null): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (mode != null) intent.putExtra(MainActivity.EXTRA_SCAN_MODE, mode)
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return RemoteViews(context.packageName, R.layout.widget_scan).apply {
            setOnClickPendingIntent(R.id.widget_scan, pending(1, MainActivity.ACTION_SCAN_NOW))
            setOnClickPendingIntent(R.id.widget_id_card, pending(2, MainActivity.ACTION_SCAN_NOW, ScanMode.ID_CARD.name))
            setOnClickPendingIntent(R.id.widget_import, pending(3, MainActivity.ACTION_IMPORT_IMAGES))
        }
    }
}
