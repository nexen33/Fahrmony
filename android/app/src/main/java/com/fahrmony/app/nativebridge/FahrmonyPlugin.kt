package com.fahrmony.app.nativebridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "FahrmonyPlugin")
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
                } else {
                    put("hasActiveSession", false)
                }
            }
            notifyListeners("mediaSessionChanged", data)
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
            }
            array.put(obj)
        }
        call.resolve(JSObject().put("sessions", array))
    }

    @PluginMethod
    fun sendMediaCommand(call: PluginCall) {
        val action = call.getString("action") ?: ""
        val pos = call.getDouble("position")?.toLong() ?: 0L

        if (action.equals("play", ignoreCase = true)) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val isAudioActive = am?.isMusicActive == true
            val sessions = FahrmonyIpcBridge.getCachedSessions()
            val isAlreadyPlaying = sessions.firstOrNull()?.isPlaying ?: false

            // 仅当系统没有任何音乐在播放且无活跃播放会话时，才拉起应用，杜绝已在播放时误弹
            if (!isAlreadyPlaying && !isAudioActive) {
                val targetPkg = FahrmonyConfig.getDefaultPlayer(context)
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
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

        FahrmonyIpcBridge.sendMediaCommand(action, pos)
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun getCapturedLogs(call: PluginCall) {
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
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            call.resolve(JSObject().put("success", true))
        } else {
            call.resolve(JSObject().put("success", false).put("error", "未检测到已安装该应用"))
        }
    }
}
