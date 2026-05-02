package com.applimiter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("AppLimiter", Context.MODE_PRIVATE)
            val isRunning = prefs.getBoolean("service_running", false)
            if (isRunning) {
                val serviceIntent = Intent(context, MonitorService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
                Log.d("BootReceiver", "Restarted MonitorService after boot")
            }
        }
    }
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MidnightReceiver", "Midnight reset triggered!")
        val prefs = context.getSharedPreferences("AppLimiter", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("used_today_ms", 0L)
            .apply()

        // Reschedule for next midnight
        val serviceIntent = Intent(context, MonitorService::class.java)
        if (prefs.getBoolean("service_running", false)) {
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
