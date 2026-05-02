package com.applimiter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class BlockActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvAppName: TextView
    private lateinit var tvResetTime: TextView
    private lateinit var tvTimeUsed: TextView
    private lateinit var btnGoHome: Button

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)

        prefs = getSharedPreferences("AppLimiter", Context.MODE_PRIVATE)

        tvAppName = findViewById(R.id.tvBlockedAppName)
        tvResetTime = findViewById(R.id.tvResetTime)
        tvTimeUsed = findViewById(R.id.tvTimeUsedTotal)
        btnGoHome = findViewById(R.id.btnGoHome)

        val appName = prefs.getString("target_name", "Приложение") ?: "Приложение"
        tvAppName.text = appName

        val usedMs = prefs.getLong("used_today_ms", 0L)
        tvTimeUsed.text = "Использовано сегодня: ${formatTime(usedMs)}"

        btnGoHome.setOnClickListener {
            // Go to home screen
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            homeIntent.addCategory(android.content.Intent.CATEGORY_HOME)
            homeIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
            finish()
        }

        handler.post(updateRunnable)
    }

    private fun updateCountdown() {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val msUntilMidnight = midnight.timeInMillis - now.timeInMillis
        val hours = msUntilMidnight / (1000 * 60 * 60)
        val minutes = (msUntilMidnight % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (msUntilMidnight % (1000 * 60)) / 1000
        tvResetTime.text = "Сброс через: %02d:%02d:%02d".format(hours, minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        super.onDestroy()
    }

    override fun onBackPressed() {
        // Block back button - force user to go home
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        homeIntent.addCategory(android.content.Intent.CATEGORY_HOME)
        homeIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        finish()
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d ч %02d мин %02d сек".format(hours, minutes, seconds)
        } else {
            "%02d мин %02d сек".format(minutes, seconds)
        }
    }
}
