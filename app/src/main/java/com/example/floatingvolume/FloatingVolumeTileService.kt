package com.example.floatingvolume

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class FloatingVolumeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            openAppSettingsScreen()
            return
        }

        val prefs = getSharedPreferences(FloatingVolumeService.PREFS_NAME, Context.MODE_PRIVATE)
        val running = prefs.getBoolean(FloatingVolumeService.KEY_IS_SERVICE_RUNNING, false)
        val serviceIntent = Intent(this, FloatingVolumeService::class.java)

        if (running) {
            stopService(serviceIntent)
            updateTile(isActive = false)
        } else {
            ContextCompat.startForegroundService(this, serviceIntent)
            updateTile(isActive = true)
        }
    }

    private fun refreshTileState() {
        val prefs = getSharedPreferences(FloatingVolumeService.PREFS_NAME, Context.MODE_PRIVATE)
        val running = prefs.getBoolean(FloatingVolumeService.KEY_IS_SERVICE_RUNNING, false)
        updateTile(isActive = running && Settings.canDrawOverlays(this))
    }

    private fun updateTile(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "Active" else "Off"
        }
        tile.updateTile()
    }

    private fun openAppSettingsScreen() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
