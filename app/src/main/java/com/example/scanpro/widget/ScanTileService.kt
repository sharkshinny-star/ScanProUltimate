package com.example.scanpro.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.example.scanpro.MainActivity

/**
 * The Quick Settings "Scan" tile (R33, as Google's scanner and Genius Scan offer): one tap from the shade opens the
 * camera. The tile is stateless; the system draws its icon and label from the manifest.
 */
class ScanTileService : TileService() {
    // Below Android 14 the Intent overload is the only one there is; lint flags it as deprecated regardless of the version check.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_SCAN_NOW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: activities are started from a tile through a PendingIntent.
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
