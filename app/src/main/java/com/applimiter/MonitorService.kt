package com.applimiter

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "AppLimiterChannel"
        const val NOTIFICATION_ID = 1
        const val CHECK_INTERVAL_MS = 1000L
        private const val TAG = "MonitorService"
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var lastActiveStart = 0L
    private var wasTargetActive = false

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkAndTrack()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("AppLimiter", Context.MODE_PRIVATE)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        isMonitoring = true
        scheduleMidnightReset()
        handler.post(monitorRunnable)
        Log.d(TAG, "MonitorService started")
        return START_STICKY
    }

    override fun onDestroy() {
        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
        // Save any ongoing session time before stopping
        if (wasTargetActive && lastActiveStart > 0) {
            val sessionTime = System.currentTimeMillis() - lastActiveStart
            addUsedTime(sessionTime)
            lastActiveStart = 0
            wasTargetActive = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkAndTrack() {
        val targetPkg = prefs.getString("target_package", null) ?: return
        val limitMs = prefs.getLong("daily_limit_ms", 30 * 60 * 1000L)

        val currentForeground = getForegroundApp()
        val isTargetActive = currentForeground == targetPkg

        if (isTargetActive && !wasTargetActive) {
            // App just came to foreground
            lastActiveStart = System.currentTimeMillis()
            wasTargetActive = true
            Log.d(TAG, "Target app opened: $targetPkg")
        } else if (!isTargetActive && wasTargetActive) {
            // App went to background
            if (lastActiveStart > 0) {
                val sessionTime = System.currentTimeMillis() - lastActiveStart
                addUsedTime(sessionTime)
                lastActiveStart = 0
            }
            wasTargetActive = false
            Log.d(TAG, "Target app closed, session saved")
        } else if (isTargetActive && wasTargetActive) {
            // App is still active - check if limit exceeded
            val sessionTime = if (lastActiveStart > 0) System.currentTimeMillis() - lastActiveStart else 0L
            val savedUsed = prefs.getLong("used_today_ms", 0L)
            val totalUsed = savedUsed + sessionTime

            if (totalUsed >= limitMs) {
                // Limit exceeded! Save time and show block screen
                addUsedTime(sessionTime)
                lastActiveStart = System.currentTimeMillis() // reset to avoid double counting
                showBlockScreen()
                Log.d(TAG, "Limit exceeded! Showing block screen")
            }
        }

        // Update notification with current stats
        updateNotification()
    }

    private fun getForegroundApp(): String? {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 3000 // last 3 seconds
            val stats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY, startTime, endTime
            )
            stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app", e)
            null
        }
    }

    private fun addUsedTime(ms: Long) {
        if (ms <= 0) return
        val current = prefs.getLong("used_today_ms", 0L)
        prefs.edit().putLong("used_today_ms", current + ms).apply()
        Log.d(TAG, "Added ${ms}ms, total: ${current + ms}ms")
    }

    private fun showBlockScreen() {
        val intent = Intent(this, BlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun scheduleMidnightReset() {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, MidnightReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }

        Log.d(TAG, "Midnight reset scheduled for ${calendar.time}")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Мониторинг приложений",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Отслеживание времени использования приложений"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val targetName = prefs.getString("target_name", "Приложение") ?: "Приложение"
        val limitMs = prefs.getLong("daily_limit_ms", 30 * 60 * 1000L)
        val usedMs = prefs.getLong("used_today_ms", 0L)
        val leftMs = maxOf(0L, limitMs - usedMs)

        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AppLimiter активен")
            .setContentText("$targetName • Осталось: ${formatTime(leftMs)}")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%dч %02dм".format(hours, minutes)
        } else {
            "%02dм %02dс".format(minutes, seconds)
        }
    }
}
