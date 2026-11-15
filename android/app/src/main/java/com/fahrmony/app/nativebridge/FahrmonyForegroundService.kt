package com.fahrmony.app.nativebridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicLong

class FahrmonyForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "fahrmony_service_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: FahrmonyForegroundService? = null

        @Volatile
        private var isForegroundActive = false

        private val startSeq = AtomicLong(0L)
        private val consumedStartSeq = AtomicLong(0L)
        private val stopSeq = AtomicLong(0L)

        fun isForegroundRunning(): Boolean = isForegroundActive

        fun start(context: Context) {
            startSeq.incrementAndGet()
            val intent = Intent(context, FahrmonyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val currentStart = startSeq.get()
            stopSeq.set(currentStart)
            if (currentStart > consumedStartSeq.get()) {
                // 在途 startForegroundService 尚未进入 onStartCommand，交由其先兑现 startForeground 后自毁，避免超时崩溃
                return
            }
            instance?.demoteAndStop()
            val appContext = context.applicationContext
            try {
                appContext.stopService(Intent(appContext, FahrmonyForegroundService::class.java))
            } catch (ignored: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        FahrmonyCarConnectionTracker.init(applicationContext)
        FahrmonyMediaManager.init(applicationContext)
        // 提前常驻唤醒媒体服务并初始化 MediaSession，车机插线前即已在系统内核就绪
        try {
            startService(Intent(applicationContext, FahrmonyMediaBrowserService::class.java))
        } catch (ignored: Exception) {}
        FahrmonyLogBuffer.addLog("SYSTEM", "ForegroundService", "前台守护已启动", "常驻守护与媒体总线监听中")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val curStart = startSeq.get()
        consumedStartSeq.set(curStart)
        if (intent == null && !FahrmonyMediaBrowserService.isCarConnected) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (stopSeq.get() >= curStart && curStart > 0L) {
            demoteAndStop()
            return START_NOT_STICKY
        }
        // START_STICKY 保证系统在车机活跃时内存紧张回收后自动拉起
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        FahrmonyLogBuffer.addLog("SYSTEM", "ForegroundService", "前台服务超时降级", "触发系统超时回调 (type=$fgsType)")
        demoteAndStop()
    }

    override fun onDestroy() {
        isForegroundActive = false
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun promoteToForeground(): Boolean {
        createNotificationChannel()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
            }
            isForegroundActive = true
            true
        } catch (e: Exception) {
            isForegroundActive = false
            android.util.Log.w("ForegroundService", "startForeground受限或安全降级: ${e.message}")
            false
        }
    }

    private fun demoteAndStop() {
        isForegroundActive = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (ignored: Exception) {}
        stopSelf()
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
