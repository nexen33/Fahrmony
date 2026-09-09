package com.fahrmony.app.nativebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Fahrmony 极简多进程 IPC 桥接管理器
 * 负责主进程 (MainActivity / Capacitor WebView) 与 :car 独立进程 (CarApp / Notification / Foreground)
 * 之间基于标准 Messenger 的非阻塞双向通信与状态同步。
 */
object FahrmonyIpcBridge {

    const val MSG_REGISTER_CLIENT = 1
    const val MSG_UNREGISTER_CLIENT = 2
    const val MSG_SYNC_STATE = 3
    const val MSG_SESSION_CHANGED = 4
    const val MSG_LOG_ADDED = 5
    const val MSG_MEDIA_COMMAND = 6
    const val MSG_CLEAR_LOGS = 7
    const val MSG_UPDATE_CONFIG = 8

    // ==========================================
    // 1. :car 服务端 (运行于 :car 独立进程)
    // ==========================================
    private var serverMessenger: Messenger? = null
    private var clientMessenger: Messenger? = null
    private var isServerInitialized = false

    fun getServerBinder(context: Context): IBinder {
        if (!isServerInitialized) {
            val serverHandler = ServerHandler(context.applicationContext)
            serverMessenger = Messenger(serverHandler)

            // 监听 :car 进程中媒体管理器事件，主动推送到主进程
            FahrmonyMediaManager.onActiveSessionChangedListener = { info ->
                notifyClientSessionChanged(info)
            }

            // 监听 :car 进程中日志入队事件，主动推送到主进程
            FahrmonyLogBuffer.onLogAdded = { entry ->
                notifyClientLogAdded(entry)
            }

            isServerInitialized = true
        }
        return serverMessenger!!.binder
    }

    private class ServerHandler(private val context: Context) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    clientMessenger = msg.replyTo
                    sendSyncState(context)
                }
                MSG_UNREGISTER_CLIENT -> {
                    if (clientMessenger == msg.replyTo) {
                        clientMessenger = null
                    }
                }
                MSG_MEDIA_COMMAND -> {
                    val action = msg.data.getString("action") ?: ""
                    val position = msg.data.getLong("position", 0L)
                    if (action.equals("play", ignoreCase = true)) {
                        FahrmonyMediaManager.play(context)
                    } else if (action.equals("seek_to", ignoreCase = true)) {
                        FahrmonyMediaManager.seekTo(position)
                    } else {
                        FahrmonyMediaManager.sendCommand(action)
                    }
                }
                MSG_CLEAR_LOGS -> {
                    FahrmonyLogBuffer.clear()
                }
                MSG_UPDATE_CONFIG -> {
                    val jsonStr = msg.data?.getString("configJson")
                    if (!jsonStr.isNullOrEmpty()) {
                        try {
                            FahrmonyConfig.updateConfig(context, org.json.JSONObject(jsonStr))
                        } catch (ignored: Exception) {}
                    }
                    FahrmonyMediaManager.refresh(context)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    fun notifyCarConnectedChanged(connected: Boolean) {
        val client = clientMessenger ?: return
        try {
            val msg = Message.obtain(null, MSG_SYNC_STATE).apply {
                data = Bundle().apply {
                    putBoolean("isCarConnected", connected)
                }
            }
            client.send(msg)
        } catch (ignored: RemoteException) {
            clientMessenger = null
        }
    }

    private fun sendSyncState(context: Context) {
        val client = clientMessenger ?: return
        try {
            val sessions = FahrmonyMediaManager.getAllActiveSessions(context)
            val sessionBundles = ArrayList<Bundle>()
            for (s in sessions) {
                sessionBundles.add(sessionToBundle(s))
            }

            val msg = Message.obtain(null, MSG_SYNC_STATE).apply {
                data = Bundle().apply {
                    putParcelableArrayList("sessions", sessionBundles)
                    putBoolean("isCarConnected", FahrmonyNotificationListener.isConnected)
                }
            }
            client.send(msg)
        } catch (ignored: RemoteException) {
            clientMessenger = null
        }
    }

    private fun notifyClientSessionChanged(info: ActiveMediaSessionInfo?) {
        val client = clientMessenger ?: return
        try {
            val msg = Message.obtain(null, MSG_SESSION_CHANGED).apply {
                data = Bundle().apply {
                    if (info != null) {
                        putBoolean("hasSession", true)
                        putBundle("session", sessionToBundle(info))
                    } else {
                        putBoolean("hasSession", false)
                    }
                }
            }
            client.send(msg)
        } catch (ignored: RemoteException) {
            clientMessenger = null
        }
    }

    private fun notifyClientLogAdded(entry: LogEntry) {
        val client = clientMessenger ?: return
        try {
            val msg = Message.obtain(null, MSG_LOG_ADDED).apply {
                data = Bundle().apply {
                    putBundle("log", logToBundle(entry))
                }
            }
            client.send(msg)
        } catch (ignored: RemoteException) {
            clientMessenger = null
        }
    }

    // ==========================================
    // 2. 主进程客户端 (运行于主进程 / FahrmonyPlugin)
    // ==========================================
    private var remoteServiceMessenger: Messenger? = null
    private var isBound = false
    private val cachedSessions = CopyOnWriteArrayList<ActiveMediaSessionInfo>()
    private var isCarConnectedCache = false
    private var onSessionChangedCallback: ((ActiveMediaSessionInfo?) -> Unit)? = null

    private val clientMessengerInstance = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_SYNC_STATE -> {
                    val bundle = msg.data
                    if (bundle.containsKey("isCarConnected")) {
                        isCarConnectedCache = bundle.getBoolean("isCarConnected", false)
                    }
                    val sessionBundles = bundle.getParcelableArrayList<Bundle>("sessions")
                    if (sessionBundles != null) {
                        cachedSessions.clear()
                        for (sb in sessionBundles) {
                            cachedSessions.add(bundleToSession(sb))
                        }
                    }
                }
                MSG_SESSION_CHANGED -> {
                    val bundle = msg.data
                    val hasSession = bundle.getBoolean("hasSession", false)
                    if (hasSession) {
                        val sessionBundle = bundle.getBundle("session")
                        if (sessionBundle != null) {
                            val session = bundleToSession(sessionBundle)
                            val idx = cachedSessions.indexOfFirst { it.packageName == session.packageName }
                            if (idx >= 0) {
                                cachedSessions[idx] = session
                            } else {
                                cachedSessions.add(0, session)
                            }
                            onSessionChangedCallback?.invoke(session)
                        }
                    } else {
                        onSessionChangedCallback?.invoke(null)
                    }
                }
                MSG_LOG_ADDED -> {
                    val logBundle = msg.data.getBundle("log")
                    if (logBundle != null) {
                        val entry = bundleToLog(logBundle)
                        FahrmonyLogBuffer.addEntry(entry)
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    })

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteServiceMessenger = Messenger(service)
            isBound = true
            try {
                val registerMsg = Message.obtain(null, MSG_REGISTER_CLIENT).apply {
                    replyTo = clientMessengerInstance
                }
                remoteServiceMessenger?.send(registerMsg)
            } catch (ignored: Exception) {}
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remoteServiceMessenger = null
            isBound = false
        }
    }

    fun initClient(context: Context, onSessionChanged: ((ActiveMediaSessionInfo?) -> Unit)? = null) {
        onSessionChangedCallback = onSessionChanged
        val intent = Intent(context, FahrmonyForegroundService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun sendMediaCommand(action: String, position: Long = 0L) {
        val messenger = remoteServiceMessenger ?: return
        try {
            val msg = Message.obtain(null, MSG_MEDIA_COMMAND).apply {
                data = Bundle().apply {
                    putString("action", action)
                    putLong("position", position)
                }
            }
            messenger.send(msg)
        } catch (ignored: RemoteException) {}
    }

    fun clearLogs() {
        FahrmonyLogBuffer.clear()
        val messenger = remoteServiceMessenger ?: return
        try {
            val msg = Message.obtain(null, MSG_CLEAR_LOGS)
            messenger.send(msg)
        } catch (ignored: RemoteException) {}
    }

    fun notifyConfigChanged(configJson: String? = null) {
        val messenger = remoteServiceMessenger ?: return
        try {
            val msg = Message.obtain(null, MSG_UPDATE_CONFIG).apply {
                if (!configJson.isNullOrEmpty()) {
                    data = Bundle().apply {
                        putString("configJson", configJson)
                    }
                }
            }
            messenger.send(msg)
        } catch (ignored: RemoteException) {}
    }

    fun getCachedSessions(): List<ActiveMediaSessionInfo> {
        return cachedSessions.toList()
    }

    fun isCarConnected(): Boolean {
        return isCarConnectedCache
    }

    // ==========================================
    // 3. 数据契约 Bundle 序列化映射
    // ==========================================
    private fun sessionToBundle(info: ActiveMediaSessionInfo): Bundle {
        return Bundle().apply {
            putString("packageName", info.packageName)
            putString("appName", info.appName)
            putString("title", info.title)
            putString("artist", info.artist)
            putString("album", info.album)
            putBoolean("isPlaying", info.isPlaying)
            putLong("duration", info.duration)
            putLong("position", info.position)
        }
    }

    private fun bundleToSession(bundle: Bundle): ActiveMediaSessionInfo {
        return ActiveMediaSessionInfo(
            packageName = bundle.getString("packageName") ?: "",
            appName = bundle.getString("appName") ?: "",
            title = bundle.getString("title") ?: "",
            artist = bundle.getString("artist") ?: "",
            album = bundle.getString("album") ?: "",
            isPlaying = bundle.getBoolean("isPlaying", false),
            duration = bundle.getLong("duration", 0L),
            position = bundle.getLong("position", 0L)
        )
    }

    private fun logToBundle(entry: LogEntry): Bundle {
        return Bundle().apply {
            putString("id", entry.id)
            putLong("timestamp", entry.timestamp)
            putString("type", entry.type)
            putString("tag", entry.tag)
            putString("title", entry.title)
            putString("content", entry.content)
            putString("rawExtras", entry.rawExtras)
        }
    }

    private fun bundleToLog(bundle: Bundle): LogEntry {
        return LogEntry(
            id = bundle.getString("id") ?: "",
            timestamp = bundle.getLong("timestamp", System.currentTimeMillis()),
            type = bundle.getString("type") ?: "SYSTEM",
            tag = bundle.getString("tag") ?: "",
            title = bundle.getString("title") ?: "",
            content = bundle.getString("content") ?: "",
            rawExtras = bundle.getString("rawExtras")
        )
    }
}
