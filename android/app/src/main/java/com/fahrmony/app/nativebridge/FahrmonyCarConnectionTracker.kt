package com.fahrmony.app.nativebridge

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Build

/**
 * Fahrmony 车载连接与活跃投屏状态感知器
 * 运行于 :car 独立进程，负责高精度感知 Android Auto 物理投屏与连接状态。
 * 杜绝未连接或拔线下车后在手机端二次拦截并推流 IM 通知。
 */
object FahrmonyCarConnectionTracker {

    private const val CAR_CONNECTION_AUTHORITY = "androidx.car.app.connection"
    private val CAR_CONNECTION_URI = Uri.parse("content://$CAR_CONNECTION_AUTHORITY/connection_type")
    private const val ACTION_CAR_CONNECTION_UPDATED = "androidx.car.app.connection.action.CAR_CONNECTION_UPDATED"
    private const val EXTRA_CONNECTION_TYPE = "Connection-Type"

    private const val CONNECTION_TYPE_NOT_CONNECTED = 0
    private const val CONNECTION_TYPE_NATIVE = 1
    private const val CONNECTION_TYPE_PROJECTION = 2

    @Volatile
    private var cachedConnectionType: Int? = null

    @Volatile
    private var isReceiverRegistered = false

    /**
     * 注册车机连接广播监听器 (全局仅初始化一次，基于 ApplicationContext)
     */
    fun init(context: Context) {
        if (isReceiverRegistered) return
        synchronized(this) {
            if (isReceiverRegistered) return
            try {
                val filter = IntentFilter().apply {
                    addAction(ACTION_CAR_CONNECTION_UPDATED)
                    addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
                    addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
                }
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        when (intent?.action) {
                            ACTION_CAR_CONNECTION_UPDATED -> {
                                val type = intent.getIntExtra(EXTRA_CONNECTION_TYPE, -1)
                                if (type != -1) {
                                    cachedConnectionType = type
                                } else {
                                    cachedConnectionType = queryConnectionType(ctx ?: context)
                                }
                                if (cachedConnectionType == CONNECTION_TYPE_NOT_CONNECTED) {
                                    val c = ctx ?: context
                                    FahrmonyNotificationListener.clearBridgeNotifications(c)
                                    FahrmonyLogBuffer.clear()
                                    FahrmonyIpcBridge.notifyCarConnectedChanged(false)
                                }
                            }
                            UiModeManager.ACTION_ENTER_CAR_MODE -> {
                                cachedConnectionType = CONNECTION_TYPE_PROJECTION
                                FahrmonyIpcBridge.notifyCarConnectedChanged(true)
                            }
                            UiModeManager.ACTION_EXIT_CAR_MODE -> {
                                cachedConnectionType = CONNECTION_TYPE_NOT_CONNECTED
                                val c = ctx ?: context
                                FahrmonyNotificationListener.clearBridgeNotifications(c)
                                FahrmonyLogBuffer.clear()
                                FahrmonyIpcBridge.notifyCarConnectedChanged(false)
                            }
                        }
                    }
                }
                val appContext = context.applicationContext
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appContext.registerReceiver(receiver, filter)
                }
                isReceiverRegistered = true
            } catch (ignored: Exception) {}
        }
    }

    /**
     * 主动查询当前是否处于 Android Auto 活跃投屏或车机连接状态
     */
    fun isCarConnected(context: Context): Boolean {
        init(context)

        // 1. 优先检查同进程内 FahrmonyMediaBrowserService 的物理握手标记 (最直接的 Gearhead 连接状态)
        if (FahrmonyMediaBrowserService.isCarConnected) {
            return true
        }

        // 2. 查询 Android Auto (Gearhead) 官方投屏状态 Provider (PROJECTION = 2 为正在投屏)
        val currentType = queryConnectionType(context)
        if (currentType == CONNECTION_TYPE_PROJECTION || currentType == CONNECTION_TYPE_NATIVE) {
            return true
        }

        // 3. 检查系统 UiMode 是否处于车载模式
        try {
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_CAR) {
                return true
            }
        } catch (ignored: Exception) {}

        return false
    }

    private fun queryConnectionType(context: Context): Int {
        try {
            val cursor = context.contentResolver.query(
                CAR_CONNECTION_URI,
                arrayOf("connection_type"),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex("connection_type")
                    if (columnIndex != -1) {
                        val type = it.getInt(columnIndex)
                        cachedConnectionType = type
                        return type
                    }
                }
            }
        } catch (ignored: Exception) {}
        return cachedConnectionType ?: CONNECTION_TYPE_NOT_CONNECTED
    }
}
