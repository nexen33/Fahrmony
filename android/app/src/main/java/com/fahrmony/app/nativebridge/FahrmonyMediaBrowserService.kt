package com.fahrmony.app.nativebridge

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver

/**
 * Android Auto 原生全屏音频沉浸服务 (MediaBrowserServiceCompat)
 *
 * 对标并全面超越第三方糯米播放器：
 * 1. 彻底摒弃脆弱的 LocalBroadcastManager，采用单例内聚同步总线；
 * 2. 补齐上一曲 (Skip Previous) 与进度条拖拽 (Seek To) 双向协议；
 * 3. 实时镜像高清专辑封面 Bitmap，激活 Android Auto 原生全屏大图与 Coolwalk 分屏小组件；
 * 4. 具备无会话时的冷启动自愈能力 (自动唤醒用户预设的播放源)。
 */
class FahrmonyMediaBrowserService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat

    companion object {
        const val ROOT_ID = "__FAHRMONY_MEDIA_ROOT__"
        var instance: FahrmonyMediaBrowserService? = null
            private set
    }

    private var lastNotifiedTitle: String? = null
    private var lastNotifyTimestamp = 0L

    override fun onCreate() {
        super.onCreate()
        // 显式升级为 Started Service，脱离纯 Bind 脆弱生命周期，跨越车机断连全程存活
        try {
            startService(Intent(this, FahrmonyMediaBrowserService::class.java))
        } catch (ignored: Exception) {}
        instance = this

        // 确保冷启动时第一时间初始化媒体总线并纳管系统活跃会话
        FahrmonyMediaManager.init(applicationContext)

        mediaSession = MediaSessionCompat(this, "FahrmonyMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            val sessionIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                PendingIntent.getActivity(
                    this@FahrmonyMediaBrowserService,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            setSessionActivity(sessionIntent)
            setQueueTitle(FahrmonyCarI18n.getAppTitle(this@FahrmonyMediaBrowserService))

            // Google 官方标准：声明媒体按键接收器，明确告知 Android Auto 由 Fahrmony 接管车机硬件按键
            val mbrIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
                this@FahrmonyMediaBrowserService,
                PlaybackStateCompat.ACTION_PLAY_PAUSE
            )
            setMediaButtonReceiver(mbrIntent)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    FahrmonyMediaManager.play(applicationContext)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId != null && mediaId.startsWith("source_switch:")) {
                        val pkg = mediaId.removePrefix("source_switch:")
                        FahrmonyMediaManager.switchToPackage(applicationContext, pkg)
                    } else {
                        FahrmonyMediaManager.play(applicationContext)
                    }
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    FahrmonyMediaManager.play(applicationContext)
                }

                override fun onPrepare() {
                    FahrmonyMediaManager.play(applicationContext)
                }

                override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId != null && mediaId.startsWith("source_switch:")) {
                        val pkg = mediaId.removePrefix("source_switch:")
                        FahrmonyMediaManager.switchToPackage(applicationContext, pkg)
                    } else {
                        FahrmonyMediaManager.play(applicationContext)
                    }
                }

                override fun onPause() {
                    FahrmonyMediaManager.sendCommand("pause")
                }

                override fun onSkipToNext() {
                    FahrmonyMediaManager.sendCommand("skip_next")
                }

                override fun onSkipToPrevious() {
                    FahrmonyMediaManager.sendCommand("skip_previous")
                }

                override fun onSeekTo(pos: Long) {
                    FahrmonyMediaManager.seekTo(pos)
                }

                override fun onSkipToQueueItem(id: Long) {
                    FahrmonyMediaManager.skipToQueueItem(id)
                }

                override fun onSetRepeatMode(repeatMode: Int) {
                    FahrmonyMediaManager.setRepeatMode(repeatMode)
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == FahrmonyMediaManager.ACTION_CAR_REPEAT) {
                        FahrmonyMediaManager.toggleRepeatMode()
                    }
                }

                override fun onStop() {
                    FahrmonyMediaManager.sendCommand("pause")
                }
            })

            setRepeatMode(FahrmonyMediaManager.getActiveRepeatMode())
            isActive = true
        }

        sessionToken = mediaSession.sessionToken

        // 注册到单例媒体管理器，立即执行一次状态镜像
        FahrmonyMediaManager.registerBrowserService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // 车机断连拔线，立即向底层播放器下发双脉冲暂停指令，杜绝冷启动首次断连时音频通道重建造成的手机外放漏音
        FahrmonyMediaManager.onCarDisconnected()
        // 返回 true 允许车机断开后未来重连时回调 onRebind，避免仅首次绑定才触发连接
        return true
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        // 车机重连瞬间，立即执行主导权确认与推流
        FahrmonyMediaManager.onCarConnected(applicationContext)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        FahrmonyMediaManager.onCarConnected(applicationContext)
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        val activeInfo = FahrmonyMediaManager.getActiveSessionInfo()

        // 1. 当前曲目项 (PLAYABLE)
        if (activeInfo != null && activeInfo.title.isNotBlank()) {
            val currentDesc = MediaDescriptionCompat.Builder()
                .setMediaId("action:current_track")
                .setTitle(activeInfo.title)
                .setSubtitle("${activeInfo.appName} · ${activeInfo.artist.ifBlank { FahrmonyCarI18n.getNowPlayingDefault(this@FahrmonyMediaBrowserService) }}")
                .setDescription(FahrmonyCarI18n.getReadySubtitle(this@FahrmonyMediaBrowserService))
                .build()
            items.add(
                MediaBrowserCompat.MediaItem(
                    currentDesc,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        } else {
            val welcomeDesc = MediaDescriptionCompat.Builder()
                .setMediaId("action:current_track")
                .setTitle(FahrmonyCarI18n.getAppTitle(this@FahrmonyMediaBrowserService))
                .setSubtitle(FahrmonyCarI18n.getReadySubtitle(this@FahrmonyMediaBrowserService))
                .build()
            items.add(
                MediaBrowserCompat.MediaItem(
                    welcomeDesc,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }

        // 2. 媒体源快捷切换/唤醒项 (PLAYABLE)
        val addedNames = mutableSetOf<String>()
        for ((pkg, name) in FahrmonyMediaManager.KNOWN_PACKAGES) {
            if (addedNames.contains(name)) continue
            addedNames.add(name)
            
            val isCurrent = (activeInfo?.packageName == pkg)
            val sourceDesc = MediaDescriptionCompat.Builder()
                .setMediaId("source_switch:$pkg")
                .setTitle(name)
                .setSubtitle(if (isCurrent) FahrmonyCarI18n.getActiveSourceSubtitle(this@FahrmonyMediaBrowserService) else FahrmonyCarI18n.getSwitchSourceSubtitle(this@FahrmonyMediaBrowserService))
                .build()
            items.add(
                MediaBrowserCompat.MediaItem(
                    sourceDesc,
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                )
            )
        }

        result.sendResult(items)
    }

    /**
     * 由 FahrmonyMediaManager 内存单例直接调用，毫无延迟与丢包风险
     */
    fun syncMetadata(metadata: MediaMetadataCompat) {
        if (::mediaSession.isInitialized) {
            mediaSession.setMetadata(metadata)
            // 智能队列适配：若底层播放器开放了真实播放列表，完整透传并点亮车机歌单；否则传 null 彻底隐藏右上角队列按键，保持极致清爽
            val realQueue = FahrmonyMediaManager.getActiveQueue()
            if (!realQueue.isNullOrEmpty()) {
                mediaSession.setQueue(realQueue)
                val queueTitle = FahrmonyMediaManager.getActiveQueueTitle()
                if (!queueTitle.isNullOrBlank()) {
                    mediaSession.setQueueTitle(queueTitle)
                }
            } else {
                mediaSession.setQueue(null)
            }
            checkAndNotifyChildrenChanged()
        }
    }

    fun syncPlaybackState(state: PlaybackStateCompat) {
        if (::mediaSession.isInitialized) {
            mediaSession.setPlaybackState(state)
        }
    }

    fun syncRepeatMode(repeatMode: Int) {
        if (::mediaSession.isInitialized) {
            mediaSession.setRepeatMode(repeatMode)
        }
    }

    private fun checkAndNotifyChildrenChanged() {
        val activeInfo = FahrmonyMediaManager.getActiveSessionInfo()
        val currentTitle = activeInfo?.title ?: ""
        val now = System.currentTimeMillis()
        if (currentTitle != lastNotifiedTitle && (now - lastNotifyTimestamp >= 1000L)) {
            lastNotifiedTitle = currentTitle
            lastNotifyTimestamp = now
            try {
                notifyChildrenChanged(ROOT_ID)
            } catch (ignored: Exception) {}
        }
    }

    override fun onDestroy() {
        FahrmonyMediaManager.unregisterBrowserService(this)
        if (instance == this) {
            instance = null
        }
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }
}
