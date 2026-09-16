package com.fahrmony.app.nativebridge

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback

@CapacitorPlugin(
    name = "FahrmonyPlugin",
    permissions = [
        Permission(
            strings = [Manifest.permission.POST_NOTIFICATIONS],
            alias = "postNotifications"
        )
    ]
)
class FahrmonyPlugin : Plugin() {

    override fun load() {
        super.load()
        // 自动拉起前台保活服务 (运行于 :car 进程)
        FahrmonyForegroundService.start(context)

        // 注册主进程 IPC 客户端：连接 :car 进程并接收反应式事件推流
        FahrmonyIpcBridge.initClient(context) { info ->
            val data = JSObject().apply {
                if (info != null) {
                    put("hasActiveSession", true)
                    put("packageName", info.packageName)
                    put("appName", info.appName)
                    put("title", info.title)
                    put("artist", info.artist)
                    put("album", info.album)
                    put("isPlaying", info.isPlaying)
                    put("duration", info.duration)
                    put("position", info.position)
                    put("artworkData", info.artworkData)
                } else {
                    put("hasActiveSession", false)
                }
            }
            android.util.Log.i("Fahrmony_CUSTOM", "[JS_NOTIFY] mediaSessionChanged: pkg=${info?.packageName}, title='${info?.title}', isPlaying=${info?.isPlaying}")
            notifyListeners("mediaSessionChanged", data)
        }

        // 监听底层车机连接状态推流，实时派发前端事件
        FahrmonyIpcBridge.onCarConnectedCallback = { connected ->
            val data = JSObject().apply {
                put("connected", connected)
            }
            notifyListeners("carConnectionChanged", data)
        }
    }

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val context = context
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }

        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
        val hasNotificationListener = enabledListeners.contains(context.packageName)

        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val ret = JSObject().apply {
            put("notificationListener", hasNotificationListener)
            put("batteryOptimized", !isIgnoringBattery)
            put("postNotifications", areNotificationsEnabled)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun requestNotificationPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                requestPermissionForAlias("postNotifications", call, "postNotificationCallback")
                return
            }
        }
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        call.resolve(JSObject().put("granted", areNotificationsEnabled))
    }

    @PermissionCallback
    private fun postNotificationCallback(call: PluginCall) {
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        call.resolve(JSObject().put("granted", areNotificationsEnabled))
    }

    @PluginMethod
    fun openPermissionSettings(call: PluginCall) {
        val type = call.getString("type") ?: ""
        val context = context
        val intent = when (type) {
            "notification_listener" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                } else {
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                }
            }
            "battery_optimization" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }
            "app_notification" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
            }
            "app_details" -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            call.resolve(JSObject().put("success", true))
        } catch (e: Exception) {
            call.reject("无法打开系统设置界面: ${e.message}")
        }
    }

    @PluginMethod
    fun getBridgeStatus(call: PluginCall) {
        val sessions = FahrmonyIpcBridge.getCachedSessions()
        val ret = JSObject().apply {
            put("isForegroundRunning", true)
            put("isCarConnected", FahrmonyIpcBridge.isCarConnected())
            put("activeSessionCount", sessions.size)
            put("capturedLogCount", FahrmonyLogBuffer.getLogs().size)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun getActiveMediaSessions(call: PluginCall) {
        FahrmonyIpcBridge.requestRefresh()
        val sessions = FahrmonyIpcBridge.getCachedSessions()
        val array = JSArray()
        for (session in sessions) {
            val obj = JSObject().apply {
                put("packageName", session.packageName)
                put("appName", session.appName)
                put("title", session.title)
                put("artist", session.artist)
                put("album", session.album)
                put("isPlaying", session.isPlaying)
                put("duration", session.duration)
                put("position", session.position)
                put("artworkData", session.artworkData)
            }
            array.put(obj)
        }
        call.resolve(JSObject().put("sessions", array))
    }

    private fun isPackageMatch(pkgA: String?, pkgB: String?): Boolean {
        if (pkgA.isNullOrBlank() || pkgB.isNullOrBlank()) return false
        if (pkgA.equals(pkgB, ignoreCase = true)) return true
        // 酷狗音乐双包名 (kugou.service / com.kugou.android / com.kugou.android.lite) 归一化兜底匹配
        val kugouAliases = setOf("kugou.service", "com.kugou.android", "com.kugou.android.lite")
        if (pkgA in kugouAliases && pkgB in kugouAliases) {
            return true
        }
        return false
    }

    @PluginMethod
    fun sendMediaCommand(call: PluginCall) {
        val action = call.getString("action") ?: ""
        val pos = call.getDouble("position")?.toLong() ?: 0L
        val packageName = call.getString("packageName")

        if (action.equals("play", ignoreCase = true)) {
            val targetPkg = if (!packageName.isNullOrBlank()) packageName else FahrmonyConfig.getDefaultPlayer(context)
            val sessions = FahrmonyIpcBridge.getCachedSessions()
            val hasSessionForTarget = sessions.any { isPackageMatch(it.packageName, targetPkg) }

            // 智能会话守卫：仅当目标播放器在底层彻底不存在会话时 (冷启动或被系统彻底查杀)，才需要 startActivity 拉起应用
            // 若会话已就绪 (即便当前处于暂停态)，绝不触发 startActivity，纯后台下发播控指令
            if (!hasSessionForTarget && targetPkg.isNotBlank()) {
                var launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                // 酷狗服务包名 (kugou.service) 若无启动入口，兜底使用主界面包名尝试拉起
                if (launchIntent == null && isPackageMatch(targetPkg, "com.kugou.android")) {
                    launchIntent = context.packageManager.getLaunchIntentForPackage("com.kugou.android")
                }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    try {
                        if (activity != null) {
                            activity.startActivity(launchIntent)
                        } else {
                            context.startActivity(launchIntent)
                        }
                    } catch (ignored: Exception) {}
                }
            }
        }

        FahrmonyIpcBridge.sendMediaCommand(action, pos, packageName)
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun getCapturedLogs(call: PluginCall) {
        // 双保险守卫：若车机当前未处于连接状态，直接清空主进程旧日志缓存并返回空列表
        if (!FahrmonyIpcBridge.isCarConnected()) {
            FahrmonyLogBuffer.clear()
        }
        val list = FahrmonyLogBuffer.getLogs()
        val array = JSArray()
        list.forEach { log ->
            val obj = JSObject().apply {
                put("id", log.id)
                put("timestamp", log.timestamp)
                put("type", log.type)
                put("tag", log.tag)
                put("title", log.title)
                put("content", log.content)
                put("rawExtras", log.rawExtras)
            }
            array.put(obj)
        }
        call.resolve(JSObject().put("logs", array))
    }

    @PluginMethod
    fun clearLogs(call: PluginCall) {
        FahrmonyIpcBridge.clearLogs()
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun setAppBridgeConfig(call: PluginCall) {
        val configObj = call.getObject("config")
        if (configObj != null) {
            FahrmonyConfig.updateConfig(context, configObj)
            FahrmonyIpcBridge.notifyConfigChanged(configObj.toString())
        }
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun getAppBridgeConfig(call: PluginCall) {
        val config = FahrmonyConfig.getConfig(context)
        val jsObj = JSObject()
        val keys = config.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            jsObj.put(k, config.get(k))
        }
        call.resolve(JSObject().put("config", jsObj))
    }

    @PluginMethod
    fun launchApp(call: PluginCall) {
        val packageName = call.getString("packageName")
        if (packageName.isNullOrEmpty()) {
            call.reject("包名不能为空")
            return
        }
        val pm = context.packageManager
        var intent = pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                if (!resolveInfos.isNullOrEmpty()) {
                    val act = resolveInfos[0].activityInfo
                    intent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        component = ComponentName(act.packageName, act.name)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            } catch (ignored: Exception) {}
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve(JSObject().put("success", true))
        } else {
            call.resolve(JSObject().put("success", false).put("error", "未检测到已安装该应用"))
        }
    }

    @PluginMethod
    fun checkUpdate(call: PluginCall) {
        FahrmonyUpdateManager.performCheckAndNotify(context, isManual = true) { info ->
            val res = JSObject().apply {
                put("hasUpdate", info.hasUpdate)
                put("latestVersion", info.latestVersion)
                put("downloadUrl", info.downloadUrl)
            }
            call.resolve(res)
        }
    }

    @PluginMethod
    fun getDiscoveredMediaSessions(call: PluginCall) {
        val list = FahrmonyMediaManager.getDiscoveredMediaSessions(context)
        val array = org.json.JSONArray()
        for (item in list) {
            val obj = JSObject().apply {
                put("packageName", item.packageName)
                put("appName", item.appName)
                put("iconBase64", item.iconBase64)
                put("title", item.title)
                put("artist", item.artist)
                put("isPlaying", item.isPlaying)
            }
            array.put(obj)
        }
        call.resolve(JSObject().put("discoveredSessions", array))
    }
}
