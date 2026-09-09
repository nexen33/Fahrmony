package com.fahrmony.app.nativebridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class FahrmonyForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "fahrmony_service_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FahrmonyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildForegroundNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }
        FahrmonyMediaManager.init(applicationContext)
        // 提前常驻唤醒媒体服务并初始化 MediaSession，车机插线前即已在系统内核就绪
        try {
            startService(Intent(applicationContext, FahrmonyMediaBrowserService::class.java))
        } catch (ignored: Exception) {}
        FahrmonyLogBuffer.addLog("SYSTEM", "ForegroundService", "前台守护已启动", "常驻守护与媒体总线监听中")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY 保证系统在内存紧张回收后自动拉起
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return FahrmonyIpcBridge.getServerBinder(applicationContext)
    }

    private fun buildForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fahrmony · 运行中")
            .setContentText("IM 通知与媒体总线守护中，服务 Android Auto")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Fahrmony 守护服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "确保桥接服务在后台持续稳定运行"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
