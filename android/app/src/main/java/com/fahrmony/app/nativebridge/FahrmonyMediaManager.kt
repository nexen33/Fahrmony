package com.fahrmony.app.nativebridge

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.media.MediaBrowserServiceCompat
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.os.ResultReceiver
import android.service.notification.NotificationListenerService
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import com.fahrmony.app.nativebridge.lyrics.UnifiedLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.cache.LyricCache
import com.fahrmony.app.nativebridge.lyrics.parser.LrcParser
import com.fahrmony.app.nativebridge.lyrics.provider.LunaLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.sync.LyricSyncEngine
import com.fahrmony.app.nativebridge.lyrics.sync.LyricSyncListener
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult
import com.fahrmony.app.nativebridge.playmode.IPlayModePersister
import com.fahrmony.app.nativebridge.playmode.SharedPreferencesPlayModePersister
import com.fahrmony.app.nativebridge.playmode.IPlayModeHeuristicDetector
import com.fahrmony.app.nativebridge.playmode.NetEaseHeuristicDetector
import com.fahrmony.app.nativebridge.playmode.QQMusicPlayModeAdapter
import com.fahrmony.app.nativebridge.playmode.LunaMusicPlayModeAdapter
import com.fahrmony.app.nativebridge.playmode.BodianMusicPlayModeAdapter
import com.fahrmony.app.nativebridge.playmode.KuwoMusicPlayModeAdapter
import com.fahrmony.app.nativebridge.playmode.KugouMusicPlayModeAdapter
import com.fahrmony.app.nativebridge.lyrics.provider.BodianLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.sanitizer.LunaMetadataSanitizer
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
        // 场景 A 智能融合：若概念版未被设为自定义音源，且预置酷狗音乐开关开启，自动视为预置支持
        if (packageName == "com.kugou.android.lite") {
            return ctx == null || FahrmonyConfig.isAppEnabled(ctx, "kugou.service")
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
    const val ACTION_CAR_SHUFFLE = "com.fahrmony.app.ACTION_CAR_SHUFFLE"
    const val ACTION_TOGGLE_LYRICS = "com.fahrmony.app.ACTION_TOGGLE_LYRICS"
    const val ACTION_TOGGLE_ARTWORK_MODE = "com.fahrmony.app.ACTION_TOGGLE_ARTWORK_MODE"
    private val lyricSyncEngine = LyricSyncEngine()

    private var lastRawSourceGenId: Int = -1
    private var lastRawSourceUri: String? = null
    private var cachedRawArtworkBitmap: Bitmap? = null
    private var lastLyricsSelfHealingTimestamp: Long = 0L

    // 播客类应用：明确排除模式控制适配，不投递任何循环/随机指令，保持原生体验
    private val EXCLUDED_PLAYBACK_MODE_PACKAGES = setOf(
        "com.ximalaya.ting.android",
        "app.podcast.cosmos"
    )

    data class PlaybackModeCapability(
        val hasStandardRepeat: Boolean,
        val hasStandardShuffle: Boolean,
        val customRepeatAction: PlaybackStateCompat.CustomAction?,
        val customShuffleAction: PlaybackStateCompat.CustomAction?
    )

    sealed class RepeatProbeResult {
        object Unsupported : RepeatProbeResult()
        object Standard : RepeatProbeResult()
        data class Custom(val actionName: String, val displayName: String) : RepeatProbeResult()
    }

    fun resolveCustomRepeatState(name: String?): CustomRepeatState? {
        if (name == null || name == "播放模式") return null
        return when {
            name.contains("心动") -> CustomRepeatState.HEARTBEAT
            name.contains("单曲") -> CustomRepeatState.REPEAT_ONE
            name.contains("随机") -> CustomRepeatState.SHUFFLE
            name.contains("列表") || name.contains("顺序") || name.contains("循环") -> CustomRepeatState.REPEAT_ALL
            else -> null
        }
    }

    private var customRepeatState = CustomRepeatState.REPEAT_ALL
    private var playModePersister: IPlayModePersister? = null
    private val heuristicDetector: IPlayModeHeuristicDetector = NetEaseHeuristicDetector()

    fun getOrCreatePersister(context: Context): IPlayModePersister {
        var p = playModePersister
        if (p == null) {
            p = SharedPreferencesPlayModePersister(context)
            playModePersister = p
        }
        return p
    }

    /**
     * 单边校准播放模式 (严禁向第三方音乐 App 透传 Action，仅更新车机镜像与本地持久化)
     */
    fun calibrateMode(context: Context, packageName: String, modeState: CustomRepeatState) {
        val prev = customRepeatState
        customRepeatState = modeState
        lastCarModeToggleTimestamp = System.currentTimeMillis() // 刷新防回环防抖
        lastNeteasePulseTimestamp = System.currentTimeMillis()
        getOrCreatePersister(context).saveMode(packageName, modeState)
        FahrmonyIpcBridge.notifyPlayModeChanged(packageName, modeState)
        val logMsg = "[CALIBRATE_MODE] 触发单边播放模式校准: [${prev.title}] -> [${customRepeatState.title}], 目标应用: $packageName (严禁透传Action)"
        android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
        android.util.Log.i("FAHRMONY_PROBE", logMsg)
        FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "单边模式校准", "[${prev.title}] -> [${customRepeatState.title}]", packageName)
        pushMirrorState()
    }

    fun getCustomRepeatState(packageName: String): CustomRepeatState {
        val activePkg = activeControllerCompat?.packageName
        if (activePkg != null) {
            if ((packageName == "com.netease.cloudmusic" && activePkg == "com.netease.cloudmusic") ||
                ((packageName == "com.kugou.android" || packageName == "kugou.service") && KugouMusicPlayModeAdapter.isMain(activePkg))) {
                return customRepeatState
            }
        }
        val ctx = appContext
        if (ctx != null) {
            val persister = getOrCreatePersister(ctx)
            if (persister.isPersistenceSupported(packageName)) {
                val saved = persister.getSavedMode(packageName)
                if (saved != null) return saved
            }
        }
        return customRepeatState
    }

    fun probePlaybackModeCapability(controller: MediaControllerCompat?): PlaybackModeCapability {
        if (controller == null) return PlaybackModeCapability(false, false, null, null)
        if (EXCLUDED_PLAYBACK_MODE_PACKAGES.contains(controller.packageName)) {
            return PlaybackModeCapability(false, false, null, null)
        }
        val state = controller.playbackState ?: return PlaybackModeCapability(false, false, null, null)

        val actions = state.actions
        val hasStandardRepeat = (actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L
        val hasStandardShuffle = (actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L

        var customRepeat: PlaybackStateCompat.CustomAction? = null
        var customShuffle: PlaybackStateCompat.CustomAction? = null

        val customActions = state.customActions
        if (!customActions.isNullOrEmpty()) {
            for (ca in customActions) {
                val act = ca.action ?: ""
                val name = ca.name?.toString() ?: ""
                val isShuffle = act.contains("随机") || act.contains("shuffle", ignoreCase = true) || act.contains("random", ignoreCase = true) ||
                        name.contains("随机") || name.contains("shuffle", ignoreCase = true) || name.contains("random", ignoreCase = true)
                val isRepeat = act.contains("模式") || act.contains("repeat", ignoreCase = true) || act.contains("loop", ignoreCase = true) ||
                        act.contains("循环") || act.contains("单曲") ||
                        name.contains("模式") || name.contains("repeat", ignoreCase = true) || name.contains("loop", ignoreCase = true) ||
                        name.contains("循环") || name.contains("单曲")

                if (isShuffle && !hasStandardShuffle && customShuffle == null) {
                    customShuffle = ca
                } else if (isRepeat && !hasStandardRepeat && customRepeat == null) {
                    customRepeat = ca
                }
            }
        }
        // 针对 QQ 音乐与汽水音乐合成虚拟 CustomAction 能力
        if (customRepeat == null) {
            if (QQMusicPlayModeAdapter.isSupported(controller.packageName)) {
                customRepeat = QQMusicPlayModeAdapter.createCustomAction()
            } else if (LunaMusicPlayModeAdapter.isSupported(controller.packageName)) {
                if (LunaMusicPlayModeAdapter.isControlSupported(controller)) {
                    customRepeat = LunaMusicPlayModeAdapter.createCustomAction()
                }
            } else if (BodianMusicPlayModeAdapter.isSupported(controller.packageName)) {
                if (BodianMusicPlayModeAdapter.isControlSupported(controller)) {
                    customRepeat = BodianMusicPlayModeAdapter.createCustomAction()
                }
            } else if (KuwoMusicPlayModeAdapter.isSupported(controller.packageName)) {
                if (KuwoMusicPlayModeAdapter.isControlSupported(controller)) {
                    customRepeat = KuwoMusicPlayModeAdapter.createCustomAction()
                }
            } else if (KugouMusicPlayModeAdapter.isSupported(controller.packageName)) {
                if (KugouMusicPlayModeAdapter.isControlSupported(controller)) {
                    customRepeat = KugouMusicPlayModeAdapter.createCustomAction(controller.packageName)
                }
            }
        }
        if (!hasStandardRepeat && customRepeat != null) {
            val ctx = appContext
            if (ctx != null) {
                val persister = getOrCreatePersister(ctx)
                if (persister.isPersistenceSupported(controller.packageName)) {
                    val saved = persister.getSavedMode(controller.packageName)
                    if (saved != null) {
                        customRepeatState = saved
                    }
                }
            }
        }
        val cap = PlaybackModeCapability(hasStandardRepeat, hasStandardShuffle, customRepeat, customShuffle)
        val repAction = customRepeat?.action ?: "<null>"
        val shfAction = customShuffle?.action ?: "<null>"
        val capLog = "[PROBE_CAPABILITY] pkg: ${controller.packageName}, hasStdRepeat: $hasStandardRepeat, hasStdShuffle: $hasStandardShuffle, customRepeat: $repAction, customShuffle: $shfAction"
        android.util.Log.i("Fahrmony_PLAYMODE", capLog)
        android.util.Log.i("FAHRMONY_PROBE", capLog)
        return cap
    }

    fun probeRepeatCapability(controller: MediaControllerCompat?): RepeatProbeResult {
        val cap = probePlaybackModeCapability(controller)
        if (cap.hasStandardRepeat) return RepeatProbeResult.Standard
        if (cap.customRepeatAction != null) {
            val act = cap.customRepeatAction.action ?: ""
            val name = cap.customRepeatAction.name?.toString() ?: "播放模式"
            return RepeatProbeResult.Custom(act, name)
        }
        return RepeatProbeResult.Unsupported
    }

    // [PLAYMODE_PROBE_CLEANUP] 已由精准 Deep Probe 取代并清理冗余旧探针缓存

    private fun dumpBundleRecursive(bundle: Bundle?, maxDepth: Int = 3, currentDepth: Int = 0): List<String> {
        if (bundle == null || currentDepth >= maxDepth) return emptyList()
        val results = mutableListOf<String>()
        try {
            val keys = bundle.keySet()?.sorted() ?: return emptyList()
            for (k in keys) {
                val value = try { bundle.get(k) } catch (e: Exception) { "<err>" }
                if (value is Bundle) {
                    val subCount = try { value.keySet()?.size ?: 0 } catch (e: Exception) { 0 }
                    results.add("$k -> [嵌套Bundle (共 $subCount 项)]:")
                    val sub = dumpBundleRecursive(value, maxDepth, currentDepth + 1)
                    sub.forEach { results.add("    $it") }
                } else {
                    val strVal = when (value) {
                        is Bitmap -> "[Bitmap: ${value.width}x${value.height}]"
                        is IntArray -> value.contentToString()
                        is LongArray -> value.contentToString()
                        is Array<*> -> value.contentToString()
                        else -> value.toString()
                    }
                    results.add("$k = $strVal")
                }
            }
        } catch (e: Exception) {
            results.add("<Bundle解包异常: ${e.message}>")
        }
        return results
    }

    private var lastDeepProbeSummary: String? = null

    fun logDeepPlaybackProbe(controller: MediaControllerCompat?, state: PlaybackStateCompat?, eventSource: String) {
        if (controller == null) return
        try {
            val pkg = controller.packageName

            // 1. Controller 会话级 Extras (重点递归穿透排查 UCAR / HiCar / 厂商私有 Bundle 存放区)
            val cExtras = try { controller.extras } catch (ignored: Throwable) { null }
            val cExtrasList = dumpBundleRecursive(cExtras)
            val cExtrasSummary = cExtrasList.joinToString(";")

            // 2. PlaybackState 级 Extras (递归穿透)
            val sExtras = try { state?.extras } catch (ignored: Throwable) { null }
            val sExtrasList = dumpBundleRecursive(sExtras)
            val sExtrasSummary = sExtrasList.joinToString(";")

            // 3. Queue 队列信息与当前项
            val qTitle = try { controller.queueTitle?.toString() ?: "<null>" } catch (ignored: Throwable) { "<err>" }
            val activeQueueId = state?.activeQueueItemId ?: -1L
            val qSize = try { controller.queue?.size ?: 0 } catch (ignored: Throwable) { 0 }

            // 4. CustomActions 完整元数据 (强化防护：外层与内层双重隔离，严禁第三方应用专有 Parcelable 解包导致 ClassNotFoundException 击穿主线程)
            val customActions = state?.customActions ?: emptyList()
            val caSummary = customActions.joinToString(";") { ca ->
                val extKeys = try {
                    ca.extras?.keySet()?.sorted()?.joinToString(",") { k ->
                        val v = try { ca.extras?.get(k)?.toString() ?: "null" } catch (ignored: Throwable) { "<err>" }
                        "$k=$v"
                    } ?: ""
                } catch (ignored: Throwable) {
                    "<extras_err>"
                }
                "${ca.action}:${ca.name}:${ca.icon}:$extKeys"
            }

            // 5. 标准 Repeat & Shuffle 与 Actions 掩码
            val repeatMode = controller.repeatMode
            val shuffleMode = controller.shuffleMode
            val actions = state?.actions ?: 0L
            val hasStdRepeat = (actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L
            val hasStdShuffle = (actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L
            val rawState = state?.state ?: PlaybackStateCompat.STATE_NONE
            val stateName = when (rawState) {
                PlaybackStateCompat.STATE_PLAYING -> "PLAYING(3)"
                PlaybackStateCompat.STATE_PAUSED -> "PAUSED(2)"
                PlaybackStateCompat.STATE_STOPPED -> "STOPPED(1)"
                PlaybackStateCompat.STATE_BUFFERING -> "BUFFERING(6)"
                PlaybackStateCompat.STATE_NONE -> "NONE(0)"
                else -> "$rawState"
            }

            // 6. Metadata 元数据完整键值字典 (全量扫描，强化 keySet 异常隔离)
            val metadata = try { controller.metadata } catch (ignored: Throwable) { null }
            val metaList = try {
                metadata?.keySet()?.sorted()?.mapNotNull { k ->
                    val v = try {
                        metadata.getString(k)
                            ?: metadata.getLong(k).toString()
                            ?: metadata.getRating(k)?.toString()
                            ?: if (metadata.getBitmap(k) != null) "[Bitmap: ${metadata.getBitmap(k)?.width}x${metadata.getBitmap(k)?.height}]" else "[Object]"
                    } catch (ignored: Throwable) {
                        null
                    }
                    if (v != null) "$k=$v" else null
                } ?: emptyList()
            } catch (ignored: Throwable) {
                emptyList<String>()
            }
            val metaSummary = metaList.joinToString(";")

            // 核心去重消融优化：将 eventSource 移出指纹比对，确保不同触发源只要数据实质未变，绝对不刷屏！
            val currentSummary = "$pkg|$rawState|$actions|$repeatMode|$shuffleMode|$qTitle|$activeQueueId|$caSummary|$cExtrasSummary|$sExtrasSummary|$metaSummary"
            if (currentSummary == lastDeepProbeSummary) {
                return
            }
            lastDeepProbeSummary = currentSummary

            val log = StringBuilder().apply {
                append("\n==================== [PLAYMODE_DEEP_PROBE] ====================\n")
                append("【触发源】: $eventSource | 【应用】: $pkg | 【运控状态】: $stateName\n")
                append("├── [Actions 掩码] 0x${actions.toString(16)} (hasStdRepeat=$hasStdRepeat, hasStdShuffle=$hasStdShuffle)\n")
                append("├── [标准模式] repeatMode: $repeatMode, shuffleMode: $shuffleMode\n")
                append("├── [队列状态] queueTitle: \"$qTitle\", activeQueueItemId: $activeQueueId, queueSize: $qSize\n")
                append("├── [Controller Extras (全局会话级)] (共 ${cExtrasList.size} 条记录):\n")
                if (cExtrasList.isEmpty()) {
                    append("│   └── (无 Controller Extras)\n")
                } else {
                    cExtrasList.forEach { append("│   ├── $it\n") }
                }
                append("├── [PlaybackState Extras (状态级)] (共 ${sExtrasList.size} 条记录):\n")
                if (sExtrasList.isEmpty()) {
                    append("│   └── (无 State Extras)\n")
                } else {
                    sExtrasList.forEach { append("│   ├── $it\n") }
                }
                append("├── [CustomActions 详情] (共 ${customActions.size} 个):\n")
                if (customActions.isEmpty()) {
                    append("│   └── (无 CustomActions)\n")
                } else {
                    customActions.forEachIndexed { idx, ca ->
                        val extraStr = try {
                            val keys = ca.extras?.keySet()
                            if (keys != null && !keys.isEmpty()) {
                                val pairs = mutableListOf<String>()
                                for (k in keys) {
                                    val v = try { ca.extras?.get(k)?.toString() ?: "null" } catch (ignored: Throwable) { "<err>" }
                                    pairs.add("$k=$v")
                                }
                                " (extras: {" + pairs.joinToString(", ") + "})"
                            } else {
                                ""
                            }
                        } catch (ignored: Throwable) {
                            " (extras: <unreadable>)"
                        }
                        append("│   ├── Action[$idx]: id=\"${ca.action}\", name=\"${ca.name}\", iconRes=${ca.icon}$extraStr\n")
                    }
                }
                append("├── [Metadata 关键参数 (共 ${metaList.size} 项)]:\n")
                if (metaList.isEmpty()) {
                    append("│   └── (无 Metadata)\n")
                } else {
                    metaList.forEach { append("│   ├── $it\n") }
                }
                append("================================================================")
            }.toString()

            android.util.Log.i("Fahrmony_PLAYMODE", log)
            android.util.Log.i("FAHRMONY_PROBE", log)
        } catch (e: Throwable) {
            android.util.Log.w("FAHRMONY_PROBE", "logDeepPlaybackProbe 安全捕获异常: ${e.message}")
        }
    }

    // 车机端主动触发模式切换的时间戳，用于防止车机自身动作产生的事件回环
    private var lastCarModeToggleTimestamp = 0L
    // 网易云手机端模式切换脉冲守卫追踪变量
    private var lastNeteasePulseTimestamp = 0L
    private var lastNeteaseStateTimestamp = 0L
    private var lastNeteaseStateTransitionTimestamp = 0L
    private var lastNeteaseTrackPosition = 0L
    private var lastNeteaseTrackDuration = 0L
    private var lastNeteasePlaybackState = PlaybackStateCompat.STATE_NONE
    // 启发式自愈探测曲目追踪变量
    private var lastObservedMediaId: String? = null
    private var lastObservedTrackDuration: Long = 0L
    private var lastObservedTrackPosition: Long = 0L

    private fun checkNeteasePhoneModePulse(controller: MediaControllerCompat, state: PlaybackStateCompat?): Boolean {
        if (controller.packageName != "com.netease.cloudmusic" || state == null) return false
        // 守卫 0: 车机必须处于真实连接态，未连接时不作步进
        if (!FahrmonyMediaBrowserService.isCarConnected) return false

        val now = System.currentTimeMillis()
        // 守卫 1: 排除车机连接与断连窗口 (3000ms 握手与断播震荡期，杜绝过渡状态误判)
        if (now - lastCarConnectedTimestamp < 3000L || now - lastCarDisconnectedTimestamp < 3000L) {
            lastNeteaseStateTimestamp = now
            lastNeteaseTrackPosition = state.position
            return false
        }

        // 守卫 2: 排除车机自身在 1200ms 内触发的动作回环
        if (now - lastCarModeToggleTimestamp < 1200L) {
            lastNeteaseStateTimestamp = now
            lastNeteaseTrackPosition = state.position
            return false
        }

        // 守卫 3: 状态允许稳定在 STATE_PLAYING 或 STATE_PAUSED，排除起播/缓冲等不稳定中间态
        val currentState = state.state
        if (currentState != PlaybackStateCompat.STATE_PLAYING && currentState != PlaybackStateCompat.STATE_PAUSED) {
            lastNeteasePlaybackState = currentState
            lastNeteaseStateTimestamp = now
            lastNeteaseTrackPosition = state.position
            return false
        }

        // 守卫 3.1: 状态转移不变性 (State-Transition Invariance)
        // 手机端点击切换播放模式时，绝不会改变播放器状态（播放中点击仍为播放，暂停中点击仍为暂停）。
        // 凡是发生状态跳变（如从 STATE_PLAYING 变为 STATE_PAUSED，或反之），必然是下车断连、物理拔线、或者用户按下播放/暂停，绝不可能是模式切换脉冲。
        if (lastNeteasePlaybackState != currentState) {
            lastNeteasePlaybackState = currentState
            lastNeteaseStateTimestamp = now
            lastNeteaseStateTransitionTimestamp = now
            lastNeteaseTrackPosition = state.position
            val metaDur = controller.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
            if (metaDur > 0L) {
                lastNeteaseTrackDuration = metaDur
            }
            return false
        }

        // 守卫 4: 模式点击防抖窗口（单次点击防抖 400ms）
        if (now - lastNeteasePulseTimestamp < 400L) {
            return false
        }

        val elapsedReal = now - lastNeteaseStateTimestamp
        val currentPos = state.position
        val currentDuration = controller.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

        // 稳健基线自愈：若基线时长尚未初始化且当前曲目时长有效，立即就地对齐，杜绝起播首按沦为时长校准牺牲品
        if (lastNeteaseTrackDuration == 0L && currentDuration > 0L) {
            lastNeteaseTrackDuration = currentDuration
        }

        var isPulse = false
        if (lastNeteaseTrackDuration == currentDuration && currentDuration > 0L) {
            if (currentState == PlaybackStateCompat.STATE_PAUSED) {
                // 排除刚从播放跌入暂停的瞬态震荡 (1200ms 物理缓冲期，防止断播/拔线产生的二次暂停事件误判为用户点按)
                if (now - lastNeteaseStateTransitionTimestamp >= 1200L && elapsedReal >= 150L) {
                    isPulse = true
                }
            } else if (elapsedReal >= 150L) {
                val progressDiff = Math.abs((currentPos - lastNeteaseTrackPosition) - elapsedReal)
                // 排除长距离拖动进度条（手动 Seek 跳变；长间隔播放时放宽时钟自然漂移门限至 6000ms）
                val maxSeekTolerance = if (elapsedReal > 5000L) 6000L else 2500L
                if (progressDiff < maxSeekTolerance) {
                    isPulse = true
                }
            }
        }

        lastNeteaseStateTimestamp = now
        lastNeteaseTrackPosition = currentPos
        if (currentDuration > 0L) {
            lastNeteaseTrackDuration = currentDuration
        }
        lastNeteasePlaybackState = currentState

        if (isPulse) {
            lastNeteasePulseTimestamp = now
            val prev = customRepeatState
            customRepeatState = customRepeatState.next(isNetEase = true)
            appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, customRepeatState) }
            FahrmonyIpcBridge.notifyPlayModeChanged("com.netease.cloudmusic", customRepeatState)
            val pulseLog = "[NETEASE_MODE_PULSE] 捕获手机端模式切换脉冲: [${prev.title}] -> [${customRepeatState.title}] (距上次操作=${elapsedReal}ms, state=$currentState)"
            android.util.Log.i("Fahrmony_PLAYMODE", pulseLog)
            android.util.Log.i("FAHRMONY_PROBE", pulseLog)
            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "手机端模式脉冲步进", "[${prev.title}] -> [${customRepeatState.title}]", controller.packageName)
            pushMirrorState()
            return true
        }
        return false
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
    private var lastSyncedArtworkId: Int = -1
    private var lastSyncedArtworkMode: Boolean? = null
    private var pendingSkipRefreshRunnable1: Runnable? = null
    private var pendingSkipRefreshRunnable2: Runnable? = null
    // 毫秒级冷启动耗时可观测性探针
    private var lastWakeUpTimestamp = 0L
    private var lastWakeUpPackage: String? = null
    private val lastWakeUpAttemptMap = ConcurrentHashMap<String, Long>()
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
        // 酷狗音乐预置位 (kugou.service) 专属优先绑定正式版 (com.kugou.android)；
        // 仅当用户压根没有安装正式版时，才兼容概念版 (com.kugou.android.lite)，杜绝双版本共存时串台；
        // 两个实体包名之间绝不互相匹配。
        if (pkgA.equals("kugou.service", ignoreCase = true) || pkgB.equals("kugou.service", ignoreCase = true)) {
            val other = if (pkgA.equals("kugou.service", ignoreCase = true)) pkgB else pkgA
            if (other.equals("com.kugou.android", ignoreCase = true)) return true
            if (other.equals("com.kugou.android.lite", ignoreCase = true)) {
                val pm = appContext?.packageManager
                val hasMain = pm?.getLaunchIntentForPackage("com.kugou.android") != null
                return !hasMain
            }
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

    private var isMusicBroadcastReceiverRegistered = false
    private val musicBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val extras = intent.extras
            val extraKeys = extras?.keySet() ?: emptySet<String>()
            val extraDump = extraKeys.joinToString("; ") { k ->
                val v = try { extras?.get(k)?.toString() ?: "null" } catch (e: Exception) { "<err>" }
                val preview = if (v.length > 100) v.take(100) + "..." else v
                "$k=[$preview]"
            }
            val logMessage = StringBuilder().apply {
                append("\n==================== [PROBE_MUSIC_BROADCAST] ====================\n")
                append("【Action】: $action\n")
                append("├── Extra 数量: ${extraKeys.size}\n")
                if (extraKeys.isEmpty()) {
                    append("└── (无 Extras)\n")
                } else {
                    extraKeys.forEach { k ->
                        val v = try { extras?.get(k)?.toString() ?: "null" } catch (e: Exception) { "<err>" }
                        append("├── $k = $v\n")
                    }
                    append("└── 摘要: $extraDump\n")
                }
                append("=================================================================")
            }.toString()

            android.util.Log.i("Fahrmony_PLAYMODE", logMessage)
            android.util.Log.i("FAHRMONY_PROBE", logMessage)
            FahrmonyLogBuffer.addLog("PROBE_BROADCAST", "音乐系统广播捕获", "Action: $action, Extras: $extraDump", action)
        }
    }

    private fun registerMusicBroadcastProbe(context: Context) {
        if (isMusicBroadcastReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                // 标准 Android 播放状态广播
                addAction("com.android.music.playstatechanged")
                addAction("com.android.music.playbackcomplete")
                addAction("com.android.music.metachanged")
                addAction("com.android.music.queuechanged")
                // 网易云音乐已知外部/系统广播
                addAction("com.netease.cloudmusic.playstatechanged")
                addAction("com.netease.cloudmusic.metachanged")
                addAction("com.netease.cloudmusic.musicplayer.playmode")
                addAction("com.netease.cloudmusic.musicplayer.playstate")
                addAction("com.netease.cloudmusic.musicplayer.metachanged")
                // 常见主流车载与通用音频广播 (Kugou/QQ/咪咕/通用)
                addAction("com.tencent.qqmusic.ACTION_PLAY_STATE")
                addAction("com.kugou.android.music.playstatechanged")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(
                    musicBroadcastReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                context.applicationContext.registerReceiver(musicBroadcastReceiver, filter)
            }
            isMusicBroadcastReceiverRegistered = true
            FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "音乐系统广播探针就绪", "已监听 Android 及主流音乐应用状态广播")
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("SYSTEM", "MediaManager", "音乐广播探针注册异常", "${e.message}", "")
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        registerNoisyReceiver(context)
        registerMusicBroadcastProbe(context)

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
                    val sanitized = LunaMetadataSanitizer.resolve(controller.packageName, meta, rawTitle, rawArtist, null)
                    val trackKey = sanitized.trackKey
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
        // 允许封面指纹动态再核检，不强行清空 lastSyncedTrackKey 避免触发歌词闪烁
        lastSyncedArtworkId = -1
        lastRawSourceGenId = -1
        lastRawSourceUri = null
        cachedRawArtworkBitmap = null
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
                // 关键防污染：若曲目发生切换，坚决丢弃上一首旧封面，杜绝跨歌曲污染
                val isSameTrack = (nTitle.isBlank() || prev?.title.isNullOrBlank() || nTitle == prev?.title)
                val prevArt = prev?.artworkBmp
                val finalArt = if (notificationArtwork != null) {
                    if (prevArt != null && !prevArt.isRecycled && notificationArtwork.width == prevArt.width && notificationArtwork.height == prevArt.height && notificationArtwork.sameAs(prevArt)) {
                        prevArt
                    } else {
                        notificationArtwork
                    }
                } else if (isSameTrack) {
                    prevArt
                } else {
                    null
                }
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
            // 关键防污染：若曲目发生切换，坚决丢弃上一首旧封面，杜绝跨歌曲污染
            val isSameTrack = (nTitle.isBlank() || prev?.title.isNullOrBlank() || nTitle == prev?.title)
            val finalTitle = nTitle.ifBlank { prev?.title ?: "" }
            val finalArtist = nArtist.ifBlank { prev?.artist ?: "" }
            val finalAlbum = nSubText.ifBlank { prev?.album ?: "" }
            val prevArt = prev?.artworkBmp
            val finalArt = if (artwork != null) {
                if (prevArt != null && !prevArt.isRecycled && artwork.width == prevArt.width && artwork.height == prevArt.height && artwork.sameAs(prevArt)) {
                    prevArt
                } else {
                    artwork
                }
            } else if (isSameTrack) {
                prevArt
            } else {
                null
            }
            fallbackMetaMap[packageName] = FallbackMeta(finalTitle, finalArtist, finalAlbum, finalArt)
            android.util.Log.i("Fahrmony_CUSTOM", "[NOTIFY_META] pkg: $packageName, title: '$finalTitle', artist: '$finalArtist'")
            val targetPkg = pendingAutoPlayPackage
            if (!targetPkg.isNullOrBlank() && isPackageMatch(packageName, targetPkg)) {
                val targetCtrl = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, targetPkg) }
                if (targetCtrl != null) {
                    checkPendingAutoPlay(targetCtrl)
                } else {
                    appContext?.let { refresh(it) }
                }
            }
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
                try {
                    logDeepPlaybackProbe(controller, state, "onPlaybackStateChanged")
                } catch (ignored: Throwable) {}
                checkPendingAutoPlay(controller)
                // 拦截并触发网易云手机端模式脉冲步进
                checkNeteasePhoneModePulse(controller, state)
                if (state != null) {
                    lastObservedTrackPosition = state.position
                    if (heuristicDetector.isSupported(controller.packageName)) {
                        heuristicDetector.onPlaybackPositionUpdated(
                            controller,
                            mediaId = lastObservedMediaId,
                            duration = lastObservedTrackDuration,
                            position = state.position
                        )
                    }
                }
                if (activeControllerCompat == controller) {
                    lyricSyncEngine.updatePlaybackState(state)
                    browserServiceRef?.get()?.syncRepeatMode(controller.repeatMode)
                    browserServiceRef?.get()?.syncShuffleMode(controller.shuffleMode)
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
                try {
                    logDeepPlaybackProbe(controller, controller.playbackState, "onMetadataChanged")
                } catch (ignored: Throwable) {}
                checkPendingAutoPlay(controller)
                // 接入启发式自愈探测 (如网易云单曲循环识别)
                val currMediaId = metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                    ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                val currDuration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                if (controller.packageName == "com.netease.cloudmusic" && currDuration > 0L) {
                    lastNeteaseTrackDuration = currDuration
                }

                if (heuristicDetector.isSupported(controller.packageName)) {
                    val detected = heuristicDetector.onTrackChanged(
                        controller,
                        prevMediaId = lastObservedMediaId,
                        currMediaId = currMediaId,
                        prevDuration = lastObservedTrackDuration,
                        lastKnownPosition = lastObservedTrackPosition
                    )
                    if (detected != null && customRepeatState != detected) {
                        val prev = customRepeatState
                        customRepeatState = detected
                        lastCarModeToggleTimestamp = System.currentTimeMillis()
                        lastNeteasePulseTimestamp = System.currentTimeMillis()
                        appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, detected) }
                        val selfHealLog = "[HEURISTIC_AUTO_ALIGN] 切歌自愈探测生效: [${prev.title}] -> [${detected.title}], 应用: ${controller.packageName}"
                        android.util.Log.i("Fahrmony_PLAYMODE", selfHealLog)
                        android.util.Log.i("FAHRMONY_PROBE", selfHealLog)
                        FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "切歌自愈校准", "[${prev.title}] -> [${detected.title}]", controller.packageName)
                        pushMirrorState()
                    }
                }

                // 接入 QQ 音乐 UCAR 元数据播放模式真值直读
                if (QQMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    QQMusicPlayModeAdapter.resolvePlayMode(metadata)?.let { newMode ->
                        if (customRepeatState != newMode) {
                            val prev = customRepeatState
                            customRepeatState = newMode
                            lastCarModeToggleTimestamp = System.currentTimeMillis()
                            appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, newMode) }
                            val modeLog = "[QQ_MODE_METADATA] 捕获QQ音乐模式真值变更: [${prev.title}] -> [${newMode.title}], 目标应用: ${controller.packageName}"
                            android.util.Log.i("Fahrmony_PLAYMODE", modeLog)
                            android.util.Log.i("FAHRMONY_PROBE", modeLog)
                            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "QQ音乐模式真值同步", "[${prev.title}] -> [${newMode.title}]", controller.packageName)
                            pushMirrorState()
                        }
                    }
                }

                // 接入汽水音乐元数据播放模式真值直读与动态扫描
                if (LunaMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    LunaMusicPlayModeAdapter.resolvePlayMode(metadata)?.let { newMode ->
                        if (customRepeatState != newMode) {
                            val prev = customRepeatState
                            customRepeatState = newMode
                            lastCarModeToggleTimestamp = System.currentTimeMillis()
                            appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, newMode) }
                            val modeLog = "[LUNA_MODE_METADATA] 捕获汽水音乐模式真值变更: [${prev.title}] -> [${newMode.title}], 目标应用: ${controller.packageName}"
                            android.util.Log.i("Fahrmony_PLAYMODE", modeLog)
                            android.util.Log.i("FAHRMONY_PROBE", modeLog)
                            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "汽水音乐模式真值同步", "[${prev.title}] -> [${newMode.title}]", controller.packageName)
                            pushMirrorState()
                        }
                    }
                }

                // 接入波点音乐元数据播放模式真值直读与动态扫描
                if (BodianMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    BodianMusicPlayModeAdapter.resolvePlayMode(metadata)?.let { newMode ->
                        if (customRepeatState != newMode) {
                            val prev = customRepeatState
                            customRepeatState = newMode
                            lastCarModeToggleTimestamp = System.currentTimeMillis()
                            appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, newMode) }
                            val modeLog = "[BODIAN_MODE_METADATA] 捕获波点音乐模式真值变更: [${prev.title}] -> [${newMode.title}], 目标应用: ${controller.packageName}"
                            android.util.Log.i("Fahrmony_PLAYMODE", modeLog)
                            android.util.Log.i("FAHRMONY_PROBE", modeLog)
                            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "波点音乐模式真值同步", "[${prev.title}] -> [${newMode.title}]", controller.packageName)
                            pushMirrorState()
                        }
                    }
                }

                // 接入酷我音乐元数据播放模式真值直读与动态扫描
                if (KuwoMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    KuwoMusicPlayModeAdapter.resolvePlayMode(metadata)?.let { newMode ->
                        if (customRepeatState != newMode) {
                            val prev = customRepeatState
                            customRepeatState = newMode
                            lastCarModeToggleTimestamp = System.currentTimeMillis()
                            appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, newMode) }
                            val modeLog = "[KUWO_MODE_METADATA] 捕获酷我音乐模式真值变更: [${prev.title}] -> [${newMode.title}], 目标应用: ${controller.packageName}"
                            android.util.Log.i("Fahrmony_PLAYMODE", modeLog)
                            android.util.Log.i("FAHRMONY_PROBE", modeLog)
                            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "酷我音乐模式真值同步", "[${prev.title}] -> [${newMode.title}]", controller.packageName)
                            pushMirrorState()
                        }
                    }
                }

                // 接入酷狗音乐元数据播放模式真值直读与动态扫描 (针对概念版)
                if (KugouMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    KugouMusicPlayModeAdapter.resolvePlayMode(controller.packageName, metadata)?.let { newMode ->
                        if (customRepeatState != newMode) {
                            val prev = customRepeatState
                            customRepeatState = newMode
                            lastCarModeToggleTimestamp = System.currentTimeMillis()
                            appContext?.let { getOrCreatePersister(it).saveMode(controller.packageName, newMode) }
                            val modeLog = "[KUGOU_MODE_METADATA] 捕获酷狗音乐模式真值变更: [${prev.title}] -> [${newMode.title}], 目标应用: ${controller.packageName}"
                            android.util.Log.i("Fahrmony_PLAYMODE", modeLog)
                            android.util.Log.i("FAHRMONY_PROBE", modeLog)
                            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "酷狗音乐模式真值同步", "[${prev.title}] -> [${newMode.title}]", controller.packageName)
                            pushMirrorState()
                        }
                    }
                }

                lastObservedMediaId = currMediaId
                lastObservedTrackDuration = currDuration

                if (activeControllerCompat == controller) {
                    val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                    val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                    val sanitized = LunaMetadataSanitizer.resolve(controller.packageName, metadata, rawTitle, rawArtist, null)
                    val trackKey = sanitized.trackKey
                    appContext?.let { checkAndUpdateLyrics(it, controller, trackKey, metadata) }
                }
                notifyInfo(immediate = true)
            }

            override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
                try {
                    logDeepPlaybackProbe(controller, controller.playbackState, "onQueueChanged")
                } catch (ignored: Throwable) {}
                pushMirrorState()
            }

            override fun onQueueTitleChanged(title: CharSequence?) {
                try {
                    logDeepPlaybackProbe(controller, controller.playbackState, "onQueueTitleChanged")
                } catch (ignored: Throwable) {}
                pushMirrorState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val modeDesc = when (repeatMode) {
                    PlaybackStateCompat.REPEAT_MODE_NONE -> "NONE(关闭/不循环)"
                    PlaybackStateCompat.REPEAT_MODE_ONE -> "ONE(单曲循环)"
                    PlaybackStateCompat.REPEAT_MODE_ALL -> "ALL(列表循环)"
                    PlaybackStateCompat.REPEAT_MODE_GROUP -> "GROUP(群组循环)"
                    else -> "UNKNOWN($repeatMode)"
                }
                val msg = "[SOURCE_MODE_CALLBACK] 源应用抛出模式变化: ${controller.packageName}, repeatMode=$modeDesc"
                android.util.Log.i("Fahrmony_PLAYMODE", msg)
                android.util.Log.i("FAHRMONY_PROBE", msg)
                if (activeControllerCompat == controller) {
                    browserServiceRef?.get()?.syncRepeatMode(repeatMode)
                    pushMirrorState()
                }
            }

            override fun onShuffleModeChanged(shuffleMode: Int) {
                val modeDesc = when (shuffleMode) {
                    PlaybackStateCompat.SHUFFLE_MODE_NONE -> "NONE(顺序播放)"
                    PlaybackStateCompat.SHUFFLE_MODE_ALL -> "ALL(随机播放)"
                    PlaybackStateCompat.SHUFFLE_MODE_GROUP -> "GROUP(群组随机)"
                    else -> "UNKNOWN($shuffleMode)"
                }
                val msg = "[SOURCE_MODE_CALLBACK] 源应用抛出随机变化: ${controller.packageName}, shuffleMode=$modeDesc"
                android.util.Log.i("Fahrmony_PLAYMODE", msg)
                android.util.Log.i("FAHRMONY_PROBE", msg)
                if (activeControllerCompat == controller) {
                    browserServiceRef?.get()?.syncShuffleMode(shuffleMode)
                    pushMirrorState()
                }
            }

            override fun onExtrasChanged(extras: Bundle?) {
                if (extras == null) return
                try {
                    // 汽水音乐 (Luna Music) Vivo 车载通道私有 Extras 歌词实时监听 (Tier-1)
                    if (LunaLyricsProvider.isSupported(controller.packageName)) {
                        val lyric = extras.getString(LunaLyricsProvider.EXTRA_KEY_LYRIC)
                            ?: extras.getCharSequence(LunaLyricsProvider.EXTRA_KEY_LYRIC)?.toString()
                        val mediaId = extras.getString(LunaLyricsProvider.EXTRA_KEY_MEDIA_ID)
                            ?: extras.getCharSequence(LunaLyricsProvider.EXTRA_KEY_MEDIA_ID)?.toString()

                        if (!lyric.isNullOrBlank()) {
                            android.util.Log.d("FAHRMONY_PROBE", "[LUNA_EXTRAS] 捕获 Vivo 车载歌词广播: mediaId=$mediaId, length=${lyric.length}")
                            LunaLyricsProvider.onVivoExtrasReceived(mediaId, lyric)
                            if (activeControllerCompat == controller) {
                                val meta = controller.metadata
                                val rawTitle = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                                val rawArtist = meta?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                                val sanitized = LunaMetadataSanitizer.resolve(controller.packageName, meta, rawTitle, rawArtist, null)
                                val trackKey = sanitized.trackKey
                                appContext?.let { ctx ->
                                    val durationMs = meta?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                                    val cacheKey = LyricCache.buildKey(controller.packageName, rawTitle, rawArtist, durationMs)
                                    val parseResult = LrcParser.parse(lyric, convertToSimplified = false)
                                    if (parseResult is LyricResult.Success) {
                                        LyricCache.put(ctx, cacheKey, parseResult)
                                        lyricSyncEngine.updateLyrics(trackKey, parseResult)
                                        lyricSyncEngine.updatePlaybackState(controller.playbackState)
                                    }
                                }
                            }
                        }
                    }

                    // 波点音乐 (Bodian Music) Vivo 车载通道私有 Extras 歌词实时监听 (Tier-1)
                    if (BodianLyricsProvider.isSupported(controller.packageName)) {
                        val lyric = extras.getString(BodianLyricsProvider.EXTRA_KEY_LYRIC)
                            ?: extras.getCharSequence(BodianLyricsProvider.EXTRA_KEY_LYRIC)?.toString()
                        val mediaId = extras.getString(BodianLyricsProvider.EXTRA_KEY_MEDIA_ID)
                            ?: extras.getCharSequence(BodianLyricsProvider.EXTRA_KEY_MEDIA_ID)?.toString()

                        if (!lyric.isNullOrBlank()) {
                            android.util.Log.d("FAHRMONY_PROBE", "[BODIAN_EXTRAS] 捕获 Vivo 车载歌词广播: mediaId=$mediaId, length=${lyric.length}")
                            BodianLyricsProvider.onVivoExtrasReceived(mediaId, lyric)
                            if (activeControllerCompat == controller) {
                                val meta = controller.metadata
                                val rawTitle = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                                val rawArtist = meta?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                                val trackKey = "${controller.packageName}|$rawTitle|$rawArtist"
                                appContext?.let { ctx ->
                                    val durationMs = meta?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
                                    val cacheKey = LyricCache.buildKey(controller.packageName, rawTitle, rawArtist, durationMs)
                                    val parseResult = LrcParser.parse(lyric, convertToSimplified = false)
                                    if (parseResult is LyricResult.Success) {
                                        LyricCache.put(ctx, cacheKey, parseResult)
                                        lyricSyncEngine.updateLyrics(trackKey, parseResult)
                                        lyricSyncEngine.updatePlaybackState(controller.playbackState)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.w("FAHRMONY_MEDIA", "onExtrasChanged handling failed", e)
                }
            }
        }
        controller.registerCallback(callback, mainHandler)
        callbackMap[controller] = callback
        // 初始注册首帧触发一次探针 Dump，避免必须等待切歌
        controller.metadata?.let { logLyricsProbe(controller.packageName, it) }
        try {
            logDeepPlaybackProbe(controller, controller.playbackState, "INITIAL_REGISTER")
        } catch (ignored: Throwable) {}
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
        val pendingPkg = pendingAutoPlayPackage
        if (!pendingPkg.isNullOrBlank()) {
            val pendingCtrl = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, pendingPkg) }
            if (pendingCtrl != null) {
                checkPendingAutoPlay(pendingCtrl)
            }
        }
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
            val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata?.description?.title?.toString()
                ?: ""
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
        val modeCap = probePlaybackModeCapability(controller)

        var actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        if (modeCap.hasStandardRepeat) {
            actions = actions or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        }
        if (modeCap.hasStandardShuffle) {
            actions = actions or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
        }

        val stateBuilder = PlaybackStateCompat.Builder()
            .setState(targetState, position, if (isPlaying) 1.0f else 0.0f)
            .setActions(actions)
            .setActiveQueueItemId(0L)

        if (!modeCap.hasStandardRepeat && modeCap.customRepeatAction != null) {
            val underlyingName = modeCap.customRepeatAction.name?.toString()
            val resolvedState = resolveCustomRepeatState(underlyingName)
            if (resolvedState != null) {
                customRepeatState = resolvedState
            }
            val customAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_CAR_REPEAT,
                customRepeatState.title,
                customRepeatState.iconRes
            ).build()
            stateBuilder.addCustomAction(customAction)
        }

        if (!modeCap.hasStandardShuffle && modeCap.customShuffleAction != null) {
            val shuffleTitle = modeCap.customShuffleAction.name?.toString()?.ifBlank { "随机播放" } ?: "随机播放"
            val customShuffle = PlaybackStateCompat.CustomAction.Builder(
                ACTION_CAR_SHUFFLE,
                shuffleTitle,
                com.fahrmony.app.R.drawable.ic_car_shuffle
            ).build()
            stateBuilder.addCustomAction(customShuffle)
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

        val isRawArtwork = FahrmonyConfig.isRawPlayerCardEnabled(optCtx)
        val artworkActionTitle = FahrmonyCarI18n.getArtworkModeActionTitle(optCtx, isRawArtwork)
        val artworkActionIcon = if (isRawArtwork) {
            com.fahrmony.app.R.drawable.ic_car_artwork_image
        } else {
            com.fahrmony.app.R.drawable.ic_car_artwork_gradient
        }
        val artworkCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_ARTWORK_MODE,
            artworkActionTitle,
            artworkActionIcon
        ).build()
        stateBuilder.addCustomAction(artworkCustomAction)

        service.syncPlaybackState(stateBuilder.build())
        if (modeCap.hasStandardRepeat && controller != null) {
            service.syncRepeatMode(controller.repeatMode)
        } else {
            service.syncRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE)
        }
        if (modeCap.hasStandardShuffle && controller != null) {
            service.syncShuffleMode(controller.shuffleMode)
        } else {
            service.syncShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
        }
    }

    private fun pushMirrorState() {
        val service = browserServiceRef?.get() ?: return
        val context = appContext ?: service.applicationContext

        // Fahrmony 车载播控常驻：确保自身 MediaSession 处于活跃状态，杜绝焦点让渡导致服务中断
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
        val rawTitle = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata?.description?.title?.toString()
            ?: ""
        val rawArtist = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata?.description?.subtitle?.toString()
            ?: ""
        val rawAlbum = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)
            ?: metadata?.description?.description?.toString()
            ?: ""

        if (rawTitle.isNotBlank()) {
            lastValidTrackMap[controller.packageName] = CachedValidTrack(rawTitle, rawArtist, rawAlbum, metadata)
        } else if (lastValidTrackMap[controller.packageName] == null && !fallback?.title.isNullOrBlank()) {
            lastValidTrackMap[controller.packageName] = CachedValidTrack(fallback!!.title, fallback.artist, fallback.album, metadata)
        }
        val cached = lastValidTrackMap[controller.packageName]

        val defaultNowPlaying = appContext?.let { FahrmonyCarI18n.getNowPlayingDefault(it) } ?: "正在播放"
        val hasRealTrack = rawTitle.isNotBlank() || (!fallback?.title.isNullOrBlank()) || (cached != null)
        // 关键防护：优先原生标题 -> 粘性曲目锁存 -> 实时通知栏兜底，防止滚动歌词污染曲目标识与频繁切色
        val titleCandidate = if (rawTitle.isNotBlank()) {
            rawTitle
        } else if (cached != null && cached.title.isNotBlank()) {
            cached.title
        } else {
            fallback?.title ?: defaultNowPlaying
        }
        val rawOrCachedArtist = if (rawArtist.isNotBlank()) {
            rawArtist
        } else if (cached != null && cached.artist.isNotBlank()) {
            cached.artist
        } else {
            fallback?.artist ?: getAppNameForPackage(appContext, controller.packageName)
        }
        val sanitized = LunaMetadataSanitizer.resolve(
            controller.packageName,
            metadata,
            titleCandidate,
            rawOrCachedArtist,
            null
        )
        val title = sanitized.title
        val cleanArtist = sanitized.artist
        val artist = if (!invalidTrackNotice.isNullOrBlank()) invalidTrackNotice!! else cleanArtist
        val album = rawAlbum.ifBlank { cached?.album ?: (fallback?.album ?: "") }
        val trackKey = sanitized.trackKey

        val isRawArtwork = FahrmonyConfig.isRawPlayerCardEnabled(context)
        val rawArtworkBmp = if (hasRealTrack && isRawArtwork) extractRawArtworkBitmap(controller) else null
        val currentArtId = rawArtworkBmp?.generationId ?: (if (isRawArtwork) 0 else currentPaletteIndex)
        val isArtworkArrivedOrChanged = isRawArtwork && (rawArtworkBmp != null) && (currentArtId != lastSyncedArtworkId)
        val artworkModeChanged = (lastSyncedArtworkMode != isRawArtwork)
        val trackKeyChanged = (trackKey != lastSyncedTrackKey)

        if (trackKeyChanged || isArtworkArrivedOrChanged || artworkModeChanged) {
            lastSyncedTrackKey = trackKey
            lastSyncedArtworkId = currentArtId
            lastSyncedArtworkMode = isRawArtwork

            val trackBmp = if (hasRealTrack) {
                if (isRawArtwork) {
                    rawArtworkBmp ?: getIdleArtwork()
                } else {
                    appContext?.let { getArtworkForTrack(it, trackKey, isRealTrack = true) } ?: getIdleArtwork()
                }
            } else {
                getIdleArtwork()
            }
            currentTrackArtworkBitmap = trackBmp

            FahrmonyLogBuffer.addLog(
                "PROBE_MIRROR",
                "推流元数据变更",
                "trackKey: $trackKey, isRealTrack: $hasRealTrack, isRawArtwork: $isRawArtwork, artChanged: $isArtworkArrivedOrChanged, modeChanged: $artworkModeChanged, title: $title, artist: $artist",
                controller.packageName
            )

            FahrmonyLogBuffer.addLog(
                "ARTWORK_OUT",
                "车机端封面推流",
                "车机模式: ${if (isRawArtwork) "原始封面" else "5色流光"}, 尺寸: ${trackBmp.width}x${trackBmp.height}, genId: ${trackBmp.generationId}, trackKey: $trackKey",
                controller.packageName
            )
            val effectiveMeta = if (rawTitle.isNotBlank()) metadata else (cached?.metadata ?: metadata)
            val isLyricsActive = lyricSyncEngine.hasValidLyrics() && (FahrmonyConfig.getLyricsMode(context) > 0)

            if (isLyricsActive && !trackKeyChanged && (isArtworkArrivedOrChanged || artworkModeChanged)) {
                // 仅封面变更或延迟到货，且当前歌词正在平稳运行中：无缝重发当前歌词行与新封面，严禁打断歌词闪现歌曲名！
                lyricSyncEngine.retriggerCurrentLine()
            } else {
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
            }

            if (trackKeyChanged) {
                // 仅在真实切歌时触发歌词查询与同步引擎更新
                checkAndUpdateLyrics(context, controller, trackKey, effectiveMeta ?: metadata)
            }

            if (isArtworkArrivedOrChanged || artworkModeChanged) {
                // 封面延迟送达或模式切换，热同步广播手机端，手机端恒定显示原始封面
                val updatedInfo = extractSessionInfo(controller)
                onSessionUpdated?.invoke(updatedInfo)
                onActiveSessionChangedListener?.invoke(updatedInfo)
                FahrmonyLogBuffer.addLog(
                    "ARTWORK_OUT",
                    "手机端封面热同步广播",
                    "手机端恒定原始封面, hasArtwork: ${updatedInfo.artworkData != null}, trackKey: $trackKey",
                    controller.packageName
                )
            }
        }

        // 歌词自愈补救机制：若开启歌词但因切歌时隙导致歌词未到位，且当前为真实曲目，执行带防抖的轻量自愈探测
        if (hasRealTrack && FahrmonyConfig.getLyricsMode(context) > 0 && !lyricSyncEngine.hasValidLyrics()) {
            val now = System.currentTimeMillis()
            if (now - lastLyricsSelfHealingTimestamp > 1200L) {
                lastLyricsSelfHealingTimestamp = now
                val effectiveMeta = if (rawTitle.isNotBlank()) metadata else (cached?.metadata ?: metadata)
                checkAndUpdateLyrics(context, controller, trackKey, effectiveMeta ?: metadata)
            }
        }

        // 执行探针观测 (受保护沙箱，杜绝反序列化中断推流主链路)
        try {
            logDeepPlaybackProbe(controller, state, "pushMirrorState")
        } catch (ignored: Throwable) {}

        // 2. 同步播放状态 (确保包含标准双向操控 actions 与暂停防回弹锁)
        val modeCap = probePlaybackModeCapability(controller)
        val hasStandardRepeat = modeCap.hasStandardRepeat
        val hasStandardShuffle = modeCap.hasStandardShuffle

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
        if (hasStandardShuffle) {
            actions = actions or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
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

        if (!hasStandardRepeat && modeCap.customRepeatAction != null) {
            val underlyingName = modeCap.customRepeatAction.name?.toString()
            val resolvedState = resolveCustomRepeatState(underlyingName)
            if (resolvedState != null) {
                customRepeatState = resolvedState
            } else if (QQMusicPlayModeAdapter.isSupported(controller.packageName)) {
                QQMusicPlayModeAdapter.resolvePlayMode(metadata)?.let {
                    customRepeatState = it
                }
            } else if (LunaMusicPlayModeAdapter.isSupported(controller.packageName)) {
                LunaMusicPlayModeAdapter.resolvePlayMode(metadata)?.let {
                    customRepeatState = it
                }
            } else if (BodianMusicPlayModeAdapter.isSupported(controller.packageName)) {
                BodianMusicPlayModeAdapter.resolvePlayMode(metadata)?.let {
                    customRepeatState = it
                }
            } else if (KuwoMusicPlayModeAdapter.isSupported(controller.packageName)) {
                KuwoMusicPlayModeAdapter.resolvePlayMode(metadata)?.let {
                    customRepeatState = it
                }
            } else if (KugouMusicPlayModeAdapter.isSupported(controller.packageName)) {
                KugouMusicPlayModeAdapter.resolvePlayMode(controller.packageName, metadata)?.let {
                    customRepeatState = it
                }
            }
            val customAction = PlaybackStateCompat.CustomAction.Builder(
                ACTION_CAR_REPEAT,
                customRepeatState.title,
                customRepeatState.iconRes
            ).build()
            stateBuilder.addCustomAction(customAction)
            android.util.Log.i("Fahrmony_PLAYMODE", "[CAR_MIRROR_STATE] 同步车机显示 -> 模式: ${customRepeatState.title}, 图标资源: ${customRepeatState.iconRes}, 目标应用: ${controller.packageName}")
        } else if (hasStandardRepeat) {
            val rDesc = when (controller.repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> "NONE(关闭/不循环)"
                PlaybackStateCompat.REPEAT_MODE_ONE -> "ONE(单曲循环)"
                PlaybackStateCompat.REPEAT_MODE_ALL -> "ALL(列表循环)"
                else -> "${controller.repeatMode}"
            }
            android.util.Log.i("Fahrmony_PLAYMODE", "[CAR_MIRROR_STATE] 同步车机标准循环显示 -> repeatMode=$rDesc, 目标应用: ${controller.packageName}")
        }

        if (!hasStandardShuffle && modeCap.customShuffleAction != null) {
            val shuffleTitle = modeCap.customShuffleAction.name?.toString()?.ifBlank { "随机播放" } ?: "随机播放"
            val customShuffle = PlaybackStateCompat.CustomAction.Builder(
                ACTION_CAR_SHUFFLE,
                shuffleTitle,
                com.fahrmony.app.R.drawable.ic_car_shuffle
            ).build()
            stateBuilder.addCustomAction(customShuffle)
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
        val artworkActionTitle = FahrmonyCarI18n.getArtworkModeActionTitle(context, isRawArtwork)
        val artworkActionIcon = if (isRawArtwork) {
            com.fahrmony.app.R.drawable.ic_car_artwork_image
        } else {
            com.fahrmony.app.R.drawable.ic_car_artwork_gradient
        }
        val artworkCustomAction = PlaybackStateCompat.CustomAction.Builder(
            ACTION_TOGGLE_ARTWORK_MODE,
            artworkActionTitle,
            artworkActionIcon
        ).build()
        stateBuilder.addCustomAction(artworkCustomAction)

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
        if (hasStandardShuffle) {
            service.syncShuffleMode(controller.shuffleMode)
        } else {
            service.syncShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE)
        }

        lyricSyncEngine.updatePlaybackState(controller.playbackState)
    }

    // 手机端专用：恒定提取原始专辑封面 Base64，基于 generationId 单对象瞬时缓存，彻底消除跨歌曲串图死锁 (Phase 8.0.3)
    private var lastEncodedArtworkGenId: Int = -1
    private var cachedArtworkBase64: String? = null

    fun getRawArtworkBase64(controller: MediaControllerCompat): String? {
        val rawBmp = extractRawArtworkBitmap(controller) ?: return null
        if (rawBmp.isRecycled) return null
        val genId = rawBmp.generationId
        if (cachedArtworkBase64 != null && lastEncodedArtworkGenId == genId) {
            return cachedArtworkBase64
        }
        val base64 = encodeBitmapToBase64(rawBmp)
        if (base64 != null) {
            lastEncodedArtworkGenId = genId
            cachedArtworkBase64 = base64
        }
        return base64
    }

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

    /**
     * 等比安全缩放位图至指定最大尺寸（默认 240px），杜绝 Binder 1MB 缓冲区溢出与 IPC 掉帧
     */
    private fun scaleBitmapSafely(source: Bitmap, maxDim: Int = 240): Bitmap {
        if (source.isRecycled) return source
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) {
            return source
        }
        val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetWidth = Math.max(1, (width * ratio).toInt())
        val targetHeight = Math.max(1, (height * ratio).toInt())
        return try {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        } catch (e: Throwable) {
            source
        }
    }

    /**
     * 提取音源 App 原始封面并做 <=240px 尺寸约束 (Phase 8.0)
     */
    fun extractRawArtworkBitmap(controller: MediaControllerCompat): Bitmap? {
        val metadata = controller.metadata
        val bmp = metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON)
            ?: metadata?.description?.iconBitmap
            ?: fallbackMetaMap[controller.packageName]?.artworkBmp

        if (bmp != null && !bmp.isRecycled) {
            val srcGenId = bmp.generationId
            if (cachedRawArtworkBitmap != null && !cachedRawArtworkBitmap!!.isRecycled) {
                if (lastRawSourceGenId == srcGenId || (bmp.width == cachedRawArtworkBitmap!!.width && bmp.height == cachedRawArtworkBitmap!!.height && bmp.sameAs(cachedRawArtworkBitmap))) {
                    return cachedRawArtworkBitmap
                }
            }
            val scaled = scaleBitmapSafely(bmp, 240)
            lastRawSourceGenId = srcGenId
            lastRawSourceUri = null
            cachedRawArtworkBitmap = scaled
            FahrmonyLogBuffer.addLog(
                "ARTWORK_IN",
                "MediaSession封面提取",
                "来源: MetadataBitmap, 尺寸: ${scaled.width}x${scaled.height}, genId: ${scaled.generationId}",
                controller.packageName
            )
            return scaled
        }

        val uriStr = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_ART_URI)
            ?: metadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
            ?: metadata?.description?.iconUri?.toString()

        if (!uriStr.isNullOrBlank()) {
            if (cachedRawArtworkBitmap != null && !cachedRawArtworkBitmap!!.isRecycled && lastRawSourceUri == uriStr) {
                return cachedRawArtworkBitmap
            }
            if (uriStr.startsWith("content://") || uriStr.startsWith("file://")) {
                try {
                    val uri = android.net.Uri.parse(uriStr)
                    val stream = appContext?.contentResolver?.openInputStream(uri)
                    if (stream != null) {
                        val decoded = android.graphics.BitmapFactory.decodeStream(stream)
                        stream.close()
                        if (decoded != null && !decoded.isRecycled) {
                            val scaled = scaleBitmapSafely(decoded, 240)
                            if (scaled != decoded && !decoded.isRecycled) {
                                decoded.recycle()
                            }
                            lastRawSourceGenId = -1
                            lastRawSourceUri = uriStr
                            cachedRawArtworkBitmap = scaled
                            FahrmonyLogBuffer.addLog(
                                "ARTWORK_IN",
                                "MediaSession封面提取",
                                "来源: UriStream, 尺寸: ${scaled.width}x${scaled.height}, genId: ${scaled.generationId}",
                                controller.packageName
                            )
                            return scaled
                        }
                    }
                } catch (ignored: Exception) {}
            }
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
            val artworkData = getRawArtworkBase64(controller)

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
            val artworkData = getRawArtworkBase64(controller)

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
            val context = appContext
            val isRawArtwork = if (context != null) FahrmonyConfig.isRawPlayerCardEnabled(context) else false
            if (isRawArtwork) {
                activeControllerCompat?.let { extractRawArtworkBitmap(it) } ?: getIdleArtwork()
            } else {
                appContext?.let { getArtworkForTrack(it, trackKey, isRealTrack = true) } ?: getIdleArtwork()
            }
        } else {
            getIdleArtwork()
        }
        val builder = MediaMetadataCompat.Builder()

        // 1. 继承所有外部有效文本元数据 (歌名、歌手、专辑、时长等)
        val rawTitle = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
        val rawArtist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
        val pkg = activeControllerCompat?.packageName ?: ""
        val sanitized = LunaMetadataSanitizer.resolve(pkg, metadata, rawTitle, rawArtist, null)
        val title = sanitized.title
        if (title.isNotBlank()) builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)

        val normalArtist = sanitized.artist
        val artist = if (!invalidTrackNotice.isNullOrBlank()) invalidTrackNotice else normalArtist
        if (!artist.isNullOrBlank()) builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)

        val album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM)
        if (!album.isNullOrBlank()) builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)

        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        if (duration > 0L) builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        // 2. 根据用户配置分发封面：原始专辑封面直出 (<=240px) 或 5色流光渐变卡片
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

    fun sanitizeRepeatMode(mode: Int): Int {
        return when (mode) {
            PlaybackStateCompat.REPEAT_MODE_NONE,
            PlaybackStateCompat.REPEAT_MODE_ONE,
            PlaybackStateCompat.REPEAT_MODE_ALL,
            PlaybackStateCompat.REPEAT_MODE_GROUP -> mode
            else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
    }

    fun sanitizeShuffleMode(mode: Int): Int {
        return when (mode) {
            PlaybackStateCompat.SHUFFLE_MODE_NONE,
            PlaybackStateCompat.SHUFFLE_MODE_ALL,
            PlaybackStateCompat.SHUFFLE_MODE_GROUP -> mode
            else -> PlaybackStateCompat.SHUFFLE_MODE_NONE
        }
    }

    fun getActiveRepeatMode(): Int {
        val raw = activeControllerCompat?.repeatMode ?: PlaybackStateCompat.REPEAT_MODE_NONE
        return sanitizeRepeatMode(raw)
    }

    fun setRepeatMode(repeatMode: Int): Boolean {
        val controller = activeControllerCompat ?: return false
        val safeMode = sanitizeRepeatMode(repeatMode)
        try {
            val modeDesc = when (safeMode) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> "NONE(关闭/不循环)"
                PlaybackStateCompat.REPEAT_MODE_ONE -> "ONE(单曲循环)"
                PlaybackStateCompat.REPEAT_MODE_ALL -> "ALL(列表循环)"
                PlaybackStateCompat.REPEAT_MODE_GROUP -> "GROUP(群组循环)"
                else -> "UNKNOWN($safeMode)"
            }
            val logMsg = "[CAR_MODE_DISPATCH] 触发标准切换循环模式 -> 目标: $modeDesc, 目标应用: ${controller.packageName}"
            android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
            android.util.Log.i("FAHRMONY_PROBE", logMsg)
            controller.transportControls?.setRepeatMode(safeMode)
            browserServiceRef?.get()?.syncRepeatMode(safeMode)
            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "切换循环模式", "目标模式: $modeDesc", controller.packageName)
            return true
        } catch (e: Exception) {
            val errLog = "[CAR_MODE_DISPATCH] 切换循环模式异常: ${e.message}, 目标应用: ${controller.packageName}"
            android.util.Log.e("Fahrmony_PLAYMODE", errLog)
            FahrmonyLogBuffer.addLog("MEDIA_IPC", "切换循环模式异常", "${e.message}", controller.packageName)
            return false
        }
    }

    fun toggleRepeatMode(): Boolean {
        val controller = activeControllerCompat ?: return false
        val probe = probePlaybackModeCapability(controller)
        return if (probe.hasStandardRepeat) {
            val current = sanitizeRepeatMode(controller.repeatMode)
            val next = when (current) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
            val logMsg = "[CAR_MODE_EVENT] 车机触发标准循环切换: 当前=$current -> 目标=$next, 目标应用=${controller.packageName}"
            android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
            android.util.Log.i("FAHRMONY_PROBE", logMsg)
            setRepeatMode(next)
        } else if (probe.customRepeatAction != null) {
            val act = probe.customRepeatAction.action ?: ""
            val isNetEase = controller.packageName == "com.netease.cloudmusic"
            try {
                val previousState = customRepeatState
                lastCarModeToggleTimestamp = System.currentTimeMillis()
                customRepeatState = if (QQMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    QQMusicPlayModeAdapter.nextMode(customRepeatState)
                } else if (LunaMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    LunaMusicPlayModeAdapter.nextMode(customRepeatState)
                } else if (BodianMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    BodianMusicPlayModeAdapter.nextMode(customRepeatState).first
                } else if (KuwoMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    KuwoMusicPlayModeAdapter.nextMode(customRepeatState).first
                } else if (KugouMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    KugouMusicPlayModeAdapter.nextMode(customRepeatState).first
                } else {
                    customRepeatState.next(isNetEase)
                }
                val savePkg = if (KugouMusicPlayModeAdapter.isMain(controller.packageName)) "com.kugou.android" else controller.packageName
                appContext?.let { getOrCreatePersister(it).saveMode(savePkg, customRepeatState) }
                if (isNetEase) {
                    FahrmonyIpcBridge.notifyPlayModeChanged("com.netease.cloudmusic", customRepeatState)
                } else if (KugouMusicPlayModeAdapter.isMain(controller.packageName)) {
                    FahrmonyIpcBridge.notifyPlayModeChanged("com.kugou.android", customRepeatState)
                }
                val logMsg = "[CAR_MODE_EVENT] 车机触发CustomAction模式流转: [${previousState.title}] -> [${customRepeatState.title}] (isNetEase=$isNetEase), 透传Action: $act, 目标应用: ${controller.packageName}"
                android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
                android.util.Log.i("FAHRMONY_PROBE", logMsg)
                if (LunaMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    LunaMusicPlayModeAdapter.dispatchToggle(controller, previousState)
                } else if (BodianMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    BodianMusicPlayModeAdapter.dispatchToggle(controller, previousState)
                } else if (KuwoMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    KuwoMusicPlayModeAdapter.dispatchToggle(controller, previousState)
                } else if (KugouMusicPlayModeAdapter.isSupported(controller.packageName)) {
                    KugouMusicPlayModeAdapter.dispatchToggle(controller, previousState)
                } else {
                    controller.transportControls?.sendCustomAction(act, probe.customRepeatAction.extras)
                }
                FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "透传CustomAction", "$act -> ${customRepeatState.title}", controller.packageName)
                pushMirrorState()
                true
            } catch (e: Exception) {
                val errLog = "[CAR_MODE_EVENT] 透传CustomAction异常: ${e.message}, 目标应用: ${controller.packageName}"
                android.util.Log.e("Fahrmony_PLAYMODE", errLog)
                FahrmonyLogBuffer.addLog("MEDIA_IPC", "透传CustomAction异常", "${e.message}", controller.packageName)
                false
            }
        } else {
            val logMsg = "[CAR_MODE_EVENT] 应用 ${controller.packageName} 不支持循环模式切换"
            android.util.Log.w("Fahrmony_PLAYMODE", logMsg)
            false
        }
    }

    fun getActiveShuffleMode(): Int {
        val raw = activeControllerCompat?.shuffleMode ?: PlaybackStateCompat.SHUFFLE_MODE_NONE
        return sanitizeShuffleMode(raw)
    }

    fun setShuffleMode(shuffleMode: Int): Boolean {
        val controller = activeControllerCompat ?: return false
        val safeMode = sanitizeShuffleMode(shuffleMode)
        try {
            val modeDesc = when (safeMode) {
                PlaybackStateCompat.SHUFFLE_MODE_NONE -> "NONE(顺序播放)"
                PlaybackStateCompat.SHUFFLE_MODE_ALL -> "ALL(随机播放)"
                PlaybackStateCompat.SHUFFLE_MODE_GROUP -> "GROUP(群组随机)"
                else -> "UNKNOWN($safeMode)"
            }
            val logMsg = "[CAR_MODE_DISPATCH] 触发标准切换随机模式 -> 目标: $modeDesc, 目标应用: ${controller.packageName}"
            android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
            android.util.Log.i("FAHRMONY_PROBE", logMsg)
            controller.transportControls?.setShuffleMode(safeMode)
            browserServiceRef?.get()?.syncShuffleMode(safeMode)
            FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "切换随机模式", "目标模式: $modeDesc", controller.packageName)
            return true
        } catch (e: Exception) {
            val errLog = "[CAR_MODE_DISPATCH] 切换随机模式异常: ${e.message}, 目标应用: ${controller.packageName}"
            android.util.Log.e("Fahrmony_PLAYMODE", errLog)
            FahrmonyLogBuffer.addLog("MEDIA_IPC", "切换随机模式异常", "${e.message}", controller.packageName)
            return false
        }
    }

    fun toggleShuffleMode(): Boolean {
        val controller = activeControllerCompat ?: return false
        val probe = probePlaybackModeCapability(controller)
        return if (probe.hasStandardShuffle) {
            val current = controller.shuffleMode
            val next = if (current == PlaybackStateCompat.SHUFFLE_MODE_NONE) {
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            } else {
                PlaybackStateCompat.SHUFFLE_MODE_NONE
            }
            val logMsg = "[CAR_MODE_EVENT] 车机触发标准随机切换: 当前=$current -> 目标=$next, 目标应用=${controller.packageName}"
            android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
            android.util.Log.i("FAHRMONY_PROBE", logMsg)
            setShuffleMode(next)
        } else if (probe.customShuffleAction != null) {
            val act = probe.customShuffleAction.action ?: ""
            try {
                val logMsg = "[CAR_MODE_EVENT] 车机触发CustomAction随机切换: 透传Action: $act, 目标应用: ${controller.packageName}"
                android.util.Log.i("Fahrmony_PLAYMODE", logMsg)
                android.util.Log.i("FAHRMONY_PROBE", logMsg)
                controller.transportControls?.sendCustomAction(act, probe.customShuffleAction.extras)
                FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "透传随机CustomAction", act, controller.packageName)
                pushMirrorState()
                true
            } catch (e: Exception) {
                val errLog = "[CAR_MODE_EVENT] 透传随机CustomAction异常: ${e.message}, 目标应用: ${controller.packageName}"
                android.util.Log.e("Fahrmony_PLAYMODE", errLog)
                FahrmonyLogBuffer.addLog("MEDIA_IPC", "透传随机CustomAction异常", "${e.message}", controller.packageName)
                false
            }
        } else {
            val logMsg = "[CAR_MODE_EVENT] 应用 ${controller.packageName} 不支持随机模式切换"
            android.util.Log.w("Fahrmony_PLAYMODE", logMsg)
            false
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
        // 仅在真实切歌（曲目标识变化）时，才静默重置歌词同步引擎，清空上一首残留 entries，杜绝 60ms 极短时隙幽灵歌词闪现；
        // 同一曲目的换句元数据广播（如 QQ 音乐换句）严禁 reset，杜绝清空 entries 导致车机闪退歌名
        if (!lyricSyncEngine.isSameTrack(trackKey)) {
            lyricSyncEngine.reset(notifyListener = false)
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
        val sanitized = LunaMetadataSanitizer.resolve(controller.packageName, metadata, rawTitle, rawArtist, currentLine)
        val cleanTitle = sanitized.title
        val cleanArtist = sanitized.artist
        val trackKey = sanitized.trackKey

        val songInfo = if (cleanArtist.isNotBlank() && !cleanTitle.contains(cleanArtist) && !cleanArtist.contains(cleanTitle)) {
            "$cleanTitle · $cleanArtist"
        } else if (cleanTitle.isNotBlank()) {
            cleanTitle
        } else {
            "..."
        }
        val displayTitle = if (currentLine.isNotBlank()) currentLine else cleanTitle.ifBlank { "..." }
        val displayArtist = if (mode == 1) {
            // 单行模式：Line 2 恒定显示歌曲名 · 歌手名，永不丢失曲目信息
            songInfo
        } else {
            // 双行模式：Line 2 显示下一句歌词，无下句时回退为歌曲信息
            if (!nextLine.isNullOrBlank()) nextLine else songInfo
        }
        val displayAlbum = songInfo
        val isRawArtwork = FahrmonyConfig.isRawPlayerCardEnabled(context)
        val defaultBmp = currentTrackArtworkBitmap ?: if (isRawArtwork) {
            activeControllerCompat?.let { extractRawArtworkBitmap(it) } ?: getIdleArtwork()
        } else {
            getArtworkForTrack(context, trackKey, isRealTrack = true)
        }
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
                if (lyricSyncEngine.hasValidLyrics()) {
                    // 若当前会话已有有效歌词缓存，直接即时重推当前行，0ms 瞬间完成单双行切换，杜绝无意义的网络重查
                    lyricSyncEngine.retriggerCurrentLine()
                } else {
                    val meta = controller.metadata ?: lastValidTrackMap[controller.packageName]?.metadata
                    val rawTitle = meta?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: ""
                    val rawArtist = meta?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: ""
                    val sanitized = LunaMetadataSanitizer.resolve(controller.packageName, meta, rawTitle, rawArtist, null)
                    val trackKey = sanitized.trackKey
                    checkAndUpdateLyrics(context, controller, trackKey, meta)
                }
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

    /**
     * 车载全屏卡片封面模式即时切换 (Phase 8.0.1 CustomAction)
     */
    fun toggleArtworkMode(context: Context): Boolean {
        val current = FahrmonyConfig.isRawPlayerCardEnabled(context)
        val next = !current
        val json = org.json.JSONObject().apply {
            put("rawPlayerCard", next)
        }
        FahrmonyConfig.updateConfig(context, json)
        pushMirrorState()
        val fullConfig = FahrmonyConfig.getConfig(context)
        FahrmonyIpcBridge.notifyClientConfigChanged(fullConfig.toString())
        val modeText = if (next) "原始专辑封面" else "5色动态流光"
        FahrmonyLogBuffer.addLog("MEDIA_CONTROL", "车机封面模式切换", modeText, "车机CustomAction切换")
        return next
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
                    lastValidTrackMap.remove(controller.packageName)
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
                    // 快速连击防抖：取消上一次挂起的延迟任务，防止交替重叠
                    pendingSkipRefreshRunnable1?.let { mainHandler.removeCallbacks(it) }
                    pendingSkipRefreshRunnable2?.let { mainHandler.removeCallbacks(it) }
                    val r1 = Runnable { pushMirrorState() }
                    val r2 = Runnable { pushMirrorState() }
                    pendingSkipRefreshRunnable1 = r1
                    pendingSkipRefreshRunnable2 = r2
                    mainHandler.postDelayed(r1, 150L)
                    mainHandler.postDelayed(r2, 450L)
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
                val modeCap = probePlaybackModeCapability(controller)

                var actions = PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP

                if (modeCap.hasStandardRepeat) {
                    actions = actions or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                }
                if (modeCap.hasStandardShuffle) {
                    actions = actions or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                }

                val state = PlaybackStateCompat.Builder()
                    .setState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED, positionMs, if (isPlaying) 1.0f else 0.0f)
                    .setActions(actions)
                    .setActiveQueueItemId(0L)

                if (!modeCap.hasStandardRepeat && modeCap.customRepeatAction != null) {
                    val underlyingName = modeCap.customRepeatAction.name?.toString()
                    val resolvedState = resolveCustomRepeatState(underlyingName)
                    if (resolvedState != null) {
                        customRepeatState = resolvedState
                    }
                    val customAction = PlaybackStateCompat.CustomAction.Builder(
                        ACTION_CAR_REPEAT,
                        customRepeatState.title,
                        customRepeatState.iconRes
                    ).build()
                    state.addCustomAction(customAction)
                }

                if (!modeCap.hasStandardShuffle && modeCap.customShuffleAction != null) {
                    val shuffleTitle = modeCap.customShuffleAction.name?.toString()?.ifBlank { "随机播放" } ?: "随机播放"
                    val customShuffle = PlaybackStateCompat.CustomAction.Builder(
                        ACTION_CAR_SHUFFLE,
                        shuffleTitle,
                        com.fahrmony.app.R.drawable.ic_car_shuffle
                    ).build()
                    state.addCustomAction(customShuffle)
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
        val resolvedTarget = resolveRealPackageName(context, finalTarget)

        val targetCtrl = if (resolvedTarget.isNotBlank()) {
            allControllersCompat.firstOrNull { isPackageMatch(it.packageName, resolvedTarget) }
        } else null

        // 1. 若目标播放器已有控制器就绪，无条件以目标播放器为准起播
        if (targetCtrl != null) {
            pendingAutoPlayPackage = null
            pendingAutoPlayDeadline = 0L
            stickyActivePackage = targetCtrl.packageName
            activeControllerCompat = targetCtrl
            notifyInfo(immediate = true)
            try {
                targetCtrl.transportControls?.play()
            } catch (ignored: Exception) {}
            return true
        }

        // 2. 若指定了目标包名但该目标未启动，只唤醒目标播放器，绝不串流起播其他应用
        if (resolvedTarget.isNotBlank()) {
            pendingAutoPlayPackage = resolvedTarget
            pendingAutoPlayDeadline = System.currentTimeMillis() + 10000L
            stickyActivePackage = resolvedTarget
            activeControllerCompat = null
            notifyInfo(immediate = true)
            wakeUpPlayer(context, resolvedTarget)
            return true
        }

        // 3. 兜底回退：无目标包名且存在活跃控制器时
        val controller = activeControllerCompat
        if (controller != null) {
            pendingAutoPlayPackage = null
            pendingAutoPlayDeadline = 0L
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

    fun resolveRealPackageName(context: Context, packageName: String): String {
        if (packageName.isBlank()) return packageName
        if (packageName.equals("kugou.service", ignoreCase = true)) {
            val pm = context.packageManager
            return when {
                pm.getLaunchIntentForPackage("com.kugou.android") != null -> "com.kugou.android"
                pm.getLaunchIntentForPackage("com.kugou.android.lite") != null -> "com.kugou.android.lite"
                else -> "com.kugou.android"
            }
        }
        return packageName
    }

    fun wakeUpPlayer(context: Context, rawPackageName: String) {
        if (rawPackageName.isBlank()) return
        val packageName = resolveRealPackageName(context, rawPackageName)

        val now = System.currentTimeMillis()
        val lastAttempt = lastWakeUpAttemptMap[packageName] ?: 0L
        if (now - lastAttempt < 3000L) {
            FahrmonyLogBuffer.addLog("WAKE", "防抖抑制", "距上次唤醒仅 ${now - lastAttempt}ms，抑制重复拉起以保护启动窗口期", packageName)
            return
        }
        lastWakeUpAttemptMap[packageName] = now

        stickyActivePackage = packageName
        lastWakeUpTimestamp = now
        lastWakeUpPackage = packageName

        val inForeground = isFahrmonyInForeground(context)
        FahrmonyLogBuffer.addLog("PROBE_WAKE", "发起应用唤醒", "目标: $packageName, 前台状态: $inForeground, 启动基准时间: ${lastWakeUpTimestamp}ms", packageName)

        if (inForeground) {
            // 逻辑 A: Fahrmony 已在前台，直接通过 startActivity 唤起目标播放器并播放
            FahrmonyLogBuffer.addLog("WAKE", "前台直拉播放器", "Fahrmony处于前台，直接拉起目标应用", packageName)
            try {
                var launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent == null && packageName.equals("kugou.service", ignoreCase = true)) {
                    launchIntent = context.packageManager.getLaunchIntentForPackage("com.kugou.android")
                        ?: context.packageManager.getLaunchIntentForPackage("com.kugou.android.lite")
                }
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launchIntent)
                }
            } catch (e: Exception) {
                FahrmonyLogBuffer.addLog("WAKE", "拉起播放器异常", "${e.message}", packageName)
            }
        } else {
            // 逻辑 B: Fahrmony 不在前台，先打开 Fahrmony (携带 BAL 豁免与锁屏穿透)，由 MainActivity 链式拉起目标播放器
            FahrmonyLogBuffer.addLog("WAKE", "先打开Fahrmony", "Fahrmony不在前台，拉起主应用建立前台控制权", packageName)
            try {
                val mainIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (mainIntent != null) {
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    mainIntent.putExtra("EXTRA_CHAIN_LAUNCH_PKG", packageName)
                    val mainPi = PendingIntent.getActivity(
                        context,
                        (context.packageName + "|" + packageName).hashCode(),
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        val options = ActivityOptions.makeBasic()
                        options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        mainPi.send(context, 0, null, null, null, null, options.toBundle())
                    } else {
                        mainPi.send()
                    }
                }
            } catch (e: Exception) {
                FahrmonyLogBuffer.addLog("WAKE", "拉起Fahrmony异常", "${e.message}", packageName)
                try {
                    val fallbackIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    fallbackIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    fallbackIntent?.putExtra("EXTRA_CHAIN_LAUNCH_PKG", packageName)
                    if (fallbackIntent != null) context.startActivity(fallbackIntent)
                } catch (ignored: Exception) {}
            }
        }

        // 显式定向广播穿透 (ACTION_MEDIA_BUTTON 附带 FLAG_RECEIVER_FOREGROUND 与显式 ComponentName)
        try {
            val pm = context.packageManager
            val mbIntent = Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(packageName)
            val receivers = pm.queryBroadcastReceivers(mbIntent, 0)
            val targetComponent = receivers.firstOrNull()?.activityInfo?.let {
                ComponentName(it.packageName, it.name)
            } ?: if (isPackageMatch(packageName, "com.luna.music")) {
                ComponentName("com.luna.music", "com.luna.biz.playing.player.BachMediaButtonReceiver")
            } else if (isPackageMatch(packageName, "com.kugou.android")) {
                ComponentName(resolveRealPackageName(context, packageName), "com.kugou.framework.service.MediaButtonIntentReceiver")
            } else null

            val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                if (targetComponent != null) component = targetComponent else setPackage(packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            context.sendBroadcast(downIntent)
            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                if (targetComponent != null) component = targetComponent else setPackage(packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            context.sendBroadcast(upIntent)
            val compDesc = targetComponent?.flattenToShortString() ?: packageName
            FahrmonyLogBuffer.addLog("WAKE", "定向广播穿透", "已向目标派发显式前台按键广播: $compDesc", packageName)
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("WAKE", "定向广播异常", "${e.message}", packageName)
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

    fun switchToPackage(context: Context, rawPackageName: String) {
        if (rawPackageName.isBlank()) return
        val packageName = resolveRealPackageName(context, rawPackageName)
        stickyActivePackage = packageName
        optimisticSyncState(PlaybackStateCompat.STATE_PLAYING)
        val target = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, packageName) }
        if (target != null) {
            pendingAutoPlayPackage = null
            pendingAutoPlayDeadline = 0L
            activeControllerCompat = target
            notifyInfo(immediate = true)
            try {
                target.transportControls?.play()
            } catch (ignored: Exception) {}
        } else {
            pendingAutoPlayPackage = packageName
            pendingAutoPlayDeadline = System.currentTimeMillis() + 10000L
        }
        wakeUpPlayer(context, packageName)

        // 阶梯式探测补偿：在 800ms、1600ms、2600ms、4000ms、5800ms 依次执行 refresh 与接管播放
        val probeDelays = listOf(800L, 1600L, 2600L, 4000L, 5800L)
        for (delay in probeDelays) {
            mainHandler.postDelayed({
                // 若当前活跃控制器已处于目标播放态，直接退出后续轮询
                if (activeControllerCompat != null && 
                    isPackageMatch(activeControllerCompat?.packageName, packageName) && 
                    activeControllerCompat?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                    return@postDelayed
                }

                // 主动刷新底层会话，确保类似汽水音乐（通知无 Token）的应用在后台就绪后第一时间捕获
                refresh(context)

                val elapsed = System.currentTimeMillis() - lastWakeUpTimestamp
                val currentControllers = allControllersCompat.map { it.packageName }.joinToString(",")
                val newTarget = allControllersCompat.firstOrNull { isPackageMatch(it.packageName, packageName) }

                FahrmonyLogBuffer.addLog(
                    "PROBE_${delay}MS",
                    "${delay}ms阶梯探测检查",
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
            }, delay)
        }
    }

    fun getLastCarConnectedTimestamp(): Long = lastCarConnectedTimestamp

    fun onCarConnected(context: Context) {
        // 重置元数据缓存键，确保无论是初次连接还是断开重连，都强制向车机推送完整的封面大图与曲目信息
        lastSyncedTrackKey = null
        currentTrackArtworkBitmap = null
        lastRawSourceGenId = -1
        lastRawSourceUri = null
        cachedRawArtworkBitmap = null
        val now = System.currentTimeMillis()
        if (now - lastCarConnectedTimestamp < 1500L) {
            return
        }
        lastCarConnectedTimestamp = now
        handshakeProtectionUntil = now + 1200L
        disconnectSuppressionUntil = 0L
        // 动态继承状态基线：若当前活跃会话为网易云且已在播放/暂停，直接对齐状态与时长，消除连车后首次点按被当作状态跳变拦截
        if (activeControllerCompat?.packageName == "com.netease.cloudmusic") {
            val activeState = activeControllerCompat?.playbackState?.state ?: PlaybackStateCompat.STATE_NONE
            lastNeteasePlaybackState = activeState
            val activeDur = activeControllerCompat?.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
            if (activeDur > 0L) {
                lastNeteaseTrackDuration = activeDur
            }
            activeControllerCompat?.playbackState?.position?.let {
                lastNeteaseTrackPosition = it
                lastNeteaseStateTimestamp = now
            }
        } else {
            lastNeteasePlaybackState = PlaybackStateCompat.STATE_NONE
        }

        // 关键防护：重连时立即移除任何排期的延迟断连暂停任务，彻底消除热重连起播后被旧任务按停
        pendingDisconnectPauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDisconnectPauseRunnable = null

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
    private var pendingDisconnectPauseRunnable: Runnable? = null

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
        // 连接主权守卫：若当前车机服务处于活跃连接中，坚决丢弃任何外部误判广播引发的断连
        if (FahrmonyMediaBrowserService.isCarConnected) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastCarDisconnectedTimestamp < 1000L) {
            return
        }
        lastCarDisconnectedTimestamp = now
        lastNeteasePlaybackState = PlaybackStateCompat.STATE_NONE
        // 设立 3000ms 断连压制窗口，拦截冷启动时前台 Activity 重新聚焦/音频重构引发的自发起播
        disconnectSuppressionUntil = now + 3000L

        FahrmonyLogBuffer.addLog("CAR_DISCONNECT", "车机断连", "收到物理断连事件", "执行手机端物理静音与全播放器双脉冲暂停")

        // 1. 手机端物理级内核音频焦点抢占 (直接从 OS 音频驱动层剥夺焦点并强行静音，对系统所有播放器通用生效)
        appContext?.let { requestTransientAudioFocusMute(it) }

        // 2. 跨进程 IPC 全量下发暂停：遍历所有活跃会话，确保所有播放器无一遗漏地进入暂停态
        pauseAllPlayers()

        // 3. 延迟 250ms 再次下发二次确认暂停，跨越物理拔线与底层 AudioTrack 声道重构震荡窗口 (执行前再次校验连接态)
        pendingDisconnectPauseRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pendingDisconnectPauseRunnable = null
            if (!FahrmonyMediaBrowserService.isCarConnected) {
                pauseAllPlayers()
            }
        }
        pendingDisconnectPauseRunnable = r
        mainHandler.postDelayed(r, 250L)
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
                    pendingAutoPlayDeadline = System.currentTimeMillis() + 10000L
                    switchToPackage(context, defaultPkg)
                }
            }
            // 再次强化推送一次，巩固 Fahrmony 的车机主导地位
            pushMirrorState()
        }, 500L)
    }
}
