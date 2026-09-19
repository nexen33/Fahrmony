package com.fahrmony.app.nativebridge

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import androidx.core.app.NotificationCompat
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.service.notification.NotificationListenerService
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import com.fahrmony.app.nativebridge.lyrics.UnifiedLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.sync.LyricSyncEngine
import com.fahrmony.app.nativebridge.lyrics.sync.LyricSyncListener
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class ActiveMediaSessionInfo(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val duration: Long,
    val position: Long,
    val artworkData: String? = null,
    val hasLyrics: Boolean = false
)

data class DiscoveredMediaSessionInfo(
    val packageName: String,
    val appName: String,
    val iconBase64: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean
)

object FahrmonyMediaManager {
    val KNOWN_PACKAGES = linkedMapOf(
        "com.tencent.qqmusic" to "QQ音乐",
        "com.netease.cloudmusic" to "网易云音乐",
        "com.luna.music" to "汽水音乐",
        "cn.wenyu.bodian" to "波点音乐",
        "kugou.service" to "酷狗音乐",
        "com.kugou.android" to "酷狗音乐",
        "cn.kuwo.player" to "酷我音乐",
        "com.ximalaya.ting.android" to "喜马拉雅",
        "app.podcast.cosmos" to "小宇宙",
    )

    // 智能黑名单：屏蔽中英文主流非音乐/即时通讯/社交协作/地图导航/电商生活/金融/工具与系统核心应用
    // 注：抖音/TikTok、Bilibili、微博 (Weibo) 明确放行，不在此黑名单中
    val SMART_MEDIA_BLACKLIST = hashSetOf(
        // 即时通讯与社交协作
        "com.tencent.mm",
        "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite", "com.tencent.minihd.qq",
        "com.ss.android.lark", "com.ss.android.lark.kami",
        "com.alibaba.android.rimet",
        "com.whatsapp", "com.whatsapp.w4b",
        "org.telegram.messenger", "org.thunderdog.challegram",
        "org.thoughtcrime.securesms",
        "jp.naver.line.android",
        "com.facebook.orca", "com.facebook.mlite",
        "com.discord",
        "com.Slack",
        "com.microsoft.teams",
        "com.skype.raider",
        "com.xingin.xhs",
        "com.zhihu.android",
        "com.baidu.tieba",
        "com.facebook.katana",
        "com.instagram.android",
        "com.instagram.barcelona",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        // 地图导航与出行
        "com.autonavi.minimap",
        "com.baidu.BaiduMap",
        "com.tencent.map",
        "com.google.android.apps.maps",
        "com.waze",
        // 电商购物与生活服务
        "com.taobao.taobao", "com.taobao.litetao",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.sankuai.meituan", "com.sankuai.meituan.takeoutnew",
        "me.ele",
        "com.dianping.v1",
        "com.taobao.idlefish",
        "com.amazon.mShop.android.shopping",
        "com.ebay.mobile",
        "com.alibaba.aliexpresshd",
        // 支付金融与银行
        "com.eg.android.AlipayGphone",
        "com.unionpay",
        "com.paypal.android.p2pmobile",
        "com.google.android.apps.walletnfcrel",
        // 办公工具与浏览器
        "cn.wps.moffice_eng",
        "com.baidu.netdisk",
        "notion.id",
        "com.quark.browser",
        "com.UCMobile",
        "com.android.chrome",
        "com.microsoft.emmx",
        "org.mozilla.firefox",
        // 系统核心与宿主
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.settings",
        "com.google.android.gms",
        "com.android.vending",
        "com.fahrmony.app"
    )

    // 常见音频/视频/播客应用智能兜底映射表 (覆盖 30+ 常见音源)
    val COMMON_AUDIO_APP_NAMES = mapOf(
        // 视频与有声内容
        "tv.danmaku.bili" to "哔哩哔哩",
        "com.bilibili.app.in" to "哔哩哔哩(国际版)",
        "com.bilibili.comic" to "哔哩哔哩漫画",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.ss.android.ugc.aweme.lite" to "抖音极速版",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.sina.weibo" to "微博",
        "com.sina.weibog3" to "微博轻享版",
        "com.weico.international" to "微博国际版",
        // 小众与第三方本地/网络播放器
        "cn.toside.music.mobile" to "洛雪音乐",
        "com.kugou.android.lite" to "酷狗概念版",
        "com.maxmpz.audioplayer" to "Poweramp",
        "com.maxmpz.audioplayer.unlock" to "Poweramp",
        "com.foobar2000.foobar2000" to "foobar2000",
        "com.salt.music" to "椒盐音乐",
        "remix.myplayer" to "倒带音乐",
        "com.tencent.blackpearl" to "MOO音乐",
        "cmccwm.mobilemusic" to "咪咕音乐",
        "com.netease.cloudmusic.lite" to "网易云极速版",
        // 国际主流流媒体与播客
        "com.apple.android.music" to "Apple Music",
        "com.spotify.music" to "Spotify",
        "com.spotify.lite" to "Spotify Lite",
        "com.google.android.apps.youtube.music" to "YouTube Music",
        "com.soundcloud.android" to "SoundCloud",
        "com.aspiro.tidal" to "TIDAL",
        "deezer.android.app" to "Deezer",
        "com.audible.application" to "Audible",
        "fm.castbox.audiobook.radio.podcast" to "Castbox",
        "com.biphares.pocketcasts" to "Pocket Casts",
        "tunein.player" to "TuneIn Radio",
        "com.pandora.android" to "Pandora",
        "com.amazon.mp3" to "Amazon Music"
    )

    fun isSupportedPackage(context: Context?, packageName: String): Boolean {
        if (packageName in KNOWN_PACKAGES.keys) return true
        val ctx = context ?: appContext
        if (ctx != null) {
            val customPkg = FahrmonyConfig.getCustomPlayerPackage(ctx)
            if (customPkg.isNotBlank() && packageName == customPkg) return true
        }
        return false
    }

    fun getAppNameForPackage(context: Context?, packageName: String): String {
        val known = KNOWN_PACKAGES[packageName]
        if (known != null) return known
        val ctx = context ?: appContext
        if (ctx != null) {
            val customPkg = FahrmonyConfig.getCustomPlayerPackage(ctx)
            if (customPkg.isNotBlank() && packageName == customPkg) {
                val customName = FahrmonyConfig.getCustomPlayerName(ctx)
                if (customName.isNotBlank()) return customName
            }
            try {
                val pm = ctx.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                if (label.isNotBlank() && label != packageName) {
                    return label
                }
            } catch (ignored: Exception) {}
        }
        val commonName = COMMON_AUDIO_APP_NAMES[packageName]
        if (!commonName.isNullOrBlank()) return commonName
        return packageName
    }

    fun getAppIconBase64(context: Context, packageName: String): String {
        try {
            val pm = context.packageManager
            var drawable = try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
            if (drawable == null) {
                try {
                    val intent = pm.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        val actInfo = intent.resolveActivityInfo(pm, 0)
                        if (actInfo != null) {
                            drawable = actInfo.loadIcon(pm)
                        }
                    }
                } catch (ignored: Exception) {}
            }
            if (drawable == null) return ""

            val bmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
                drawable.bitmap
            } else {
                val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            val scaled = if (bmp.width > 128 || bmp.height > 128) {
                Bitmap.createScaledBitmap(bmp, 128, 128, true)
            } else {
                bmp
            }
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val bytes = baos.toByteArray()
            return "data:image/png;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            return ""
        }
    }

    const val ACTION_CAR_REPEAT = "com.fahrmony.app.ACTION_CAR_REPEAT"
    const val ACTION_TOGGLE_LYRICS = "com.fahrmony.app.ACTION_TOGGLE_LYRICS"
    private val lyricSyncEngine = LyricSyncEngine()

    sealed class RepeatProbeResult {
        object Unsupported : RepeatProbeResult()
        object Standard : RepeatProbeResult()
        data class Custom(val actionName: String, val displayName: String) : RepeatProbeResult()
    }

    enum class CustomRepeatState(val title: String, val iconRes: Int) {
        REPEAT_ALL("列表循环", com.fahrmony.app.R.drawable.ic_car_repeat),
        SHUFFLE("随机播放", com.fahrmony.app.R.drawable.ic_car_shuffle),
        REPEAT_ONE("单曲循环", com.fahrmony.app.R.drawable.ic_car_repeat_one);

        fun next(): CustomRepeatState {
            return when (this) {
                REPEAT_ALL -> SHUFFLE
                SHUFFLE -> REPEAT_ONE
                REPEAT_ONE -> REPEAT_ALL
            }
        }
    }

    private var customRepeatState = CustomRepeatState.REPEAT_ALL

    fun probeRepeatCapability(controller: MediaControllerCompat?): RepeatProbeResult {
        if (controller == null) return RepeatProbeResult.Unsupported
        val state = controller.playbackState ?: return RepeatProbeResult.Unsupported

        // 1. 优先检查官方标准位 (如 Spotify, Apple Music 等)
        val actions = state.actions
        if ((actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L) {
            return RepeatProbeResult.Standard
        }

        // 2. 检查国内主流 App 的 CustomAction (如网易云音乐的 '播放模式')
        val customActions = state.customActions
        if (!customActions.isNullOrEmpty()) {
            for (ca in customActions) {
                val act = ca.action ?: ""
                val name = ca.name?.toString() ?: ""
                if (act.contains("模式") || act.contains("repeat", ignoreCase = true) || act.contains("loop", ignoreCase = true) ||
                    name.contains("模式") || name.contains("repeat", ignoreCase = true) || name.contains("loop", ignoreCase = true)) {
                    val displayName = name.ifBlank { "播放模式" }
                    return RepeatProbeResult.Custom(act, displayName)
                }
            }
        }
        return RepeatProbeResult.Unsupported
    }

    private var activeControllerCompat: MediaControllerCompat? = null
    // 线程安全集合：支持 NotificationListener 跨进程 Binder 线程写入与 Capacitor JS 轮询并发安全
    private val allControllersCompat = CopyOnWriteArrayList<MediaControllerCompat>()
    private val callbackMap = ConcurrentHashMap<MediaControllerCompat, MediaControllerCompat.Callback>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var browserServiceRef: WeakReference<FahrmonyMediaBrowserService>? = null
    private var isInitialized = false
    private var lastAutoPlayTimestamp = 0L
    private var stickyActivePackage: String? = null
    private var lastActiveTimestamp = 0L
    private var appContext: Context? = null

    // 监听与更新回调
    var onSessionUpdated: ((ActiveMediaSessionInfo?) -> Unit)? = null
    var onActiveSessionChangedListener: ((ActiveMediaSessionInfo?) -> Unit)? = null
    private var pendingPauseRunnable: Runnable? = null
    private var skipProtectionUntil = 0L
    private var skipProtectionState = PlaybackStateCompat.STATE_PLAYING
    private var userRequestedPauseUntil = 0L
    private var lastCarConnectedTimestamp = 0L
    private var lastSyncedTrackKey: String? = null
    private var defaultArtworkBitmap: Bitmap? = null
    private var currentTrackArtworkBitmap: Bitmap? = null
    // 毫秒级冷启动耗时可观测性探针
    private var lastWakeUpTimestamp = 0L
    private var lastWakeUpPackage: String? = null
    // 事件驱动冷启动自动起播标记 (带 8000ms 有效窗口)
    private var pendingAutoPlayPackage: String? = null
    private var pendingAutoPlayDeadline = 0L
    // 冷启动异常与死锁容错自愈重试机制 (针对 STATE_ERROR 与 STATE_STOPPED)
    private var lastAutoPlayAttemptTimestamp = 0L
    private var autoPlayTargetPackage: String? = null
    private var autoPlayRetryCount = 0
    private var autoPlayRetryRunnable: Runnable? = null
    // 车机握手期独占播放态保护锁 (防止连车前几百毫秒误推暂停导致宿主切到第三方应用)
    private var handshakeProtectionUntil = 0L
    // 用户主动起播态保护锁 (防止第三方缓冲期误推暂停导致车机视图切走)
    private var playProtectionUntil = 0L
    // 不可播异常曲目提醒文案
    private var invalidTrackNotice: String? = null

    data class CachedValidTrack(
        val title: String,
        val artist: String,
        val album: String,
        val metadata: MediaMetadataCompat?
    )
    private val lastValidTrackMap = ConcurrentHashMap<String, CachedValidTrack>()

    data class GradientPalette(
        val topColor: Int,
        val bottomColor: Int,
        val name: String
    )

    private val PALETTES = arrayOf(
        // 1. 经典天蓝流光 (Sky Cyan)
        GradientPalette(android.graphics.Color.parseColor("#38BDF8"), android.graphics.Color.parseColor("#0284C7"), "Sky"),
        // 2. 北欧薄荷清风 (Nordic Mint)
        GradientPalette(android.graphics.Color.parseColor("#34D399"), android.graphics.Color.parseColor("#059669"), "Mint"),
        // 3. 电光洋兰紫 (Electric Orchid - 高彩度高明度避开Monet黑键陷阱)
        GradientPalette(android.graphics.Color.parseColor("#E879F9"), android.graphics.Color.parseColor("#A855F7"), "Orchid"),
        // 4. 暖阳珊瑚流光 (Warm Coral)
        GradientPalette(android.graphics.Color.parseColor("#FB923C"), android.graphics.Color.parseColor("#EA580C"), "Coral"),
        // 5. 晨曦莓粉微光 (Rose Berry)
        GradientPalette(android.graphics.Color.parseColor("#F472B6"), android.graphics.Color.parseColor("#DB2777"), "Rose")
    )

    private var currentPaletteIndex = 0
    private var lastGeneratedArtwork: Bitmap? = null
    private var lastArtworkTrackKey: String? = null
    private var defaultIdleArtwork: Bitmap? = null

    private fun createGradientBitmap(palette: GradientPalette): Bitmap {
        val size = 240
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        val canvas = android.graphics.Canvas(bmp)
        val shader = android.graphics.LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            palette.topColor,
            palette.bottomColor,
            android.graphics.Shader.TileMode.CLAMP
        )
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG).apply {
            this.shader = shader
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bmp
    }

    fun getIdleArtwork(): Bitmap {
        if (defaultIdleArtwork != null && !defaultIdleArtwork!!.isRecycled) {
            return defaultIdleArtwork!!
        }
        val bmp = createGradientBitmap(PALETTES[0])
        defaultIdleArtwork = bmp
        return bmp
    }

    fun getArtworkForTrack(context: Context, trackKey: String, isRealTrack: Boolean = true): Bitmap {
        // 占位、等待或无真实歌曲过渡态，恒定使用固定默认流光，绝对禁止跳色
        if (!isRealTrack || trackKey == "__null_session__" || trackKey == "__default_idle__") {
            return getIdleArtwork()
        }

        if (trackKey == lastArtworkTrackKey && lastGeneratedArtwork != null && !lastGeneratedArtwork!!.isRecycled) {
            return lastGeneratedArtwork!!
        }

        // 仅在真实切歌 (上下曲不同) 时智能色阶交替，保证相邻两曲颜色绝对不重样
        val hash = (trackKey.hashCode() and 0x7FFFFFFF)
        var nextIdx = (currentPaletteIndex + 1 + (hash % (PALETTES.size - 1))) % PALETTES.size
        if (nextIdx == currentPaletteIndex) {
            nextIdx = (currentPaletteIndex + 1) % PALETTES.size
        }
        currentPaletteIndex = nextIdx
        val palette = PALETTES[nextIdx]

        val bmp = createGradientBitmap(palette)
        lastArtworkTrackKey = trackKey
        lastGeneratedArtwork = bmp
        return bmp
    }

    fun getOrCreateDefaultArtwork(context: Context): Bitmap {
        return getIdleArtwork()
    }

    data class FallbackMeta(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val artworkBmp: Bitmap? = null
    )

    fun isPackageMatch(pkgA: String?, pkgB: String?): Boolean {
        if (pkgA.isNullOrBlank() || pkgB.isNullOrBlank()) return false
        if (pkgA.equals(pkgB, ignoreCase = true)) return true
        // 酷狗音乐双包名 (kugou.service / com.kugou.android / com.kugou.android.lite) 归一化兜底匹配
        val kugouAliases = setOf("kugou.service", "com.kugou.android", "com.kugou.android.lite")
        if (pkgA in kugouAliases && pkgB in kugouAliases) {
            return true
        }
        return false
    }

    private val fallbackMetaMap = ConcurrentHashMap<String, FallbackMeta>()

    private var isNoisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                FahrmonyLogBuffer.addLog("AUDIO_NOISY", "音频物理断连", "捕获到ACTION_AUDIO_BECOMING_NOISY", "执行手机端物理静音与暂停熔断")
                onCarDisconnected()
            }
        }
    }

    private fun registerNoisyReceiver(context: Context) {
        if (isNoisyReceiverRegistered) return
        try {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    noisyReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(noisyReceiver, filter)
            }
            isNoisyReceiverRegistered = true
            FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "Noisy广播监听就绪", "ACTION_AUDIO_BECOMING_NOISY")
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "Noisy广播注册异常", "${e.message}", "")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        registerNoisyReceiver(context)

        lyricSyncEngine.setOffsetMs(FahrmonyConfig.getLyricsOffsetMs(context))
        lyricSyncEngine.setEnabled(FahrmonyConfig.isLyricsEnabled(context))
        lyricSyncEngine.setListener(object : LyricSyncListener {
            override fun onLyricLineChanged(
                currentLine: String,
                nextLine: String?,
                entryIndex: Int,
                totalEntries: Int
            ) {
                pushLyricMetadata(currentLine, nextLine)
            }

            override fun onLyricsStateReset() {
                val service = browserServiceRef?.get() ?: return
                val controller = activeControllerCompat ?: return
                val meta = controller.metadata ?: lastValidTrackMap[controller.packageName]?.metadata
                if (meta != null) {
                    val rawTitle = meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                        ?: lastValidTrackMap[controller.packageName]?.title ?: ""
                    val rawArtist = meta.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                        ?: lastValidTrackMap[controller.packageName]?.artist ?: ""
                    val trackKey = "${controller.packageName}|$rawTitle|$rawArtist"
                    service.syncMetadata(sanitizeMetadata(meta, trackKey, isRealTrack = true))
                }
            }
        })

        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return

        val componentName = ComponentName(context, FahrmonyNotificationListener::class.java)

        try {
            if (!isInitialized) {
                sessionManager.addOnActiveSessionsChangedListener(
                    { controllers ->
                        updateControllers(context, controllers)
                    },
                    componentName,
                    mainHandler
                )
                isInitialized = true
            }
            val currentControllers = sessionManager.getActiveSessions(componentName)
            updateControllers(context, currentControllers)
        } catch (e: SecurityException) {
            FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "等待授权", "NotificationListener 授权后将自动激活会话监听")
            // 尝试主动请求系统重新绑定服务 (Android 7.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !FahrmonyNotificationListener.isConnected) {
                try {
                    NotificationListenerService.requestRebind(componentName)
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun refresh(context: Context) {
        appContext = context.applicationContext
        if (!isInitialized) {
            init(context)
        }
        lyricSyncEngine.setOffsetMs(FahrmonyConfig.getLyricsOffsetMs(context))
        lyricSyncEngine.setEnabled(FahrmonyConfig.isLyricsEnabled(context))
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            ?: return
        val componentName = ComponentName(context, FahrmonyNotificationListener::class.java)
        try {
            val list = sessionManager.getActiveSessions(componentName)
            updateControllers(context, list)
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !FahrmonyNotificationListener.isConnected) {
                try {
                    NotificationListenerService.requestRebind(componentName)
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 直接通过状态栏通知中携带的 android.mediaSession Token 构建 MediaControllerCompat (对齐糯米播放器核心能力)
     * 即使系统 getActiveSessions 暂未返回，也能实现秒级纳管
     */
    fun attachToken(context: Context, packageName: String, token: android.media.session.MediaSession.Token, extras: android.os.Bundle?, notificationArtwork: Bitmap? = null) {
        // 绝不纳管自身会话，彻底根除自环死锁与手机端按键失效
        if (packageName == context.packageName) return
        // 校验是否属于已知受支持的官方媒体应用或用户自定义添加的播放器
        if (!isSupportedPackage(context, packageName)) return
        try {
            appContext = context.applicationContext
            // 提取通知中的备用元数据 (包含真实状态栏 Icon 解码出的 Bitmap)
            if (extras != null || notificationArtwork != null) {
                val nTitle = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
                val nArtist = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
                val nSubText = extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
                val prev = fallbackMetaMap[packageName]
                val finalArt = notificationArtwork ?: prev?.artworkBmp
                if (nTitle.isNotBlank() || nArtist.isNotBlank() || finalArt != null) {
                    fallbackMetaMap[packageName] = FallbackMeta(
                        nTitle.ifBlank { prev?.title ?: "" },
                        nArtist.ifBlank { prev?.artist ?: "" },
                        nSubText.ifBlank { prev?.album ?: "" },
                        finalArt
                    )
                }
                android.util.Log.i("Fahrmony_CUSTOM", "[TOKEN_ATTACH] pkg: $packageName, title: '$nTitle', artist: '$nArtist'")
            }

            val compatToken = MediaSessionCompat.Token.fromToken(token) ?: return

            // 检查现有列表是否已存在相同 Token
            val existing = allControllersCompat.firstOrNull { it.sessionToken == compatToken }
            if (existing != null) {
                if (existing.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                    stickyActivePackage = packageName
                    lastActiveTimestamp = System.currentTimeMillis()
                    activeControllerCompat = existing
                }
                notifyInfo()
                return
            }

            // 同源去重与状态更新：若已存在同一 packageName 的陈旧控制器，注销回调并剔除，杜绝重复展示
            val samePkgControllers = allControllersCompat.filter { it.packageName == packageName }
            for (old in samePkgControllers) {
                val cb = callbackMap.remove(old)
                if (cb != null) {
                    try { old.unregisterCallback(cb) } catch (ignored: Exception) {}
                }
                allControllersCompat.remove(old)
            }

            val newCtrl = MediaControllerCompat(context, compatToken)
            allControllersCompat.add(newCtrl)
            registerSingleControllerCallback(newCtrl)

            if (packageName == lastWakeUpPackage && lastWakeUpTimestamp > 0L) {
                val elapsed = System.currentTimeMillis() - lastWakeUpTimestamp
                FahrmonyLogBuffer.addLog(
                    "PROBE_READY",
                    "通知Token捕获就绪",
                    "目标: $packageName, 从冷拉起到收到状态栏会话Token总耗时: ${elapsed}ms",
                    packageName
                )
            }

            // 若处于播放状态或当前无活跃源，立即提权为主控制器并粘性锁定
            if (activeControllerCompat == null || newCtrl.playbackState?.state == PlaybackStateCompat.STATE_PLAYING || stickyActivePackage == packageName) {
                activeControllerCompat = newCtrl
                stickyActivePackage = packageName
                lastActiveTimestamp = System.currentTimeMillis()
            }

            checkPendingAutoPlay(newCtrl)
            notifyInfo()
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "Attach异常", "${e.message}")
        }
    }

    fun updateNotificationMeta(packageName: String, extras: android.os.Bundle?, artwork: Bitmap? = null) {
        if (extras != null || artwork != null) {
            val nTitle = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
            val nArtist = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
            val nSubText = extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
            val prev = fallbackMetaMap[packageName]
            val finalTitle = nTitle.ifBlank { prev?.title ?: "" }
            val finalArtist = nArtist.ifBlank { prev?.artist ?: "" }
            val finalAlbum = nSubText.ifBlank { prev?.album ?: "" }
            val finalArt = artwork ?: prev?.artworkBmp
            fallbackMetaMap[packageName] = FallbackMeta(finalTitle, finalArtist, finalAlbum, finalArt)
            android.util.Log.i("Fahrmony_CUSTOM", "[NOTIFY_META] pkg: $packageName, title: '$finalTitle', artist: '$finalArtist'")
            notifyInfo()
        }
    }

    private val customPlayerStickyCache = java.util.concurrent.ConcurrentHashMap<String, CachedValidTrack>()

    fun onDefaultPlayerChanged(newPackage: String) {
        stickyActivePackage = newPackage
        val match = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, newPackage) }
        activeControllerCompat = match
        notifyInfo(immediate = true)
    }

    /**
     * 自定义音源配置变更时即刻执行系统探针扫描，完成跨进程 Binder 热握手，杜绝初次按键被握手消耗
     */
    fun onCustomPlayerConfigChanged(context: Context, customPkg: String, customName: String? = null) {
        if (customPkg.isBlank()) {
            customPlayerStickyCache.clear()
            return
        }
        appContext = context.applicationContext
        try {
            val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val componentName = ComponentName(context, FahrmonyNotificationListener::class.java)
            val activeSessions = sessionManager?.getActiveSessions(componentName)
            if (!activeSessions.isNullOrEmpty()) {
                updateControllers(context, activeSessions)
            }
        } catch (ignored: Exception) {}

        stickyActivePackage = customPkg
        val match = allControllersCompat.firstOrNull { it.packageName == customPkg }
        if (match != null) {
            activeControllerCompat = match
            val info = extractSessionInfo(match)
            if (info.title.isNotBlank()) {
                customPlayerStickyCache[customPkg] = CachedValidTrack(info.title, info.artist, info.album, match.metadata)
            }
        }
        android.util.Log.i("Fahrmony_CUSTOM", "[CONFIG_CHANGED] pkg: $customPkg, name: $customName, matchFound: ${match != null}")
        notifyInfo(immediate = true)
    }

    fun registerBrowserService(service: FahrmonyMediaBrowserService) {
        browserServiceRef = WeakReference(service)
        pushMirrorState()
    }

    fun unregisterBrowserService(service: FahrmonyMediaBrowserService) {
        if (browserServiceRef?.get() == service) {
            browserServiceRef = null
        }
    }

    private fun registerSingleControllerCallback(controller: MediaControllerCompat) {
        val callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (activeControllerCompat == controller) {
                    lyricSyncEngine.updatePlaybackState(state)
                }
                val newState = state?.state ?: PlaybackStateCompat.STATE_NONE
                if (newState == PlaybackStateCompat.STATE_PAUSED) {
                    userRequestedPauseUntil = 0L
                }
                val isSkipProtecting = System.currentTimeMillis() < skipProtectionUntil

                if (isSkipProtecting) {
                    // 切歌保护期内：忽略第三方的中间态抖动，立即同步当前会话，确保车机始终聚焦 Fahrmony
                    notifyInfo(immediate = true)
                    return
                }

                if (newState == PlaybackStateCompat.STATE_PLAYING) {
                    val isDisconnectSuppressing = System.currentTimeMillis() < disconnectSuppressionUntil
                    if (isDisconnectSuppressing) {
                        FahrmonyLogBuffer.addLog(
                            "CAR_DISCONNECT",
                            "断连防抖压制",
                            "捕获断连窗口期前台非预期自发起播，立即二次强制压制暂停",
                            controller.packageName
                        )
                        try {
                            controller.transportControls?.pause()
                        } catch (ignored: Exception) {}
                        optimisticSyncState(PlaybackStateCompat.STATE_PAUSED)
                        return
                    }

                    userRequestedPauseUntil = 0L
                    playProtectionUntil = 0L
                    skipProtectionUntil = 0L
                    invalidTrackNotice = null
                    autoPlayRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    autoPlayRetryRunnable = null
                    lastAutoPlayAttemptTimestamp = 0L
                    autoPlayRetryCount = 0
                    autoPlayTargetPackage = null
                    lastWakeUpPackage = null
                    lastWakeUpTimestamp = 0L
                    // 收到真实播放事件：取消任何挂起的暂停防抖任务，立即 0ms 瞬时同步，确立车机端主导权
                    pendingPauseRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingPauseRunnable = null
                    stickyActivePackage = controller.packageName
                    lastActiveTimestamp = System.currentTimeMillis()
                    activeControllerCompat = controller
                    notifyInfo(immediate = true)
                } else if (newState == PlaybackStateCompat.STATE_PAUSED || newState == PlaybackStateCompat.STATE_BUFFERING) {
                    // 切歌或缓冲时的瞬态暂停：延迟 300ms 观察，若 300ms 内收到新歌的 STATE_PLAYING 则直接抵消，彻底消除切歌左右横跳
                    pendingPauseRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        if (activeControllerCompat == controller) {
                            notifyInfo(immediate = true)
                        }
                    }
                    pendingPauseRunnable = runnable
                    mainHandler.postDelayed(runnable, 300L)
                } else {
                    notifyInfo(immediate = false)
                }

                // 冷启动异常与死锁容错自愈重试 (针对 STATE_ERROR 7 与 STATE_STOPPED 1，或冷启动期由于底层初始化异常跌入的暂停态)
                val isTarget = (controller.packageName == autoPlayTargetPackage) || (controller.packageName == lastWakeUpPackage)
                val isWithinWindow = (System.currentTimeMillis() - lastAutoPlayAttemptTimestamp < 8000L) ||
                                     (lastWakeUpTimestamp > 0L && System.currentTimeMillis() - lastWakeUpTimestamp < 8000L)
                val isAbnormalState = (newState == PlaybackStateCompat.STATE_ERROR ||
                                       newState == PlaybackStateCompat.STATE_STOPPED ||
                                       newState == PlaybackStateCompat.STATE_NONE ||
                                       (newState == PlaybackStateCompat.STATE_PAUSED && userRequestedPauseUntil == 0L))
                val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (isTarget && isWithinWindow && isAbnormalState && am?.isMusicActive != true) {
                    // 若当前已有排期的自愈重试任务正在倒计时中，直接忽略毫秒级的中间态抖动，严禁提前透支重试计数器
                    if (autoPlayRetryRunnable != null) {
                        return
                    }
                    if (autoPlayRetryCount < 2) {
                        autoPlayRetryCount++
                        val retryDelay = 750L
                        FahrmonyLogBuffer.addLog(
                            "AUTO_PLAY",
                            "冷启动异常自愈重试",
                            "捕获底层状态 $newState (异常/停止/非预期暂停)，排期 ${retryDelay}ms 后重发 play (第 $autoPlayRetryCount 次)",
                            controller.packageName
                        )
                        val r = Runnable {
                            autoPlayRetryRunnable = null
                            val target = autoPlayTargetPackage ?: lastWakeUpPackage
                            if (target == controller.packageName && controller.playbackState?.state != PlaybackStateCompat.STATE_PLAYING) {
                                try {
                                    controller.transportControls?.play()
                                    FahrmonyLogBuffer.addLog("AUTO_PLAY", "自愈重试指令下发", "已向底层播放器下发重发指令 (第 $autoPlayRetryCount 次)", controller.packageName)
                                } catch (e: Exception) {
                                    FahrmonyLogBuffer.addLog("AUTO_PLAY", "自愈重试指令异常", "${e.message}", controller.packageName)
                                }
                            }
                        }
                        autoPlayRetryRunnable = r
                        mainHandler.postDelayed(r, retryDelay)
                    } else {
                        // 真实重试2次均已执行完毕且依然处于异常状态，方可判定为歌曲损坏/受限无法播放
                        val trackTitle = controller.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                        showInvalidTrackWarning(appContext, trackTitle, controller.packageName)
                    }
                } else if (newState == PlaybackStateCompat.STATE_ERROR && autoPlayRetryRunnable == null && autoPlayRetryCount == 0) {
                    // 非冷启动期间底层主动反馈播放错误，增加 1500ms 确认防抖，杜绝解码器瞬态毛刺误报
                    val trackTitle = controller.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                    mainHandler.postDelayed({
                        if (controller.playbackState?.state == PlaybackStateCompat.STATE_ERROR) {
                            showInvalidTrackWarning(appContext, trackTitle, controller.packageName)
                        }
                    }, 1500L)
                }
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                logLyricsProbe(controller.packageName, metadata)
                checkPendingAutoPlay(controller)
                if (activeControllerCompat == controller) {
                    val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                    val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                    val trackKey = "${controller.packageName}|$rawTitle|$rawArtist"
                    appContext?.let { checkAndUpdateLyrics(it, controller, trackKey, metadata) }
                }
                notifyInfo(immediate = true)
            }

            override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
                pushMirrorState()
            }

            override fun onQueueTitleChanged(title: CharSequence?) {
                pushMirrorState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (activeControllerCompat == controller) {
                    browserServiceRef?.get()?.syncRepeatMode(repeatMode)
                }
            }
        }
        controller.registerCallback(callback, mainHandler)
        callbackMap[controller] = callback
        // 初始注册首帧触发一次探针 Dump，避免必须等待切歌
        controller.metadata?.let { logLyricsProbe(controller.packageName, it) }
    }

    fun logLyricsProbe(pkg: String, metadata: MediaMetadataCompat?) {
        if (metadata == null) return
        try {
            val keys = metadata.keySet() ?: emptySet<String>()
            val dump = keys.joinToString("; ") { key ->
                val v = try {
                    metadata.getString(key) ?: metadata.getLong(key).toString()
                } catch (e: Exception) {
                    try {
                        if (metadata.getBitmap(key) != null) "[Bitmap]" else "[Object]"
                    } catch (e2: Exception) {
                        "[Unknown]"
                    }
                }
                val preview = if (v.length > 80) v.take(80) + "..." else v
                "$key=[$preview]"
            }
            FahrmonyLogBuffer.addLog(
                type = "LYRICS_PROBE",
                tag = pkg,
                title = "Metadata KeySet (${keys.size} keys)",
                content = dump
            )
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("LYRICS_PROBE", pkg, "Probe Error", e.message ?: "error")
        }
    }

    private fun showInvalidTrackWarning(context: Context?, trackTitle: String, packageName: String) {
        val message = "当前媒体曲目不可播放，请检查是否为缓存和地区限制问题"
        invalidTrackNotice = message
        pushMirrorState()
        FahrmonyLogBuffer.addLog("INVALID_TRACK", "曲目不可播放提示", message, "$packageName: $trackTitle")
        if (context != null) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null) {
                    val notification = NotificationCompat.Builder(context, FahrmonyForegroundService.CHANNEL_ID)
                        .setSmallIcon(context.applicationInfo.icon)
                        .setContentTitle(if (trackTitle.isNotBlank()) "《$trackTitle》无法播放" else "曲目不可播放")
                        .setContentText(message)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(2002, notification)
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun updateControllers(context: Context, controllers: List<MediaController>?) {
        if (!controllers.isNullOrEmpty()) {
            for (c in controllers) {
                // 绝不纳管自身会话，杜绝自环死锁
                if (c.packageName == context.packageName) continue
                // 校验是否属于已知受支持的官方媒体应用或用户自定义添加的播放器
                if (!isSupportedPackage(context, c.packageName)) continue
                val token = c.sessionToken ?: continue
                val compatToken = MediaSessionCompat.Token.fromToken(token) ?: continue
                val existing = allControllersCompat.firstOrNull { it.packageName == c.packageName }
                if (existing == null) {
                    try {
                        val compatController = MediaControllerCompat(context, compatToken)
                        allControllersCompat.add(compatController)
                        registerSingleControllerCallback(compatController)
                    } catch (e: Exception) {
                        FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "Controller创建失败", "${c.packageName}: ${e.message}")
                    }
                } else if (existing.sessionToken != compatToken) {
                    // 同源 App 会话 Token 发生了重置，彻底注销同包名所有旧 Controller 并替换为最新
                    val samePkg = allControllersCompat.filter { it.packageName == c.packageName }
                    for (old in samePkg) {
                        val cb = callbackMap.remove(old)
                        if (cb != null) {
                            try { old.unregisterCallback(cb) } catch (ignored: Exception) {}
                        }
                        allControllersCompat.remove(old)
                    }
                    try {
                        val compatController = MediaControllerCompat(context, compatToken)
                        allControllersCompat.add(compatController)
                        registerSingleControllerCallback(compatController)
                    } catch (e: Exception) {
                        FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "Controller更新失败", "${c.packageName}: ${e.message}")
                    }
                }
            }

            if (lastWakeUpPackage != null && lastWakeUpTimestamp > 0L) {
                val hasTarget = controllers.any { isPackageMatch(it.packageName, lastWakeUpPackage) }
                if (hasTarget) {
                    val elapsed = System.currentTimeMillis() - lastWakeUpTimestamp
                    FahrmonyLogBuffer.addLog(
                        "PROBE_READY",
                        "系统ActiveSessions就绪",
                        "目标: $lastWakeUpPackage, 从冷拉起到系统注册会话总耗时: ${elapsed}ms",
                        lastWakeUpPackage ?: ""
                    )
                }
            }
        }

        updateActiveController()
        activeControllerCompat?.let { checkPendingAutoPlay(it) }
    }

    private fun updateActiveController() {
        appContext?.packageName?.let { selfPkg ->
            allControllersCompat.removeAll { it.packageName == selfPkg }
        }
        if (allControllersCompat.isEmpty()) {
            activeControllerCompat = null
            onSessionUpdated?.invoke(null)
            pushMirrorState()
            return
        }

        // 核心排序策略 (确保真实反映当前正在播放的 App，如网易云音乐/QQ音乐，并保持粘性锁定，绝不随意漂移)：
        // 1. 优先选取处于 STATE_PLAYING 播放中的应用 (绝对第一优先级，绝不打断当前正在听歌的体验)
        // 2. 其次对齐用户设置的默认播放器 (若当前静止且默认播放器已有就绪会话，优先认领默认播放器)
        // 3. 再次保持 stickyActivePackage 锁定控制器 (即暂停时若非默认播放器触发，防止误漂移)
        // 4. 再次选取白名单主流 App (QQ音乐、网易云等)
        // 5. 最后回退到首个活跃 Controller
        val playing = allControllersCompat.firstOrNull { isSupportedPackage(appContext, it.packageName) && it.playbackState?.state == PlaybackStateCompat.STATE_PLAYING }
        if (playing != null) {
            stickyActivePackage = playing.packageName
            lastActiveTimestamp = System.currentTimeMillis()
            activeControllerCompat = playing
            notifyInfo()
            return
        }

        val defaultPkg = appContext?.let { FahrmonyConfig.getDefaultPlayer(it) }
        if (!defaultPkg.isNullOrBlank()) {
            val defaultCtrl = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, defaultPkg) }
            if (defaultCtrl != null) {
                stickyActivePackage = defaultPkg
                activeControllerCompat = defaultCtrl
                notifyInfo()
                return
            }
        }

        if (stickyActivePackage != null) {
            val sticky = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, stickyActivePackage) }
            if (sticky != null) {
                activeControllerCompat = sticky
                notifyInfo()
                return
            }
        }

        val matchedKnown = allControllersCompat.firstOrNull { isSupportedPackage(appContext, it.packageName) }
        if (matchedKnown != null) {
            stickyActivePackage = matchedKnown.packageName
            activeControllerCompat = matchedKnown
            notifyInfo()
            return
        }

        val fallback = allControllersCompat.firstOrNull { isSupportedPackage(appContext, it.packageName) }
        if (fallback != null) {
            stickyActivePackage = fallback.packageName
            activeControllerCompat = fallback
            notifyInfo()
            return
        }

        // 无已知受支持媒体应用时，彻底清空活跃控制器并重置镜像
        activeControllerCompat = null
        onSessionUpdated?.invoke(null)
        pushMirrorState()
    }

    private fun checkPendingAutoPlay(controller: MediaControllerCompat) {
        val targetPkg = pendingAutoPlayPackage ?: return
        val now = System.currentTimeMillis()
        if (now > pendingAutoPlayDeadline) {
            pendingAutoPlayPackage = null
            return
        }
        if (isPackageMatch(controller.packageName, targetPkg)) {
            val metadata = controller.metadata
            val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
            val fallback = fallbackMetaMap[controller.packageName]
            val hasRealTrack = rawTitle.isNotBlank() || (!fallback?.title.isNullOrBlank())

            // 关键：若会话刚上线但曲目尚未载入内存 (空壳会话)，保留标记等待 onMetadataChanged 真实曲目到达
            if (!hasRealTrack) {
                return
            }

            pendingAutoPlayPackage = null
            pendingAutoPlayDeadline = 0L
            val isPlaying = controller.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
            val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (!isPlaying && am?.isMusicActive != true) {
                try {
                    lastAutoPlayAttemptTimestamp = System.currentTimeMillis()
                    autoPlayTargetPackage = controller.packageName
                    autoPlayRetryCount = 0
                    controller.transportControls?.play()
                    FahrmonyLogBuffer.addLog(
                        "AUTO_PLAY",
                        "事件驱动起播成功",
                        "目标真实曲目就绪，已下发play指令: $rawTitle",
                        "应用: ${controller.packageName}"
                    )
                    // 启动冷启动起播主动核检轮询链 (针对网易云等刚上线未就绪播放引擎的应用)
                    scheduleAutoPlayVerification(controller, 1)
                } catch (e: Exception) {
                    FahrmonyLogBuffer.addLog("AUTO_PLAY", "事件驱动起播异常", "${e.message}", controller.packageName)
                }
            }
        }
    }

    private fun scheduleAutoPlayVerification(controller: MediaControllerCompat, attempt: Int) {
        val delay = if (attempt == 1) 500L else 1000L
        mainHandler.postDelayed({
            val isPlaying = controller.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
            val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isAudioActive = am?.isMusicActive == true
            if (!isPlaying && !isAudioActive && autoPlayTargetPackage == controller.packageName) {
                FahrmonyLogBuffer.addLog(
                    "AUTO_PLAY",
                    "起播确认重试",
                    "第 $attempt 次核检未发声 (rawState=${controller.playbackState?.state})，补发二次指令与硬件按键脉冲",
                    controller.packageName
                )
                try {
                    controller.transportControls?.play()
                } catch (ignored: Exception) {}
                try {
                    am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                } catch (ignored: Exception) {}

                if (attempt < 2) {
                    scheduleAutoPlayVerification(controller, attempt + 1)
                }
            }
        }, delay)
    }

    private val debounceRunnable = Runnable {
        doNotifyInfo()
    }

    private fun notifyInfo(immediate: Boolean = false) {
        mainHandler.removeCallbacks(debounceRunnable)
        if (immediate) {
            doNotifyInfo()
        } else {
            mainHandler.postDelayed(debounceRunnable, 250L)
        }
    }

    private fun doNotifyInfo() {
        val info = getActiveSessionInfo()
        if (info != null && info.title.isNotBlank()) {
            FahrmonyLogBuffer.addLog(
                type = "MEDIA_SESSION",
                tag = "${info.appName} (${info.packageName})",
                title = info.title,
                content = "${info.artist} - ${info.album} [${if (info.isPlaying) "播放中" else "暂停"}]"
            )
        }
        onSessionUpdated?.invoke(info)
        onActiveSessionChangedListener?.invoke(info)
        pushMirrorState()
    }

    fun optimisticSyncState(targetState: Int) {
        val service = browserServiceRef?.get() ?: return
        val controller = activeControllerCompat
        val position = controller?.playbackState?.position ?: 0L
        val isPlaying = (targetState == PlaybackStateCompat.STATE_PLAYING)
        val repeatProbe = probeRepeatCapability(controller)

        var actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        if (repeatProbe is RepeatProbeResult.Standard) {
            actions = actions or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setState(targetState, position, if (isPlaying) 1.0f else 0.0f)
            .setActions(actions)
            .setActiveQueueItemId(0L)

        if (repeatProbe is RepeatProbeResult.Custom) {
            val customAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_CAR_REPEAT,
                customRepeatState.title,
                customRepeatState.iconRes
            ).build()
            stateBuilder.addCustomAction(customAction)
        }

        val optCtx = appContext ?: service.applicationContext
        val lyricsMode = FahrmonyConfig.getLyricsMode(optCtx)
        val lyricsActionTitle = FahrmonyCarI18n.getLyricsActionTitle(optCtx, lyricsMode)
        val lyricsActionIcon = when (lyricsMode) {
            1 -> com.fahrmony.app.R.drawable.ic_car_lyrics_single
            2 -> com.fahrmony.app.R.drawable.ic_car_lyrics_dual
            else -> com.fahrmony.app.R.drawable.ic_car_lyrics_off
        }
        val lyricsCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_LYRICS,
            lyricsActionTitle,
            lyricsActionIcon
        ).build()
        stateBuilder.addCustomAction(lyricsCustomAction)

        service.syncPlaybackState(stateBuilder.build())
    }

    private fun pushMirrorState() {
        val service = browserServiceRef?.get() ?: return
        val context = appContext ?: service.applicationContext
        val isRawCardEnabled = FahrmonyConfig.isRawPlayerCardEnabled(context)

        if (isRawCardEnabled) {
            // 用户开启“音频 App 原始播放卡片”：关闭替代卡片并让出主要焦点给底层正在播放的音乐 App
            try {
                service.setSessionActive(false)
                val emptyState = PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
                    .build()
                service.syncPlaybackState(emptyState)
                lastSyncedTrackKey = "__raw_player_card_yielded__"
                FahrmonyLogBuffer.addLog("PROBE_MIRROR", "焦点让渡", "音频App原始播放卡片已启用，已关闭替代卡片并释放车机主焦点", activeControllerCompat?.packageName ?: "none")
            } catch (ignored: Exception) {}
            return
        }

        // 默认模式：确保自身 MediaSession 处于活跃状态，争夺车机主要焦点
        try {
            service.setSessionActive(true)
        } catch (ignored: Exception) {}

        val controller = activeControllerCompat

        if (controller == null) {
            val stateBuilder = PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_STOP
                )
            service.syncPlaybackState(stateBuilder.build())
            service.syncRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)

            val trackKey = "__null_session__"
            if (lastSyncedTrackKey != trackKey) {
                lastSyncedTrackKey = trackKey
                val defaultBmp = getIdleArtwork()
                currentTrackArtworkBitmap = defaultBmp
                val appTitle = appContext?.let { FahrmonyCarI18n.getAppTitle(it) } ?: "Fahrmony 合拍"
                val waitingSub = appContext?.let { FahrmonyCarI18n.getWaitingSubtitle(it) } ?: "等待音乐播放中"
                val metaBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, appTitle)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, waitingSub)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, defaultBmp)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, defaultBmp)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, defaultBmp)
                FahrmonyLogBuffer.addLog("PROBE_MIRROR", "推流空会话元数据", "trackKey: $trackKey, appTitle: $appTitle", "none")
                service.syncMetadata(metaBuilder.build())
            }
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState
        val rawState = state?.state ?: PlaybackStateCompat.STATE_NONE

        // 1. 同步元数据 (带 trackKey 差异比对与粘性缓存，杜绝缓冲瞬间误推占位符)
        val fallback = fallbackMetaMap[controller.packageName]
        val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
        val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        val rawAlbum = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: ""

        if (rawTitle.isNotBlank()) {
            lastValidTrackMap[controller.packageName] = CachedValidTrack(rawTitle, rawArtist, rawAlbum, metadata)
        }
        val cached = lastValidTrackMap[controller.packageName]

        val defaultNowPlaying = appContext?.let { FahrmonyCarI18n.getNowPlayingDefault(it) } ?: "正在播放"
        val hasRealTrack = rawTitle.isNotBlank() || (!fallback?.title.isNullOrBlank()) || (cached != null)
        val title = rawTitle.ifBlank { fallback?.title ?: (cached?.title ?: defaultNowPlaying) }
        val rawOrCachedArtist = rawArtist.ifBlank { fallback?.artist ?: (cached?.artist ?: (getAppNameForPackage(appContext, controller.packageName))) }
        val artist = if (!invalidTrackNotice.isNullOrBlank()) invalidTrackNotice!! else rawOrCachedArtist
        val album = rawAlbum.ifBlank { fallback?.album ?: (cached?.album ?: "") }
        val trackKey = "${controller.packageName}|$title|$artist"

        if (trackKey != lastSyncedTrackKey) {
            lastSyncedTrackKey = trackKey
            val trackBmp = if (hasRealTrack) {
                appContext?.let { getArtworkForTrack(it, trackKey, isRealTrack = true) } ?: getIdleArtwork()
            } else {
                getIdleArtwork()
            }
            currentTrackArtworkBitmap = trackBmp

            FahrmonyLogBuffer.addLog(
                "PROBE_MIRROR",
                "推流元数据变更",
                "trackKey: $trackKey, isRealTrack: $hasRealTrack, title: $title, artist: $artist",
                controller.packageName
            )
            val effectiveMeta = if (rawTitle.isNotBlank()) metadata else (cached?.metadata ?: metadata)
            if (effectiveMeta != null && hasRealTrack) {
                service.syncMetadata(sanitizeMetadata(effectiveMeta, trackKey, isRealTrack = true, customBitmap = trackBmp))
            } else {
                val metaBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, trackBmp)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, trackBmp)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, trackBmp)
                service.syncMetadata(metaBuilder.build())
            }

            // 触发歌词查询与同步引擎更新
            checkAndUpdateLyrics(context, controller, trackKey, effectiveMeta ?: metadata)
        }

        // 2. 同步播放状态 (确保包含标准双向操控 actions 与暂停防回弹锁)
        val repeatProbe = probeRepeatCapability(controller)
        val hasStandardRepeat = (repeatProbe is RepeatProbeResult.Standard)

        var actions = (state?.actions ?: 0L) or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        if (hasStandardRepeat) {
            actions = actions or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        }

        val position = state?.position ?: 0L
        val isUserPaused = System.currentTimeMillis() < userRequestedPauseUntil
        val isHandshakeProtecting = !isUserPaused && (System.currentTimeMillis() < handshakeProtectionUntil)
        val isPlayProtecting = !isUserPaused && (System.currentTimeMillis() < playProtectionUntil)
        val isSkipProtecting = !isUserPaused && !isHandshakeProtecting && !isPlayProtecting && (System.currentTimeMillis() < skipProtectionUntil)
        val isPlaying = if (isUserPaused) {
            false
        } else if (isHandshakeProtecting || isPlayProtecting) {
            true
        } else if (isSkipProtecting) {
            skipProtectionState == PlaybackStateCompat.STATE_PLAYING
        } else {
            rawState == PlaybackStateCompat.STATE_PLAYING
        }
        val playbackCompatState = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setState(playbackCompatState, position, if (isPlaying) 1.0f else 0.0f)
            .setActions(actions)
            .setActiveQueueItemId(0L)

        if (repeatProbe is RepeatProbeResult.Custom) {
            val customAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_CAR_REPEAT,
                customRepeatState.title,
                customRepeatState.iconRes
            ).build()
            stateBuilder.addCustomAction(customAction)
        }

        val lyricsMode = FahrmonyConfig.getLyricsMode(context)
        val lyricsActionTitle = FahrmonyCarI18n.getLyricsActionTitle(context, lyricsMode)
        val lyricsActionIcon = when (lyricsMode) {
            1 -> com.fahrmony.app.R.drawable.ic_car_lyrics_single
            2 -> com.fahrmony.app.R.drawable.ic_car_lyrics_dual
            else -> com.fahrmony.app.R.drawable.ic_car_lyrics_off
        }
        val lyricsCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_LYRICS,
            lyricsActionTitle,
            lyricsActionIcon
        ).build()
        stateBuilder.addCustomAction(lyricsCustomAction)

        FahrmonyLogBuffer.addLog(
            "PROBE_MIRROR",
            "推流播放状态",
            "state: ${if (playbackCompatState == PlaybackStateCompat.STATE_PLAYING) "播放中" else "暂停"}, rawState: $rawState, isUserPaused: $isUserPaused",
            controller.packageName
        )
        service.syncPlaybackState(stateBuilder.build())
        if (hasStandardRepeat) {
            service.syncRepeatMode(controller.repeatMode)
        } else {
            service.syncRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
        }

        lyricSyncEngine.updatePlaybackState(controller.playbackState)
    }

    // 封面 Base64 内存缓存 (以 trackKey 为索引，杜绝高频刷新与播放走针时的重复压缩 GC 开销)
    private val artworkCache = ConcurrentHashMap<String, String>()

    private fun encodeBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val maxDim = 256
            val width = bitmap.width
            val height = bitmap.height
            val scaled = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val (newW, newH) = if (ratio > 1f) {
                    maxDim to (maxDim / ratio).toInt()
                } else {
                    (maxDim * ratio).toInt() to maxDim
                }
                Bitmap.createScaledBitmap(bitmap, newW.coerceAtLeast(1), newH.coerceAtLeast(1), true)
            } else {
                bitmap
            }
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val bytes = baos.toByteArray()
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractArtwork(controller: MediaControllerCompat, title: String, artist: String, album: String): String? {
        val trackKey = "${controller.packageName}_${title}_${artist}_${album}"
        if (title.isBlank() && artist.isBlank()) return null
        val cached = artworkCache[trackKey]
        if (cached != null) return cached

        val metadata = controller.metadata
        val bmp = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
            ?: metadata?.description?.iconBitmap
            ?: fallbackMetaMap[controller.packageName]?.artworkBmp

        if (bmp != null && !bmp.isRecycled) {
            val base64 = encodeBitmapToBase64(bmp)
            if (base64 != null) {
                if (artworkCache.size > 20) artworkCache.clear()
                artworkCache[trackKey] = base64
                return base64
            }
        }

        val uriStr = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)

        if (!uriStr.isNullOrBlank()) {
            if (uriStr.startsWith("http://") || uriStr.startsWith("https://") || uriStr.startsWith("data:")) {
                if (artworkCache.size > 20) artworkCache.clear()
                artworkCache[trackKey] = uriStr
                return uriStr
            }
            try {
                val uri = android.net.Uri.parse(uriStr)
                val stream = appContext?.contentResolver?.openInputStream(uri)
                if (stream != null) {
                    val decoded = android.graphics.BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (decoded != null) {
                        val base64 = encodeBitmapToBase64(decoded)
                        if (!decoded.isRecycled) decoded.recycle()
                        if (base64 != null) {
                            if (artworkCache.size > 20) artworkCache.clear()
                            artworkCache[trackKey] = base64
                            return base64
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        return null
    }

    private fun extractSessionInfo(controller: MediaControllerCompat): ActiveMediaSessionInfo {
        val pkg = controller.packageName
        val isKnownWhiteList = (pkg in KNOWN_PACKAGES.keys)

        val metadata = controller.metadata
        val state = controller.playbackState

        val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
        val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        val rawAlbum = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L

        if (isKnownWhiteList) {
            // ========== 轨道 1: 8 大官方已适配白名单 App (原汁原味成熟逻辑，零干扰零回归) ==========
            val fallback = fallbackMetaMap[pkg]
            val title = rawTitle.ifBlank { fallback?.title ?: "" }
            val artist = rawArtist.ifBlank { fallback?.artist ?: "" }
            val album = rawAlbum.ifBlank { fallback?.album ?: "" }
            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            val appName = getAppNameForPackage(appContext, pkg)
            val artworkData = extractArtwork(controller, title, artist, album)

            return ActiveMediaSessionInfo(
                packageName = pkg,
                appName = appName,
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                duration = duration,
                position = position,
                artworkData = artworkData,
                hasLyrics = lyricSyncEngine.hasValidLyrics()
            )
        } else {
            // ========== 轨道 2: 自定义音源专属增强通道 (粘性元数据锁存 + 通知栏深度抽取 + 音频焦点智能保活) ==========
            val sticky = customPlayerStickyCache[pkg]
            val fallback = fallbackMetaMap[pkg]

            // 提取标题：优先原生元数据 -> 实时通知栏抽取 -> description 标题 -> 队列标题 -> 专属粘性缓存
            val descTitle = controller.metadata?.description?.title?.toString() ?: ""
            val queueTitle = controller.queueTitle?.toString() ?: ""
            val title = if (rawTitle.isNotBlank()) {
                rawTitle
            } else if (!fallback?.title.isNullOrBlank()) {
                fallback!!.title
            } else if (descTitle.isNotBlank()) {
                descTitle
            } else if (queueTitle.isNotBlank()) {
                queueTitle
            } else {
                sticky?.title ?: ""
            }

            val descSubtitle = controller.metadata?.description?.subtitle?.toString() ?: ""
            val artist = if (rawArtist.isNotBlank()) {
                rawArtist
            } else if (!fallback?.artist.isNullOrBlank()) {
                fallback!!.artist
            } else if (descSubtitle.isNotBlank()) {
                descSubtitle
            } else {
                sticky?.artist ?: ""
            }

            val album = if (rawAlbum.isNotBlank()) {
                rawAlbum
            } else if (!fallback?.album.isNullOrBlank()) {
                fallback!!.album
            } else {
                sticky?.album ?: ""
            }

            if (title.isNotBlank()) {
                customPlayerStickyCache[pkg] = CachedValidTrack(title, artist, album, metadata)
            }

            // 播放态判定：标准 STATE_PLAYING 或 (音频焦点处于输出中 + 存在有效曲目名且未处于显式暂停态)
            val rawPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isAudioActive = am?.isMusicActive == true
            val isExplicitPaused = (state?.state == PlaybackStateCompat.STATE_PAUSED || state?.state == PlaybackStateCompat.STATE_STOPPED)
            val isPlaying = rawPlaying || (isAudioActive && title.isNotBlank() && !isExplicitPaused)

            android.util.Log.i(
                "Fahrmony_CUSTOM",
                "[EXTRACT] pkg: $pkg, rawTitle: '$rawTitle', fallbackTitle: '${fallback?.title}', stickyTitle: '${sticky?.title}' => finalTitle: '$title', rawState: ${state?.state}, isPlaying: $isPlaying"
            )

            val appName = getAppNameForPackage(appContext, pkg)
            val artworkData = extractArtwork(controller, title, artist, album)

            return ActiveMediaSessionInfo(
                packageName = pkg,
                appName = appName,
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                duration = duration,
                position = position,
                artworkData = artworkData,
                hasLyrics = lyricSyncEngine.hasValidLyrics()
            )
        }
    }

    private fun sanitizeMetadata(metadata: MediaMetadataCompat, trackKey: String, isRealTrack: Boolean = true, customBitmap: Bitmap? = null): MediaMetadataCompat {
        val defaultBmp = customBitmap ?: currentTrackArtworkBitmap ?: if (isRealTrack) {
            appContext?.let { getArtworkForTrack(it, trackKey, isRealTrack = true) } ?: getIdleArtwork()
        } else {
            getIdleArtwork()
        }
        val builder = MediaMetadataCompat.Builder()

        // 1. 继承所有外部有效文本元数据 (歌名、歌手、专辑、时长等)
        val title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
        if (!title.isNullOrBlank()) builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)

        val normalArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        val artist = if (!invalidTrackNotice.isNullOrBlank()) invalidTrackNotice else normalArtist
        if (!artist.isNullOrBlank()) builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)

        val album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
        if (!album.isNullOrBlank()) builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)

        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        if (duration > 0L) builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        // 2. 彻底屏蔽外部暗色专辑封面与远程 URI，全天候强制使用高动态低饱和精美流光渐变
        // 确保切歌时上下曲颜色明显不同，同时 Google Palette 提取器 100% 稳定命中高亮 VibrantSwatch，彻底杜绝黑键
        if (defaultBmp != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, defaultBmp)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, defaultBmp)
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, defaultBmp)
        }

        return builder.build()
    }

    fun getActiveSessionInfo(): ActiveMediaSessionInfo? {
        val controller = activeControllerCompat ?: return null
        return extractSessionInfo(controller)
    }

    fun getActiveQueue(): List<MediaSessionCompat.QueueItem>? {
        val controller = activeControllerCompat ?: return null
        val queue = controller.queue
        return if (!queue.isNullOrEmpty()) queue else null
    }

    fun getActiveQueueTitle(): CharSequence? {
        return activeControllerCompat?.queueTitle
    }

    fun skipToQueueItem(id: Long): Boolean {
        val controller = activeControllerCompat ?: return false
        try {
            controller.transportControls?.skipToQueueItem(id)
            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "切换队列项", "目标ID: $id", controller.packageName)
            return true
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("MEDIA_IPC", "切换队列项异常", "${e.message}", controller.packageName)
            return false
        }
    }

    fun getActiveRepeatMode(): Int {
        return activeControllerCompat?.repeatMode ?: PlaybackStateCompat.REPEAT_MODE_NONE
    }

    fun setRepeatMode(repeatMode: Int): Boolean {
        val controller = activeControllerCompat ?: return false
        try {
            controller.transportControls?.setRepeatMode(repeatMode)
            browserServiceRef?.get()?.syncRepeatMode(repeatMode)
            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "切换循环模式", "目标模式: $repeatMode", controller.packageName)
            return true
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("MEDIA_IPC", "切换循环模式异常", "${e.message}", controller.packageName)
            return false
        }
    }

    fun toggleRepeatMode(): Boolean {
        val controller = activeControllerCompat ?: return false
        val probe = probeRepeatCapability(controller)
        return when (probe) {
            is RepeatProbeResult.Custom -> {
                try {
                    customRepeatState = customRepeatState.next()
                    controller.transportControls?.sendCustomAction(probe.actionName, null)
                    FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "透传CustomAction", "${probe.actionName} -> ${customRepeatState.title}", controller.packageName)
                    pushMirrorState()
                    true
                } catch (e: Exception) {
                    FahrmonyLogBuffer.addLog("MEDIA_IPC", "透传CustomAction异常", "${e.message}", controller.packageName)
                    false
                }
            }
            is RepeatProbeResult.Standard -> {
                val current = controller.repeatMode
                val next = when (current) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                    PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
                setRepeatMode(next)
            }
            is RepeatProbeResult.Unsupported -> false
        }
    }

    private fun checkAndUpdateLyrics(
        context: Context,
        controller: MediaControllerCompat,
        trackKey: String,
        metadata: MediaMetadataCompat?
    ) {
        if (!FahrmonyConfig.isLyricsEnabled(context)) {
            lyricSyncEngine.reset(notifyListener = false)
            return
        }
        val allowNetwork = FahrmonyConfig.isNetworkLyricsEnabled(context)
        UnifiedLyricsProvider.getLyrics(context, controller.packageName, metadata, allowNetwork) { result ->
            val current = activeControllerCompat
            if (current != null && isPackageMatch(current.packageName, controller.packageName)) {
                lyricSyncEngine.updateLyrics(trackKey, result)
                lyricSyncEngine.updatePlaybackState(current.playbackState)
            }
        }
    }

    private fun pushLyricMetadata(currentLine: String, nextLine: String?) {
        val service = browserServiceRef?.get() ?: return
        val context = appContext ?: service.applicationContext
        val mode = FahrmonyConfig.getLyricsMode(context)
        if (mode == 0) {
            return
        }
        val controller = activeControllerCompat ?: return
        val metadata = controller.metadata
        val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            ?: lastValidTrackMap[controller.packageName]?.title ?: ""
        val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ?: lastValidTrackMap[controller.packageName]?.artist ?: ""
        val trackKey = "${controller.packageName}|$rawTitle|$rawArtist"

        val songInfo = if (rawArtist.isNotBlank() && !rawTitle.contains(rawArtist)) "$rawTitle · $rawArtist" else rawTitle.ifBlank { "..." }
        val displayTitle = if (currentLine.isNotBlank()) currentLine else rawTitle.ifBlank { "..." }
        val displayArtist = if (mode == 1) {
            // 单行模式：Line 2 恒定显示歌曲名 · 歌手名，永不丢失曲目信息
            songInfo
        } else {
            // 双行模式：Line 2 显示下一句歌词，无下句时回退为歌曲信息
            if (!nextLine.isNullOrBlank()) nextLine else songInfo
        }
        val displayAlbum = songInfo
        val defaultBmp = currentTrackArtworkBitmap ?: getArtworkForTrack(context, trackKey, isRealTrack = true)
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, displayTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, displayArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, displayAlbum)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, defaultBmp)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, defaultBmp)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, defaultBmp)

        if (duration > 0L) {
            metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        }

        service.syncLyricMetadata(metaBuilder.build())
    }

    fun toggleLyricsMode(context: Context): Int {
        val currentMode = FahrmonyConfig.getLyricsMode(context)
        val nextMode = (currentMode + 1) % 3
        FahrmonyConfig.setLyricsMode(context, nextMode)
        if (nextMode > 0) {
            val json = org.json.JSONObject().apply {
                put("networkLyricsEnabled", true)
            }
            FahrmonyConfig.updateConfig(context, json)
        }
        lyricSyncEngine.setEnabled(nextMode > 0)
        if (nextMode > 0) {
            val controller = activeControllerCompat
            if (controller != null) {
                val meta = controller.metadata ?: lastValidTrackMap[controller.packageName]?.metadata
                val rawTitle = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                val rawArtist = meta?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                val trackKey = "${controller.packageName}|$rawTitle|$rawArtist"
                checkAndUpdateLyrics(context, controller, trackKey, meta)
            }
        } else {
            lyricSyncEngine.reset(notifyListener = true)
        }
        pushMirrorState()
        // 广播至主进程，驱动手机端媒体 Tab 同步
        val fullConfig = FahrmonyConfig.getConfig(context)
        FahrmonyIpcBridge.notifyClientConfigChanged(fullConfig.toString())
        val modeText = when (nextMode) {
            1 -> "单行"
            2 -> "双行"
            else -> "关闭"
        }
        FahrmonyLogBuffer.addLog("LYRICS", "车载歌词模式切换", modeText, "车机CustomAction切换")
        return nextMode
    }

    fun getAllActiveSessions(context: Context): List<ActiveMediaSessionInfo> {
        // 1. 主动扫描系统当前活跃 MediaSession，确保已配置的自定义非标应用会话秒级补全纳管
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val cn = ComponentName(context, FahrmonyNotificationListener::class.java)
            val activeSessions = msm?.getActiveSessions(cn)
            if (!activeSessions.isNullOrEmpty()) {
                updateControllers(context, activeSessions)
            }
        } catch (ignored: Exception) {}

        // 2. 读取纳管控制器列表，按包名去重
        val map = LinkedHashMap<String, ActiveMediaSessionInfo>()
        for (c in allControllersCompat) {
            // 校验是否属于已知受支持应用或自定义播放器
            if (!isSupportedPackage(context, c.packageName)) continue
            val info = extractSessionInfo(c)
            val existing = map[info.packageName]
            if (existing == null || (!existing.isPlaying && info.isPlaying)) {
                map[info.packageName] = info
            }
        }
        return map.values.toList()
    }

    /**
     * 动态嗅探正在播放或处于活跃状态的候选音源 (排查黑名单与 8 大内置白名单)
     */
    fun getDiscoveredMediaSessions(context: Context): List<DiscoveredMediaSessionInfo> {
        val result = mutableListOf<DiscoveredMediaSessionInfo>()
        val seenPackages = hashSetOf<String>()

        // 1. 检查已有的 MediaSessionCompat 控制器快照
        for (c in allControllersCompat) {
            val pkg = c.packageName
            if (pkg == context.packageName) continue
            if (pkg in KNOWN_PACKAGES.keys) continue
            if (pkg.startsWith("com.shopee.")) continue
            if (pkg in SMART_MEDIA_BLACKLIST) continue
            if (seenPackages.contains(pkg)) continue

            val info = extractSessionInfo(c)
            val isPlaying = info.isPlaying
            val hasTitle = info.title.isNotBlank()
            if (isPlaying || hasTitle) {
                val appName = getAppNameForPackage(context, pkg)
                val iconBase64 = getAppIconBase64(context, pkg)
                result.add(
                    DiscoveredMediaSessionInfo(
                        packageName = pkg,
                        appName = appName,
                        iconBase64 = iconBase64,
                        title = info.title,
                        artist = info.artist,
                        isPlaying = isPlaying
                    )
                )
                seenPackages.add(pkg)
            }
        }

        // 2. 主动从系统 MediaSessionManager 补充可能未被监听器捕获的会话
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val cn = ComponentName(context, FahrmonyNotificationListener::class.java)
            val activeSessions = msm?.getActiveSessions(cn)
            if (!activeSessions.isNullOrEmpty()) {
                for (session in activeSessions) {
                    val pkg = session.packageName
                    if (pkg == context.packageName) continue
                    if (pkg in KNOWN_PACKAGES.keys) continue
                    if (pkg.startsWith("com.shopee.")) continue
                    if (pkg in SMART_MEDIA_BLACKLIST) continue
                    if (seenPackages.contains(pkg)) continue

                    val state = session.playbackState
                    val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
                    val meta = session.metadata
                    val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                    val artist = meta?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""

                    if (isPlaying || title.isNotBlank()) {
                        val appName = getAppNameForPackage(context, pkg)
                        val iconBase64 = getAppIconBase64(context, pkg)
                        result.add(
                            DiscoveredMediaSessionInfo(
                                packageName = pkg,
                                appName = appName,
                                iconBase64 = iconBase64,
                                title = title,
                                artist = artist,
                                isPlaying = isPlaying
                            )
                        )
                        seenPackages.add(pkg)
                    }
                }
            }
        } catch (ignored: Exception) {}

        return result
    }

    // 指令直达目标 App 的 TransportControls (具备跨进程 DeadObjectException 异常自愈隔离)
    // 指令直达目标 App 的 TransportControls (具备跨进程 DeadObjectException 异常自愈隔离与目标包名精准寻址)
    fun sendCommand(action: String, targetPackage: String? = null): Boolean {
        if (action.equals("pause", ignoreCase = true)) {
            // 立即乐观置位 STATE_PAUSED，杜绝会话颠簸
            optimisticSyncState(PlaybackStateCompat.STATE_PAUSED)
        }

        // 精准寻址：若显式指定了目标包名，优先在已纳管控制器中查找该目标
        var targetCtrl = if (!targetPackage.isNullOrBlank()) {
            allControllersCompat.firstOrNull { isPackageMatch(it.packageName, targetPackage) }
        } else null

        // 若指定了目标包名但内存池中未找到，主动从系统 MediaSessionManager 动态寻址尝试二次纳管
        if (!targetPackage.isNullOrBlank() && targetCtrl == null) {
            try {
                val ctx = appContext ?: return false
                val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                val cn = ComponentName(ctx, FahrmonyNotificationListener::class.java)
                val activeSessions = msm?.getActiveSessions(cn)
                if (!activeSessions.isNullOrEmpty()) {
                    updateControllers(ctx, activeSessions)
                    targetCtrl = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, targetPackage) }
                }
            } catch (ignored: Exception) {}
        }

        // 若指定了目标包名但该应用确实无就绪控制器：
        // 若为 play，交由定向起播流程；若为其他指令，坚决严禁回退到 activeControllerCompat 导致串台污染
        if (!targetPackage.isNullOrBlank() && targetCtrl == null) {
            if (action.equals("play", ignoreCase = true)) {
                appContext?.let { play(it, targetPackage) }
                return true
            }
            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "定向指令拦截", "目标应用 $targetPackage 无就绪会话，拒绝串台降级", targetPackage)
            return false
        }

        val controller = targetCtrl ?: (if (targetPackage.isNullOrBlank()) activeControllerCompat else null) ?: return false
        val controls = controller.transportControls ?: return false

        android.util.Log.i("Fahrmony_CUSTOM", "[CMD_DISPATCH] action: $action, targetPkg: $targetPackage, resolvedCtrl: ${controller.packageName}")

        try {
            when (action.lowercase()) {
                "play" -> {
                    disconnectSuppressionUntil = 0L
                    userRequestedPauseUntil = 0L
                    playProtectionUntil = System.currentTimeMillis() + 1500L
                    invalidTrackNotice = null
                    autoPlayRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    autoPlayRetryRunnable = null
                    lastAutoPlayAttemptTimestamp = 0L
                    autoPlayRetryCount = 0
                    autoPlayTargetPackage = null
                    stickyActivePackage = controller.packageName
                    activeControllerCompat = controller
                    optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)
                    controls.play()
                }
                "pause" -> {
                    pendingAutoPlayPackage = null
                    pendingAutoPlayDeadline = 0L
                    handshakeProtectionUntil = 0L
                    skipProtectionUntil = 0L
                    playProtectionUntil = 0L
                    userRequestedPauseUntil = System.currentTimeMillis() + 1500L
                    autoPlayRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    autoPlayRetryRunnable = null
                    lastAutoPlayAttemptTimestamp = 0L
                    autoPlayRetryCount = 0
                    autoPlayTargetPackage = null
                    optimisticSyncState(PlaybackStateCompat.STATE_PAUSED)
                    controls.pause()
                }
                "skip_next", "skip_previous" -> {
                    userRequestedPauseUntil = 0L
                    playProtectionUntil = 0L
                    invalidTrackNotice = null
                    // 智能状态保留：切歌必然是主动播放行为，无论切歌前是否发声，均乐观维持 STATE_PLAYING 杜绝车机卡片切给第三方
                    skipProtectionUntil = System.currentTimeMillis() + 2500L
                    skipProtectionState = PlaybackStateCompat.STATE_PLAYING

                    optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)

                    if (action.lowercase() == "skip_next") {
                        controls.skipToNext()
                    } else {
                        controls.skipToPrevious()
                    }

                    // 切歌后调度两次快速状态确认，确立并锚定 Fahrmony 会话所有权
                    mainHandler.postDelayed({ pushMirrorState() }, 150L)
                    mainHandler.postDelayed({ pushMirrorState() }, 450L)
                }
                else -> return false
            }
            return true
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("MEDIA_IPC", "指令发送异常", "目标进程不可达", "${e.message}")
            if (e is DeadObjectException || e is RemoteException) {
                // 目标 App 进程被强制查杀，从活跃队列剥离并自动自愈下移到备选控制器
                val cb = callbackMap.remove(controller)
                if (cb != null) {
                    try { controller.unregisterCallback(cb) } catch (ignored: Exception) {}
                }
                allControllersCompat.remove(controller)
                updateActiveController()
            }
            return false
        }
    }

    fun seekTo(positionMs: Long): Boolean {
        val controller = activeControllerCompat ?: return false
        try {
            controller.transportControls?.seekTo(positionMs)
            // 乐观刷新本地播放进度，防止车机进度条跳动回弹
            val service = browserServiceRef?.get()
            if (service != null) {
                val isPlaying = controller.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
                val repeatProbe = probeRepeatCapability(controller)

                var actions = PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP

                if (repeatProbe is RepeatProbeResult.Standard) {
                    actions = actions or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                }

                val state = PlaybackStateCompat.Builder()
                    .setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, positionMs, if (isPlaying) 1.0f else 0.0f)
                    .setActions(actions)
                    .setActiveQueueItemId(0L)

                if (repeatProbe is RepeatProbeResult.Custom) {
                    val customAction = PlaybackStateCompat.CustomAction.Builder(
                        ACTION_CAR_REPEAT,
                        customRepeatState.title,
                        customRepeatState.iconRes
                    ).build()
                    state.addCustomAction(customAction)
                }

                service.syncPlaybackState(state.build())
            }
            return true
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("MEDIA_IPC", "拖拽进度异常", "目标进程不可达", "${e.message}")
            return false
        }
    }

    fun play(context: Context, targetPackage: String? = null): Boolean {
        disconnectSuppressionUntil = 0L
        pendingAutoPlayPackage = null
        pendingAutoPlayDeadline = 0L
        userRequestedPauseUntil = 0L
        playProtectionUntil = System.currentTimeMillis() + 1500L
        invalidTrackNotice = null
        autoPlayRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        autoPlayRetryRunnable = null
        lastAutoPlayAttemptTimestamp = 0L
        autoPlayRetryCount = 0
        autoPlayTargetPackage = null
        // 1. 立即单方面乐观置位 STATE_PLAYING，抢占 Android Auto 显示优先权，绝不给第三方黑卡片留任何缝隙
        optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)

        val defaultPkg = FahrmonyConfig.getDefaultPlayer(context)
        val finalTarget = if (!targetPackage.isNullOrBlank()) targetPackage else defaultPkg

        val targetCtrl = if (finalTarget.isNotBlank()) {
            allControllersCompat.firstOrNull { isPackageMatch(it.packageName, finalTarget) }
        } else null

        // 1. 若目标播放器已有控制器就绪，无条件以目标播放器为准起播
        if (targetCtrl != null) {
            stickyActivePackage = targetCtrl.packageName
            activeControllerCompat = targetCtrl
            notifyInfo(immediate = true)
            try {
                targetCtrl.transportControls?.play()
            } catch (ignored: Exception) {}
            return true
        }

        // 2. 若指定了目标包名但该目标未启动，只唤醒目标播放器，绝不串流起播其他应用
        if (finalTarget.isNotBlank()) {
            stickyActivePackage = finalTarget
            activeControllerCompat = null
            notifyInfo(immediate = true)
            wakeUpPlayer(context, finalTarget)
            return true
        }

        // 3. 兜底回退：无目标包名且存在活跃控制器时
        val controller = activeControllerCompat
        if (controller != null) {
            try {
                controller.transportControls?.play()
            } catch (ignored: Exception) {}
            return true
        }

        return true
    }

    fun isFahrmonyInForeground(context: Context): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val processes = am?.runningAppProcesses ?: return false
            val mainPkg = context.packageName
            for (p in processes) {
                if (p.processName == mainPkg && p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return true
                }
            }
        } catch (ignored: Exception) {}
        return false
    }

    fun wakeUpPlayer(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        stickyActivePackage = packageName
        lastWakeUpTimestamp = System.currentTimeMillis()
        lastWakeUpPackage = packageName

        val inForeground = isFahrmonyInForeground(context)
        FahrmonyLogBuffer.addLog("PROBE_WAKE", "发起应用唤醒", "目标: $packageName, 前台状态: $inForeground, 启动基准时间: ${lastWakeUpTimestamp}ms", packageName)

        if (inForeground) {
            // 逻辑 A: Fahrmony 已在前台，直接通过 startActivity 唤起目标播放器并播放
            FahrmonyLogBuffer.addLog("WAKE", "前台直拉播放器", "Fahrmony处于前台，直接拉起目标应用", packageName)
            try {
                var launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent == null && isPackageMatch(packageName, "com.kugou.android")) {
                    launchIntent = context.packageManager.getLaunchIntentForPackage("com.kugou.android")
                }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launchIntent)
                }
            } catch (e: Exception) {
                FahrmonyLogBuffer.addLog("WAKE", "拉起播放器异常", "${e.message}", packageName)
            }
        } else {
            // 逻辑 B: Fahrmony 不在前台，先打开 Fahrmony，然后由 Fahrmony (MainActivity) 去 startActivity 唤起默认播放器并播放
            FahrmonyLogBuffer.addLog("WAKE", "先打开Fahrmony", "Fahrmony不在前台，先拉起主应用建立控制权", packageName)
            try {
                val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (mainIntent != null) {
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    mainIntent.putExtra("EXTRA_CHAIN_LAUNCH_PKG", packageName)
                    context.startActivity(mainIntent)
                }
            } catch (e: Exception) {
                FahrmonyLogBuffer.addLog("WAKE", "拉起Fahrmony异常", "${e.message}", packageName)
            }
        }

        // 待目标界面建立后下发播放按键脉冲触发自动起播 (必须满足系统音频尚未起播的前提，杜绝反向切换成暂停)
        mainHandler.postDelayed({
            try {
                val elapsed = System.currentTimeMillis() - lastWakeUpTimestamp
                val hasController = allControllersCompat.any { isPackageMatch(it.packageName, packageName) }
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val isAudioActive = am?.isMusicActive == true
                val isControllerPlaying = activeControllerCompat?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING

                FahrmonyLogBuffer.addLog(
                    "PROBE_500MS",
                    "500ms按键派发检查",
                    "距拉起耗时: ${elapsed}ms, 控制器就绪: $hasController, 音频发声: $isAudioActive, 当前播放态: $isControllerPlaying",
                    packageName
                )

                if (!isAudioActive && !isControllerPlaying) {
                    am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    am?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                }
            } catch (ignored: Exception) {}
        }, 500L)
    }

    fun switchToPackage(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        stickyActivePackage = packageName
        optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)
        val target = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, packageName) }
        if (target != null) {
            activeControllerCompat = target
            notifyInfo(immediate = true)
            try {
                target.transportControls?.play()
            } catch (ignored: Exception) {}
        }
        wakeUpPlayer(context, packageName)

        // 延迟 800ms 检查并再次尝试下发指令
        mainHandler.postDelayed({
            val elapsed = System.currentTimeMillis() - lastWakeUpTimestamp
            val currentControllers = allControllersCompat.map { it.packageName }.joinToString(",")
            val newTarget = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, packageName) }

            FahrmonyLogBuffer.addLog(
                "PROBE_800MS",
                "800ms兜底探测检查",
                "距拉起耗时: ${elapsed}ms, 目标控制器命中: ${newTarget != null}, 现有控制器列表: [${if (currentControllers.isBlank()) "无" else currentControllers}]",
                packageName
            )

            if (newTarget != null) {
                activeControllerCompat = newTarget
                notifyInfo(immediate = true)
                try {
                    newTarget.transportControls?.play()
                } catch (ignored: Exception) {}
            }
        }, 800L)
    }

    fun onCarConnected(context: Context) {
        // 重置元数据缓存键，确保无论是初次连接还是断开重连，都强制向车机推送完整的封面大图与曲目信息
        lastSyncedTrackKey = null
        currentTrackArtworkBitmap = null
        val now = System.currentTimeMillis()
        if (now - lastCarConnectedTimestamp < 1500L) {
            return
        }
        lastCarConnectedTimestamp = now
        handshakeProtectionUntil = now + 1200L
        disconnectSuppressionUntil = 0L

        FahrmonyLogBuffer.addLog("CAR_CONNECT", "车机握手", "收到MediaBrowser握手", "确立Fahrmony主导权")
        pushMirrorState()

        if (FahrmonyConfig.isAutoPlayEnabled(context)) {
            // 立即置位 STATE_PLAYING 抢占车机 Coolwalk 控件主导权，杜绝第三方视图在连上时抢占
            optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)
            handleAutoPlay(context)
        } else {
            // 若未启用自动播放但后台播放器已在发声，立即强制推流接管卡片
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am?.isMusicActive == true) {
                optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)
                pushMirrorState()
            }
        }

        // 强力会话锁定：延迟 +400ms 与 +1200ms 执行两次主导权巩固，防止网易云/QQ音乐后台活跃会话在握手时夺权
        mainHandler.postDelayed({ pushMirrorState() }, 400L)
        mainHandler.postDelayed({ pushMirrorState() }, 1200L)
    }

    private var lastCarDisconnectedTimestamp = 0L
    private var disconnectSuppressionUntil = 0L

    private fun requestTransientAudioFocusMute(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { /* no-op */ }
                    .build()
                val res = am.requestAudioFocus(focusRequest)
                FahrmonyLogBuffer.addLog("AUDIO_FOCUS", "音频焦点熔断", "请求瞬态焦点结果: $res", "硬件级掐断声卡输出")
                mainHandler.postDelayed({
                    try {
                        am.abandonAudioFocusRequest(focusRequest)
                    } catch (ignored: Exception) {}
                }, 300L)
            } else {
                @Suppress("DEPRECATION")
                val res = am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                FahrmonyLogBuffer.addLog("AUDIO_FOCUS", "音频焦点熔断", "请求瞬态焦点结果: $res", "硬件级掐断声卡输出")
                mainHandler.postDelayed({
                    try {
                        @Suppress("DEPRECATION")
                        am.abandonAudioFocus(null)
                    } catch (ignored: Exception) {}
                }, 300L)
            }
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("AUDIO_FOCUS", "音频焦点熔断异常", "${e.message}", "")
        }
    }

    private fun pauseAllPlayers() {
        optimisticSyncState(PlaybackStateCompat.STATE_PAUSED)
        // 1. 先暂停当前活跃 controller
        try {
            activeControllerCompat?.transportControls?.pause()
        } catch (ignored: Exception) {}
        // 2. 遍历所有已知 controller，若有处于播放态的均下发 pause，确保对网易云、QQ音乐、酷狗、喜马拉雅等所有播放器生效
        for (ctrl in allControllersCompat) {
            try {
                if (ctrl.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                    ctrl.transportControls?.pause()
                }
            } catch (ignored: Exception) {}
        }
    }

    fun onCarDisconnected() {
        val now = System.currentTimeMillis()
        if (now - lastCarDisconnectedTimestamp < 1000L) {
            return
        }
        lastCarDisconnectedTimestamp = now
        // 设立 4000ms 断连压制窗口，拦截冷启动时前台 Activity 重新聚焦/音频重构引发的自发起播
        disconnectSuppressionUntil = now + 4000L

        FahrmonyLogBuffer.addLog("CAR_DISCONNECT", "车机断连", "收到物理断连事件", "执行手机端物理静音与全播放器双脉冲暂停")

        // 1. 手机端物理级内核音频焦点抢占 (直接从 OS 音频驱动层剥夺焦点并强行静音，对系统所有播放器通用生效)
        appContext?.let { requestTransientAudioFocusMute(it) }

        // 2. 跨进程 IPC 全量下发暂停：遍历所有活跃会话，确保所有播放器无一遗漏地进入暂停态
        pauseAllPlayers()

        // 3. 延迟 250ms 再次下发二次确认暂停，跨越物理拔线与底层 AudioTrack 声道重构震荡窗口
        mainHandler.postDelayed({
            pauseAllPlayers()
        }, 250L)
    }

    // 车载握手成功后，由 CarAppService、MediaBrowser 或手机端调用的自动播放恢复引擎 (带 5000ms 冷却保护，杜绝双握手并发冲突)
    fun handleAutoPlay(context: Context) {
        if (!FahrmonyConfig.isAutoPlayEnabled(context)) {
            FahrmonyLogBuffer.addLog("AUTO_PLAY", "自动播放策略", "策略未启用", "用户在设置中关闭了车载连接自动播放")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastAutoPlayTimestamp < 5000L) {
            FahrmonyLogBuffer.addLog("AUTO_PLAY", "自动播放抑制", "冷却保护中", "5秒内已响应过车载连接握手，忽略重复并发触发")
            return
        }
        lastAutoPlayTimestamp = now

        // 立即向车机乐观置位 STATE_PLAYING，确立 Fahrmony 为当前活跃媒体组件
        optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)

        val defaultPkg = FahrmonyConfig.getDefaultPlayer(context)

        // 延迟 500ms 等待车机音频通道与硬件音频焦点协商建立
        mainHandler.postDelayed({
            val controller = activeControllerCompat
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val isAudioActive = am?.isMusicActive == true
            val isControllerPlaying = controller?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING

            if (!isControllerPlaying && !isAudioActive) {
                if (defaultPkg.isNotBlank() && controller != null && controller.packageName == defaultPkg) {
                    lastAutoPlayAttemptTimestamp = System.currentTimeMillis()
                    autoPlayTargetPackage = defaultPkg
                    autoPlayRetryCount = 0
                    controller.transportControls?.play()
                    FahrmonyLogBuffer.addLog("AUTO_PLAY", "自动恢复播放", "已向默认播放器下发指令", "应用: $defaultPkg")
                } else if (defaultPkg.isNotBlank()) {
                    FahrmonyLogBuffer.addLog("AUTO_PLAY", "自动恢复播放", "定向切换并拉起首选播放器", "首选: $defaultPkg, 残留活跃: ${controller?.packageName}")
                    pendingAutoPlayPackage = defaultPkg
                    pendingAutoPlayDeadline = System.currentTimeMillis() + 8000L
                    switchToPackage(context, defaultPkg)
                }
            }
            // 再次强化推送一次，巩固 Fahrmony 的车机主导地位
            pushMirrorState()
        }, 500L)
    }
}
