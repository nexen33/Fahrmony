package com.fahrmony.app.nativebridge

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Fahrmony 更新管理引擎 (遵循 2026 现代移动架构规范)
 * 1. 每日 20:00 后台定时检测最新 GitHub Release；
 * 2. 支持中/英/德/日四国语言通知与选项构建；
 * 3. 严格遵循向后兼容性原则与优雅异常降级。
 */
object FahrmonyUpdateManager {

    private const val GITHUB_API_LATEST = "https://api.github.com/repos/nexen33/Fahrmony/releases/latest"
    private const val GITHUB_RELEASE_PAGE = "https://github.com/nexen33/Fahrmony/releases/latest"
    private const val PREFS_NAME = "fahrmony_update_prefs"
    private const val KEY_LAST_CHECK_DATE = "last_check_date"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    const val CHANNEL_ID_UPDATE = "fahrmony_app_update_channel"
    const val NOTIFICATION_ID_UPDATE = 2026
    const val ACTION_IGNORE_UPDATE = "com.fahrmony.app.ACTION_IGNORE_UPDATE"
    const val EXTRA_VERSION_TO_IGNORE = "extra_version_to_ignore"

    private val executor = Executors.newSingleThreadExecutor()

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseUrl: String,
        val changelog: String
    )

    fun init(context: Context) {
        createNotificationChannel(context)
        scheduleDailyAlarm(context)
        // 兜底心跳：若当前时间已过当天 20:00 且当天尚未检查，自动静默触发一次
        checkDailyCatchUp(context)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID_UPDATE,
                "Fahrmony 应用更新",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "检测到 GitHub 新版本发布时的提醒通知"
                enableLights(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun scheduleDailyAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, FahrmonyUpdateAlarmReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 8820, intent, flags)

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // 20:00 前后 5 分钟离散随机抖动 (-300 秒 ~ +300 秒，平滑分布在 19:55 ~ 20:05)
                val jitterSeconds = (-300..300).random()
                add(Calendar.SECOND, jitterSeconds)

                // 若计算后的时间戳早于当前时刻，调度到明天的对应抖动时段
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (ignored: Exception) {
            // 安全降级：无需崩溃
        }
    }

    private fun checkDailyCatchUp(context: Context) {
        executor.execute {
            try {
                val cal = Calendar.getInstance()
                val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val todayDateKey = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
                val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastDate = sp.getString(KEY_LAST_CHECK_DATE, null)

                // 若当天已过 20:05 (完全错过 19:55~20:05 抖动窗口)，且今天尚未执行过检查
                val isPastWindow = currentHour > 20 || (currentHour == 20 && cal.get(Calendar.MINUTE) >= 5)
                if (isPastWindow && lastDate != todayDateKey) {
                    performCheckAndNotify(context, isManual = false)
                }
            } catch (ignored: Exception) {}
        }
    }

    fun performCheckAndNotify(context: Context, isManual: Boolean, callback: ((UpdateInfo) -> Unit)? = null) {
        executor.execute {
            val result = fetchReleaseInfo(context)
            if (!isManual) {
                // 记录今天已检查
                val cal = Calendar.getInstance()
                val todayDateKey = "${cal.get(Calendar.YEAR)}_${cal.get(Calendar.DAY_OF_YEAR)}"
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CHECK_DATE, todayDateKey)
                    .apply()
            }

            if (result.hasUpdate && !isManual) {
                // 检查用户是否已忽略此版本
                val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val ignoredVersion = sp.getString(KEY_IGNORED_VERSION, null)
                if (ignoredVersion != result.latestVersion) {
                    showUpdateNotification(context, result)
                }
            }

            callback?.invoke(result)
        }
    }

    fun fetchReleaseInfo(context: Context): UpdateInfo {
        val currentVersion = getCurrentVersionName(context)
        var connection: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_LATEST)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("User-Agent", "Fahrmony-App-Android")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()

                val json = JSONObject(sb.toString())
                val tagName = json.optString("tag_name", "")
                val body = json.optString("body", "")
                val htmlUrl = json.optString("html_url", GITHUB_RELEASE_PAGE)

                var directApkUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            directApkUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                val hasUpdate = isRemoteNewer(tagName, currentVersion)
                return UpdateInfo(
                    hasUpdate = hasUpdate,
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    downloadUrl = directApkUrl,
                    releaseUrl = htmlUrl,
                    changelog = body
                )
            }
        } catch (ignored: Exception) {
            // 网络异常时安全降级
        } finally {
            connection?.disconnect()
        }

        return UpdateInfo(
            hasUpdate = false,
            currentVersion = currentVersion,
            latestVersion = currentVersion,
            downloadUrl = GITHUB_RELEASE_PAGE,
            releaseUrl = GITHUB_RELEASE_PAGE,
            changelog = ""
        )
    }

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "v1.1.5"
        } catch (e: Exception) {
            "v1.1.5"
        }
    }

    fun isRemoteNewer(remoteVer: String, currentVer: String): Boolean {
        if (remoteVer.isBlank()) return false
        val cleanRemote = remoteVer.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVer.trim().removePrefix("v").removePrefix("V")
        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IGNORED_VERSION, version)
            .apply()
    }

    private fun getSavedLanguage(context: Context): String {
        val sp = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
        val saved = sp.getString("fahrmony_lang", null) ?: sp.getString("fahrmony_language", null)
        if (!saved.isNullOrBlank()) {
            return saved
        }
        val sysLang = java.util.Locale.getDefault().language
        return when {
            sysLang.startsWith("zh", ignoreCase = true) -> "zh-CN"
            sysLang.startsWith("de", ignoreCase = true) -> "de-DE"
            sysLang.startsWith("ja", ignoreCase = true) -> "ja-JP"
            else -> "en-US"
        }
    }

    private fun showUpdateNotification(context: Context, info: UpdateInfo) {
        val lang = getSavedLanguage(context)

        // 四国语言严格适配字典
        val title = when (lang) {
            "en-US" -> "Fahrmony New Version Found ${info.latestVersion}"
            "de-DE" -> "Neue Fahrmony-Version gefunden ${info.latestVersion}"
            "ja-JP" -> "Fahrmony の新バージョンが見つかりました ${info.latestVersion}"
            else -> "发现 Fahrmony 新版本 ${info.latestVersion}"
        }

        val contentText = when (lang) {
            "en-US" -> "Would you like to download the update?"
            "de-DE" -> "Möchten Sie das Update herunterladen?"
            "ja-JP" -> "アップデートをダウンロードしますか？"
            else -> "是否需要下载更新？"
        }

        val btnDownload = when (lang) {
            "en-US" -> "Go to GitHub to Download Update"
            "de-DE" -> "Zu GitHub gehen & Update laden"
            "ja-JP" -> "GitHub でダウンロード"
            else -> "前往 Github 下载更新"
        }

        val btnIgnore = when (lang) {
            "en-US" -> "Ignore"
            "de-DE" -> "Ignorieren"
            "ja-JP" -> "無視"
            else -> "忽略"
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Action 1: 前往下载 Intent (调用系统默认浏览器)
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pDownload = PendingIntent.getActivity(context, 101, downloadIntent, flags)

        // Action 2: 忽略 Intent (发送广播给自身 Receiver)
        val ignoreIntent = Intent(context, FahrmonyUpdateReceiver::class.java).apply {
            action = ACTION_IGNORE_UPDATE
            putExtra(EXTRA_VERSION_TO_IGNORE, info.latestVersion)
        }
        val pIgnore = PendingIntent.getBroadcast(context, 102, ignoreIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_UPDATE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pDownload)
            .addAction(android.R.drawable.ic_menu_upload, btnDownload, pDownload)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, btnIgnore, pIgnore)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.notify(NOTIFICATION_ID_UPDATE, notification)
    }
}

/**
 * 广播接收器：处理用户点击“忽略”Action
 */
class FahrmonyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == FahrmonyUpdateManager.ACTION_IGNORE_UPDATE) {
            val verToIgnore = intent.getStringExtra(FahrmonyUpdateManager.EXTRA_VERSION_TO_IGNORE)
            if (!verToIgnore.isNullOrBlank()) {
                FahrmonyUpdateManager.ignoreVersion(context, verToIgnore)
            }
            // 清除当前通知
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(FahrmonyUpdateManager.NOTIFICATION_ID_UPDATE)
        }
    }
}

/**
 * 广播接收器：每日 20:00 AlarmManager 定时唤醒
 */
class FahrmonyUpdateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        FahrmonyUpdateManager.performCheckAndNotify(context, isManual = false)
        // 重新调度次日 20:00
        FahrmonyUpdateManager.init(context)
    }
}
