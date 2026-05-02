package com.applimiter

import android.app.AppOpsManager
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvCurrentApp: TextView
    private lateinit var tvAppPackage: TextView
    private lateinit var tvTimeUsed: TextView
    private lateinit var tvTimeLeft: TextView
    private lateinit var tvLimit: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectApp: Button
    private lateinit var btnSetLimit: Button
    private lateinit var btnToggleService: Button
    private lateinit var ivAppIcon: ImageView
    private lateinit var cardAppInfo: View
    private lateinit var tvNoApp: TextView
    private lateinit var tvStatus: TextView
    private lateinit var circularProgress: CircularProgressView

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("AppLimiter", Context.MODE_PRIVATE)

        initViews()
        checkPermissions()
        setupClickListeners()
    }

    private fun initViews() {
        tvCurrentApp = findViewById(R.id.tvCurrentApp)
        tvAppPackage = findViewById(R.id.tvAppPackage)
        tvTimeUsed = findViewById(R.id.tvTimeUsed)
        tvTimeLeft = findViewById(R.id.tvTimeLeft)
        tvLimit = findViewById(R.id.tvLimit)
        progressBar = findViewById(R.id.progressBar)
        btnSelectApp = findViewById(R.id.btnSelectApp)
        btnSetLimit = findViewById(R.id.btnSetLimit)
        btnToggleService = findViewById(R.id.btnToggleService)
        ivAppIcon = findViewById(R.id.ivAppIcon)
        cardAppInfo = findViewById(R.id.cardAppInfo)
        tvNoApp = findViewById(R.id.tvNoApp)
        tvStatus = findViewById(R.id.tvStatus)
        circularProgress = findViewById(R.id.circularProgress)
    }

    private fun setupClickListeners() {
        btnSelectApp.setOnClickListener { showAppPickerDialog() }
        btnSetLimit.setOnClickListener { showSetLimitDialog() }
        btnToggleService.setOnClickListener { toggleService() }
    }

    private fun checkPermissions() {
        if (!hasUsagePermission()) {
            showUsagePermissionDialog()
        } else if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        }
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showUsagePermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение")
            .setMessage("Для отслеживания времени использования приложений необходимо предоставить доступ к статистике использования.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setNegativeButton("Отмена", null)
            .setCancelable(false)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Разрешение на отображение поверх других приложений")
            .setMessage("Для блокировки приложений необходимо разрешение отображения поверх других приложений.")
            .setPositiveButton("Открыть настройки") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAppPickerDialog() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString() }

        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val icons = apps.map { it.loadIcon(pm) }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_picker, null)
        val listView = dialogView.findViewById<ListView>(R.id.listViewApps)
        val searchView = dialogView.findViewById<SearchView>(R.id.searchViewApps)

        var filteredApps = apps.toMutableList()
        var filteredNames = names.toMutableList()

        val adapter = AppListAdapter(this, filteredApps, pm)
        listView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText?.lowercase() ?: ""
                filteredApps = apps.filter {
                    it.loadLabel(pm).toString().lowercase().contains(query)
                }.toMutableList()
                adapter.updateData(filteredApps)
                return true
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Выберите приложение")
            .setView(dialogView)
            .setNegativeButton("Отмена", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = adapter.getItem(position)
            val pkg = selected!!.activityInfo.packageName
            val appName = selected.loadLabel(pm).toString()

            prefs.edit()
                .putString("target_package", pkg)
                .putString("target_name", appName)
                .apply()

            dialog.dismiss()
            updateUI()
        }

        dialog.show()
    }

    private fun showSetLimitDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_limit, null)
        val etHours = dialogView.findViewById<EditText>(R.id.etHours)
        val etMinutes = dialogView.findViewById<EditText>(R.id.etMinutes)

        val currentLimit = prefs.getLong("daily_limit_ms", 30 * 60 * 1000L)
        val hours = currentLimit / (60 * 60 * 1000)
        val minutes = (currentLimit % (60 * 60 * 1000)) / (60 * 1000)

        etHours.setText(hours.toString())
        etMinutes.setText(minutes.toString())

        AlertDialog.Builder(this)
            .setTitle("Установить дневной лимит")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val h = etHours.text.toString().toLongOrNull() ?: 0
                val m = etMinutes.text.toString().toLongOrNull() ?: 30
                val limitMs = (h * 60 * 60 * 1000) + (m * 60 * 1000)
                if (limitMs > 0) {
                    prefs.edit().putLong("daily_limit_ms", limitMs).apply()
                    updateUI()
                } else {
                    Toast.makeText(this, "Лимит должен быть больше 0", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun toggleService() {
        val isRunning = prefs.getBoolean("service_running", false)
        val targetPkg = prefs.getString("target_package", null)

        if (targetPkg == null) {
            Toast.makeText(this, "Сначала выберите приложение", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasUsagePermission()) {
            showUsagePermissionDialog()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
            return
        }

        if (!isRunning) {
            val intent = Intent(this, MonitorService::class.java)
            ContextCompat.startForegroundService(this, intent)
            prefs.edit().putBoolean("service_running", true).apply()
            btnToggleService.text = "⏹ Остановить мониторинг"
            btnToggleService.setBackgroundColor(getColor(R.color.red))
            tvStatus.text = "🟢 Мониторинг активен"
        } else {
            val intent = Intent(this, MonitorService::class.java)
            stopService(intent)
            prefs.edit().putBoolean("service_running", false).apply()
            btnToggleService.text = "▶ Запустить мониторинг"
            btnToggleService.setBackgroundColor(getColor(R.color.green))
            tvStatus.text = "🔴 Мониторинг остановлен"
        }
    }

    private fun updateUI() {
        val targetPkg = prefs.getString("target_package", null)
        val targetName = prefs.getString("target_name", "Не выбрано")
        val limitMs = prefs.getLong("daily_limit_ms", 30 * 60 * 1000L)
        val usedMs = prefs.getLong("used_today_ms", 0L)
        val isRunning = prefs.getBoolean("service_running", false)

        if (targetPkg == null) {
            cardAppInfo.visibility = View.GONE
            tvNoApp.visibility = View.VISIBLE
        } else {
            cardAppInfo.visibility = View.VISIBLE
            tvNoApp.visibility = View.GONE

            tvCurrentApp.text = targetName
            tvAppPackage.text = targetPkg

            try {
                val icon = packageManager.getApplicationIcon(targetPkg)
                ivAppIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            val leftMs = maxOf(0L, limitMs - usedMs)
            val progress = if (limitMs > 0) ((usedMs.toFloat() / limitMs) * 100).toInt() else 0

            tvTimeUsed.text = "Использовано: ${formatTime(usedMs)}"
            tvTimeLeft.text = "Осталось: ${formatTime(leftMs)}"
            tvLimit.text = "Лимит: ${formatTime(limitMs)}"
            progressBar.progress = minOf(progress, 100)

            circularProgress.setProgress(minOf(progress, 100))
            circularProgress.setTimeLeft(formatTime(leftMs))

            if (leftMs == 0L) {
                tvTimeLeft.setTextColor(getColor(R.color.red))
                tvStatus.text = "🚫 Лимит исчерпан"
            } else {
                tvTimeLeft.setTextColor(getColor(R.color.green))
            }
        }

        if (isRunning) {
            btnToggleService.text = "⏹ Остановить мониторинг"
            btnToggleService.setBackgroundColor(getColor(R.color.red))
            if (targetPkg != null) {
                val usedMs2 = prefs.getLong("used_today_ms", 0L)
                val leftMs2 = maxOf(0L, limitMs - usedMs2)
                tvStatus.text = if (leftMs2 == 0L) "🚫 Лимит исчерпан" else "🟢 Мониторинг активен"
            }
        } else {
            btnToggleService.text = "▶ Запустить мониторинг"
            btnToggleService.setBackgroundColor(getColor(R.color.green))
            if (prefs.getString("target_package", null) != null) {
                tvStatus.text = "🔴 Мониторинг остановлен"
            }
        }
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

    override fun onResume() {
        super.onResume()
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }
}

class AppListAdapter(
    private val context: Context,
    private var apps: List<android.content.pm.ResolveInfo>,
    private val pm: PackageManager
) : BaseAdapter() {

    fun updateData(newApps: List<android.content.pm.ResolveInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun getCount() = apps.size
    override fun getItem(position: Int) = apps[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]
        view.findViewById<TextView>(R.id.tvAppName).text = app.loadLabel(pm)
        view.findViewById<TextView>(R.id.tvPackageName).text = app.activityInfo.packageName
        view.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(app.loadIcon(pm))
        return view
    }
}
