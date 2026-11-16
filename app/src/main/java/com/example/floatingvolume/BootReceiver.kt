package com.example.floatingvolume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(
            FloatingVolumeService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val autoStart = prefs.getBoolean(FloatingVolumeService.KEY_AUTO_BOOT, false)

        if (autoStart && Settings.canDrawOverlays(context)) {
            val serviceIntent = Intent(context, FloatingVolumeService::class.java)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
                // Restricted by OS background start policy
            }
        }
    }
}
