package com.fahrmony.app.nativebridge.im

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.fahrmony.app.nativebridge.FahrmonyCarConnectionTracker
import com.fahrmony.app.nativebridge.FahrmonyCarI18n
import com.fahrmony.app.nativebridge.FahrmonyLogBuffer
import com.fahrmony.app.nativebridge.FahrmonyMediaManager
import com.fahrmony.app.nativebridge.FahrmonyNotificationListener
import java.util.concurrent.ConcurrentHashMap

enum class CallSessionState {
    RINGING,
    MUTED_LOCALLY,
    IN_CALL
}

data class VoipCallSession(
    val packageName: String,
    @Volatile var sbnId: Int,
    val carNotifId: Int,
    val appName: String,
    val callerName: String,
    val isVideo: Boolean,
    @Volatile var answerIntent: PendingIntent?,
    @Volatile var declineIntent: PendingIntent?,
    val createdAtMs: Long = System.currentTimeMillis(),
    @Volatile var state: CallSessionState = CallSessionState.RINGING
) {
    val canDirectAnswer: Boolean
        get() = answerIntent != null && declineIntent != null
}

/**
 * 车载 IM 语音/视频来电提醒大卡桥接器与两阶段跨系统通话挂断自动复播引擎 (Phase 10.0.3)
 * - 路径 A (高优 MessagingStyle + CarExtender 车机横幅大卡): 100% 穿透 Gearhead HUD 弹窗
 * - QQ/TIM (含接听/拒绝句柄): 点[已读]直接接听 (actions[1])，点[回复]语音说“挂断/拒接”直接拒接 (actions[0])
 * - 微信/飞书/Lark: 来电大卡提醒 + 点[已读]车机静音收起 + 通中常驻通知拦截消卡
 * - 两阶段跨系统复播: +250ms 瞬态焦点回调自愈唤醒 -> +600ms 状态核检与强制恢复播放
 */
object ImVoipCallBridge {

    internal const val TAG = "FAHRMONY_IM_PROBE"
    private const val LEGACY_ACCOUNT_ID = "fahrmony_im_voip"
    private const val LEGACY_SERVICE_CLASS = "com.fahrmony.app.nativebridge.im.FahrmonyCallService"

    private const val VOIP_NOTIF_ID_MIN = 19000
    private const val VOIP_NOTIF_ID_MAX = 19099
    private const val RING_TIMEOUT_MS = 65_000L

    private val POST_CALL_KEYWORDS = arrayOf(
        "已取消", "对方已取消", "已拒绝", "对方已拒绝", "未接听", "对方未接听",
        "通话结束", "通话时长", "聊天时长", "对方忙", "忙线", "无应答", "已结束",
        "[语音通话]", "[视频通话]", "Call cancelled", "Call declined", "Missed call"
    )

    private val lock = Any()
    internal val mainHandler = Handler(Looper.getMainLooper())
    private val qqBadgeRegex = Regex("""[(\uff08]\d+条新?消息[)\uff09]$""")

    private val activeSessions = ConcurrentHashMap<String, VoipCallSession>()
    private val lastCancellationTimestamp = ConcurrentHashMap<String, Long>()

    @Volatile
    private var shouldResumeMusic = false

    @Volatile
    private var resumeTargetPackage: String? = null

    private var pendingCallPauseRunnable: Runnable? = null
    private var pendingModeWaitRunnable: Runnable? = null
    private var pendingStage1Runnable: Runnable? = null
    private var pendingStage2Runnable: Runnable? = null
    private var modeWaitRetryCount = 0

    /**
     * 启动时清理旧版遗留的 Self-Managed PhoneAccount，彻底消除系统 Telecom 抢占 IM 语音焦点
     */
    fun cleanupLegacyPhoneAccount(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
            val handle = PhoneAccountHandle(
                ComponentName(context.packageName, LEGACY_SERVICE_CLASS),
                LEGACY_ACCOUNT_ID
            )
            tm.unregisterPhoneAccount(handle)
        }
    }

    fun computeVoipCarNotificationId(packageName: String): Int {
        return when (packageName) {
            "com.tencent.mm" -> 19001
            "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite" -> 19002
            "com.ss.android.lark" -> 19003
            "com.larksuite.suite" -> 19004
            else -> 19010 + ((packageName.hashCode() and 0x7FFFFFFF) % 89)
        }
    }

    private fun getBrandColor(packageName: String): Int {
        return when (packageName) {
            "com.tencent.mm" -> 0xFF07C160.toInt()
            "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite" -> 0xFF12B7F5.toInt()
            "com.ss.android.lark", "com.larksuite.suite" -> 0xFF3370FF.toInt()
            else -> 0xFF38BDF8.toInt()
        }
    }

    /**
     * 在 FahrmonyNotificationListener 空文本门禁之前调用：
     * 1. 若命中“通话进行中/语音通话中/正在视频通话”等常驻状态通知，自动消除车机响铃卡、转入 IN_CALL 并拦截（返回 true）；
     * 2. 若命中通话已取消/已结束/对方已拒绝等残留或结束提示，吸收并消除车机来电卡并拦截（返回 true），杜绝误弹普通文本卡；
     * 3. 若命中来电响铃通知，发送高优 MessagingStyle 车载来电提醒大卡并拦截（返回 true）。
     */
    fun interceptIfVoipRelated(context: Context, sbn: StatusBarNotification, appName: String): Boolean {
        val notif = sbn.notification ?: return false
        val pkg = sbn.packageName
        val extras = notif.extras
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val rawText = (extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()?.trim() ?: ""
        val ticker = notif.tickerText?.toString()?.trim() ?: ""

        // 1. 拦截通中常驻状态通知（微信 id=40、飞书/Lark id=2069666038、QQ id=235 通中无按钮态）
        if (isOngoingCallStatus(sbn, notif, rawText, ticker)) {
            val carNotifId = computeVoipCarNotificationId(pkg)
            synchronized(lock) {
                latchPreCallPlaybackIfNeededLocked()
                cancelPendingResumeRunnablesLocked()
                val existing = activeSessions[pkg]
                if (existing != null) {
                    existing.state = CallSessionState.IN_CALL
                    existing.sbnId = sbn.id
                } else {
                    activeSessions[pkg] = VoipCallSession(
                        packageName = pkg,
                        sbnId = sbn.id,
                        carNotifId = carNotifId,
                        appName = appName,
                        callerName = rawTitle.ifBlank { appName },
                        isVideo = rawText.contains("视频"),
                        answerIntent = null,
                        declineIntent = null,
                        state = CallSessionState.IN_CALL
                    )
                }
                scheduleFallbackCallPauseLocked()
            }
            cancelCarCallBanner(context, carNotifId)
            Log.i(TAG, "[VOIP_IN_CALL_SUPPRESSED] pkg=$pkg, id=${sbn.id}, text=[$rawText] -> 已消卡并锁定 IN_CALL")
            return true
        }

        // 2. 拦截通话已取消/已结束/对方已拒绝等残留或结束提示，吸收并消除车机来电卡，杜绝误弹普通文本卡
        if (isPostCallCancellation(pkg, notif, rawText)) {
            val carNotifId = computeVoipCarNotificationId(pkg)
            cancelCarCallBanner(context, carNotifId)
            var hadSession = false
            var sessionState: CallSessionState? = null
            synchronized(lock) {
                val session = activeSessions.remove(pkg)
                if (session != null) {
                    hadSession = true
                    sessionState = session.state
                }
                if (activeSessions.isEmpty()) {
                    cancelFallbackCallPauseLocked()
                }
                lastCancellationTimestamp[pkg] = System.currentTimeMillis()
            }
            if (hadSession) {
                val delayMs = if (sessionState == CallSessionState.IN_CALL) 50L else 350L
                schedulePostCallResumeCheck(context.applicationContext, delayMs)
            }
            Log.i(TAG, "[VOIP_POST_CALL_SUPPRESSED] pkg=$pkg, id=${sbn.id}, text=[$rawText], hadSession=$hadSession -> 吸收通话取消/结束通知并结算复播")
            return true
        }

        // 3. 识别来电响铃通知
        val parsed = parseIncomingCall(sbn, notif, appName, rawTitle, rawText) ?: return false

        // EGM 加固门禁：若当前属于刚取消/挂断通话后的 1500ms 衰退期，吸收残余通知帧并返回 true，严禁向下穿透为普通聊天消息
        val lastCancelTs = lastCancellationTimestamp[pkg] ?: 0L
        if ((System.currentTimeMillis() - lastCancelTs) < 1500L) {
            Log.i(TAG, "[VOIP_IGNORE_POST_CANCEL_FLUSH] pkg=$pkg, sbnId=${sbn.id} -> 刚取消/挂断处于防抖期，吸收残余来电通知杜绝穿透普通消息卡")
            return true
        }

        synchronized(lock) {
            latchPreCallPlaybackIfNeededLocked()
            cancelPendingResumeRunnablesLocked()

            val current = activeSessions[pkg]
            if (current != null && (System.currentTimeMillis() - current.createdAtMs) < RING_TIMEOUT_MS) {
                current.sbnId = parsed.sbnId
                if (parsed.answerIntent != null) current.answerIntent = parsed.answerIntent
                if (parsed.declineIntent != null) current.declineIntent = parsed.declineIntent
                if (current.state == CallSessionState.RINGING ||
                    current.state == CallSessionState.MUTED_LOCALLY ||
                    current.state == CallSessionState.IN_CALL
                ) {
                    Log.i(TAG, "[VOIP_RING_DEDUP] 吸收同包名重复来电刷新: pkg=$pkg, sbnId=${sbn.id}, state=${current.state}")
                    return true
                }
            }
            activeSessions[pkg] = parsed
            scheduleFallbackCallPauseLocked()
        }

        val headline = FahrmonyCarI18n.getVoipCallHeadline(
            context,
            parsed.appName,
            parsed.callerName,
            parsed.isVideo
        )
        val subtitle = FahrmonyCarI18n.getVoipCallSubtitle(
            context,
            parsed.canDirectAnswer
        )

        val posted = FahrmonyNotificationListener.postCarMessagingCard(
            context = context,
            notificationId = parsed.carNotifId,
            packageName = pkg,
            appName = appName,
            senderHeader = headline,
            messageBody = subtitle,
            conversationName = appName,
            isGroup = false,
            brandColor = getBrandColor(pkg),
            logType = "VOIP_CALL_RING",
            logTitle = "车载来电提醒大卡已弹出: ${parsed.callerName}"
        )

        Log.i(
            TAG,
            "[VOIP_CALL_RING] pkg=$pkg, sbnId=${sbn.id}, carNotifId=${parsed.carNotifId}, caller=${parsed.callerName}, direct=${parsed.canDirectAnswer}, posted=$posted"
        )
        return posted
    }

    /**
     * 车机端点击 [已读] 按钮回调：
     * - 若为携带接听句柄的 QQ 来电 (canDirectAnswer == true)：一键触发 answerIntent (actions[1]) 直接接听并消卡
     * - 若为微信/飞书/Lark 来电：立即静音收起车机横幅卡并锁定 MUTED_LOCALLY
     */
    fun onCarMarkAsRead(context: Context, notifId: Int): Boolean {
        if (notifId !in VOIP_NOTIF_ID_MIN..VOIP_NOTIF_ID_MAX) return false
        var matched: VoipCallSession? = null
        synchronized(lock) {
            for (session in activeSessions.values) {
                if (session.carNotifId == notifId) {
                    session.state = CallSessionState.MUTED_LOCALLY
                    matched = session
                    break
                }
            }
        }
        cancelCarCallBanner(context, notifId)

        val session = matched ?: return true
        val answerPi = session.answerIntent
        if (session.canDirectAnswer && answerPi != null) {
            runCatching {
                ImReplyBridge.sendPendingIntentCompat(context, answerPi, null)
                FahrmonyLogBuffer.addLog(
                    type = "VOIP_ACTION",
                    tag = session.appName,
                    title = "车机已直接接听来电: ${session.callerName}",
                    content = "已触发原通知接听 PendingIntent (通知ID: $notifId)"
                )
                Log.i(TAG, "[VOIP_CAR_ANSWER] 已触发 ${session.appName} 接听动作: caller=${session.callerName}")
            }.onFailure { e ->
                Log.e(TAG, "[VOIP_CAR_ANSWER_ERR] ${e.javaClass.simpleName}: ${e.message}")
            }
        } else {
            FahrmonyLogBuffer.addLog(
                type = "VOIP_ACTION",
                tag = session.appName,
                title = "车机已静音收起来电提醒: ${session.callerName}",
                content = "已消除车机来电横幅卡并锁定本通电话不再重复弹窗"
            )
            Log.i(TAG, "[VOIP_CAR_MUTE] 已静音收起 ${session.appName} 来电提醒: caller=${session.callerName}")
        }
        return true
    }

    /**
     * 车机端点击 [回复] 语音动作回调：
     * - 针对 QQ 来电：识别“挂断/拒接/不接/忙”触发 declineIntent (actions[0])；识别“接听/好”触发 answerIntent (actions[1])
     * - 针对所有 19000..19099 来电通知统一消费并消卡，防止穿透至普通消息回复报出异常
     */
    fun onCarVoiceReply(context: Context, notifId: Int, replyText: String): Boolean {
        if (notifId !in VOIP_NOTIF_ID_MIN..VOIP_NOTIF_ID_MAX) return false
        var matched: VoipCallSession? = null
        synchronized(lock) {
            for (session in activeSessions.values) {
                if (session.carNotifId == notifId) {
                    session.state = CallSessionState.MUTED_LOCALLY
                    matched = session
                    break
                }
            }
        }
        cancelCarCallBanner(context, notifId)

        val session = matched ?: return true
        val normalized = replyText.trim().lowercase()
        val wantsAnswer = (normalized.contains("接") || normalized.contains("好") ||
            normalized.contains("answer") || normalized.contains("accept")) &&
            !normalized.contains("不接") && !normalized.contains("拒")

        val targetPi = if (wantsAnswer && session.answerIntent != null) {
            session.answerIntent
        } else {
            session.declineIntent
        }

        if (targetPi != null) {
            val isAnswering = (targetPi === session.answerIntent)
            runCatching {
                ImReplyBridge.sendPendingIntentCompat(context, targetPi, null)
                FahrmonyLogBuffer.addLog(
                    type = "VOIP_ACTION",
                    tag = session.appName,
                    title = if (isAnswering) "车机语音已接听来电: ${session.callerName}" else "车机语音已拒接来电: ${session.callerName}",
                    content = "语音指令: [$replyText]"
                )
                Log.i(TAG, "[VOIP_CAR_VOICE_ACTION] app=${session.appName}, answer=$isAnswering, cmd=[$replyText]")
            }.onFailure { e ->
                Log.e(TAG, "[VOIP_CAR_VOICE_ERR] ${e.javaClass.simpleName}: ${e.message}")
            }
        } else {
            FahrmonyLogBuffer.addLog(
                type = "VOIP_ACTION",
                tag = session.appName,
                title = "车机已静音收起来电提醒: ${session.callerName}",
                content = "语音指令 [$replyText] 已触发本地静音消卡"
            )
        }
        return true
    }

    /**
     * 监听来电通知或通中常驻通知被系统移除（挂断/拒接/对方取消）：
     * 立即消除车机来电提醒卡，并启动两阶段跨系统通话挂断自动复播引擎
     */
    fun onNotificationRemoved(context: Context, sbn: StatusBarNotification, reason: Int) {
        val notif = sbn.notification
        if (notif != null && (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }
        val pkg = sbn.packageName
        var removedSession: VoipCallSession? = null
        synchronized(lock) {
            val current = activeSessions[pkg] ?: return
            // 反向竞态保护：若已进入 IN_CALL (如微信 id=40)，忽略旧响铃通知 (如 id=41) 的延迟移除事件
            if (current.state == CallSessionState.IN_CALL && sbn.id != current.sbnId) {
                Log.i(TAG, "[VOIP_REMOVE_IGNORE_STALE] 会话已处于 IN_CALL(sbnId=${current.sbnId})，忽略旧通知移除 sbnId=${sbn.id}")
                return
            }
            activeSessions.remove(pkg)
            removedSession = current
            if (activeSessions.isEmpty()) {
                cancelFallbackCallPauseLocked()
            }
            lastCancellationTimestamp[pkg] = System.currentTimeMillis()
        }

        val session = removedSession ?: return
        cancelCarCallBanner(context, session.carNotifId)
        Log.i(
            TAG,
            "[VOIP_CALL_REMOVED] 源通知已移除 (pkg=$pkg, sbnId=${sbn.id}, state=${session.state}, reason=$reason)，启动挂断复播核检"
        )
        val initialDelayMs = if (session.state == CallSessionState.IN_CALL) 50L else 350L
        schedulePostCallResumeCheck(context.applicationContext, initialDelayMs)
    }

    fun clearAll(context: Context? = null) {
        val notifIds = mutableListOf<Int>()
        synchronized(lock) {
            for (s in activeSessions.values) {
                notifIds.add(s.carNotifId)
            }
            activeSessions.clear()
            lastCancellationTimestamp.clear()
            shouldResumeMusic = false
            resumeTargetPackage = null
            cancelFallbackCallPauseLocked()
            cancelPendingResumeRunnablesLocked()
        }
        if (context != null) {
            for (id in notifIds) {
                cancelCarCallBanner(context, id)
            }
        }
    }

    private fun cancelCarCallBanner(context: Context, carNotifId: Int) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(carNotifId)
        }
    }

    private fun latchPreCallPlaybackIfNeededLocked() {
        if (shouldResumeMusic) return
        val (wasPlaying, targetPkg) = FahrmonyMediaManager.queryPreCallPlaybackSnapshot(3500L)
        if (wasPlaying) {
            shouldResumeMusic = true
            resumeTargetPackage = targetPkg
            Log.i(TAG, "[VOIP_PRECALL_SNAPSHOT] 锁定通话前播放态: shouldResume=true, targetPkg=$targetPkg")
        } else if (activeSessions.isEmpty()) {
            shouldResumeMusic = false
            resumeTargetPackage = null
            Log.i(TAG, "[VOIP_PRECALL_SNAPSHOT] 通话前音乐处于手动暂停态: shouldResume=false")
        }
    }

    private fun scheduleFallbackCallPauseLocked() {
        cancelFallbackCallPauseLocked()
        val r = Runnable {
            pendingCallPauseRunnable = null
            if (activeSessions.isNotEmpty()) {
                FahrmonyMediaManager.pauseForCallInterrupt(resumeTargetPackage)
            }
        }
        pendingCallPauseRunnable = r
        mainHandler.postDelayed(r, 800L)
    }

    private fun cancelFallbackCallPauseLocked() {
        pendingCallPauseRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingCallPauseRunnable = null
    }

    private fun cancelPendingResumeRunnablesLocked() {
        pendingModeWaitRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingModeWaitRunnable = null
        pendingStage1Runnable?.let { mainHandler.removeCallbacks(it) }
        pendingStage1Runnable = null
        pendingStage2Runnable?.let { mainHandler.removeCallbacks(it) }
        pendingStage2Runnable = null
        modeWaitRetryCount = 0
    }

    private fun isAudioModeNormal(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val mode = am.mode
        return mode == AudioManager.MODE_NORMAL
    }

    /**
     * 两阶段跨系统通话挂断自动复播调度器：
     * 1. 先确认无活跃通话会话且系统 AudioManager.mode 已退回 MODE_NORMAL（若处于通话握手或通中则分级轮询等待）；
     * 2. Stage 1 (+250ms): 触发瞬态 AudioFocus 请求与释放脉冲，唤醒系统 AudioFocus 栈回调让播放器优先自然复播；
     * 3. Stage 2 (+600ms): 核检播放器状态，若因后台冻结 (fast_freezer) 或焦点标志丢失仍未复播，立即下发强制 play 指令。
     */
    private fun schedulePostCallResumeCheck(context: Context, delayMs: Long) {
        synchronized(lock) {
            if (!shouldResumeMusic) return
            cancelPendingResumeRunnablesLocked()
            val waitRunnable = object : Runnable {
                override fun run() {
                    synchronized(lock) {
                        if (!shouldResumeMusic || activeSessions.isNotEmpty()) {
                            pendingModeWaitRunnable = null
                            return
                        }
                        if (!FahrmonyCarConnectionTracker.isCarConnected(context)) {
                            shouldResumeMusic = false
                            resumeTargetPackage = null
                            pendingModeWaitRunnable = null
                            return
                        }
                        if (!isAudioModeNormal(context)) {
                            modeWaitRetryCount++
                            // 最长等待上限约 45 分钟 (前 30 次每 500ms，后续每 2000ms)
                            if (modeWaitRetryCount <= 1380) {
                                val nextPollMs = if (modeWaitRetryCount <= 30) 500L else 2000L
                                mainHandler.postDelayed(this, nextPollMs)
                            } else {
                                shouldResumeMusic = false
                                resumeTargetPackage = null
                                pendingModeWaitRunnable = null
                            }
                            return
                        }
                        pendingModeWaitRunnable = null
                        startTwoStageMusicResumeLocked(context)
                    }
                }
            }
            pendingModeWaitRunnable = waitRunnable
            mainHandler.postDelayed(waitRunnable, delayMs)
        }
    }

    private fun isTargetMediaPlaying(context: Context, targetPkg: String?): Boolean {
        // 1. 优先使用 FahrmonyMediaManager 已对齐的活跃 MediaSession
        val active = FahrmonyMediaManager.getActiveSessionInfo()
        if (active != null && active.isPlaying) {
            if (targetPkg.isNullOrBlank() || FahrmonyMediaManager.isPackageMatch(active.packageName, targetPkg)) {
                return true
            }
        }
        // 2. 遍历系统活跃会话列表，严格核检目标 package 是否处于 STATE_PLAYING / STATE_BUFFERING
        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return false
        val component = ComponentName(context, FahrmonyNotificationListener::class.java)
        val controllers = runCatching { mm.getActiveSessions(component) }.getOrNull() ?: return false
        val targetCtrl = if (!targetPkg.isNullOrBlank()) {
            controllers.firstOrNull { FahrmonyMediaManager.isPackageMatch(it.packageName, targetPkg) }
        } else {
            controllers.firstOrNull()
        }
        val state = targetCtrl?.playbackState?.state
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun isPostCallCancellation(
        pkg: String,
        notif: Notification,
        rawText: String
    ): Boolean {
        if (!isSupportedVoipPkg(pkg)) return false
        val isCallCat = notif.category == Notification.CATEGORY_CALL
        val hasCallSignal = isCallCat || notif.fullScreenIntent != null || activeSessions.containsKey(pkg)
        if (hasCallSignal && POST_CALL_KEYWORDS.any { rawText.contains(it) }) {
            return true
        }
        // 专用格式系统级非会话单帧通知（如 "[通话已取消]"）
        if (rawText.startsWith("[") && rawText.endsWith("]") && POST_CALL_KEYWORDS.any { rawText.contains(it) }) {
            return true
        }
        return false
    }

    private fun isSupportedVoipPkg(pkg: String): Boolean {
        return pkg == "com.tencent.mm" ||
            pkg == "com.tencent.mobileqq" || pkg == "com.tencent.tim" || pkg == "com.tencent.qqlite" ||
            pkg == "com.ss.android.lark" || pkg == "com.larksuite.suite"
    }

    private fun startTwoStageMusicResumeLocked(context: Context) {
        // 第一阶段 (t = +250ms): 触发系统音频焦点回调，观察播放器自然复播
        val stage1 = Runnable {
            synchronized(lock) {
                pendingStage1Runnable = null
                if (!shouldResumeMusic || activeSessions.isNotEmpty() || !isAudioModeNormal(context)) {
                    return@Runnable
                }
            }
            FahrmonyMediaManager.refresh(context)
            val targetPkg = resumeTargetPackage
            if (!isTargetMediaPlaying(context, targetPkg)) {
                Log.i(TAG, "[VOIP_RESUME_STAGE1] t=+250ms 触发系统音频焦点栈回调脉冲 (target=$targetPkg)")
                requestTransientFocusPulse(context)
            } else {
                Log.i(TAG, "[VOIP_RESUME_STAGE1_ALREADY_PLAYING] t=+250ms 播放器已自然恢复播放 (target=$targetPkg)")
            }
        }

        // 第二阶段 (t = +600ms): 核检状态变化，若未自愈则立即下发强制恢复播放
        val stage2 = Runnable {
            val targetPkg: String?
            synchronized(lock) {
                pendingStage2Runnable = null
                if (!shouldResumeMusic || activeSessions.isNotEmpty()) {
                    return@Runnable
                }
                if (!isAudioModeNormal(context)) {
                    // 若在 250ms..600ms 间突然重新进入通话态，退回等待队列
                    schedulePostCallResumeCheck(context, 500L)
                    return@Runnable
                }
                targetPkg = resumeTargetPackage
                shouldResumeMusic = false
                resumeTargetPackage = null
            }

            FahrmonyMediaManager.refresh(context)
            if (isTargetMediaPlaying(context, targetPkg)) {
                Log.i(TAG, "[VOIP_RESUME_STAGE1_OK] t=+600ms 核检通过：播放器已通过系统回调自然恢复播放 (target=$targetPkg)")
                FahrmonyLogBuffer.addLog(
                    type = "VOIP_MUSIC_RESUME",
                    tag = targetPkg ?: "Media",
                    title = "通话结束音乐已自然复播",
                    content = "第一阶段 (+250ms) 系统焦点回调已恢复播放"
                )
            } else {
                Log.i(TAG, "[VOIP_RESUME_STAGE2_FORCE] t=+600ms 系统回调未起播，立即下发强制恢复播放指令 (target=$targetPkg)")
                FahrmonyMediaManager.sendCommand("play", targetPkg)
                FahrmonyLogBuffer.addLog(
                    type = "VOIP_MUSIC_RESUME",
                    tag = targetPkg ?: "Media",
                    title = "通话结束已强制恢复音乐播放",
                    content = "第二阶段 (+600ms) 已下发强制 play 指令唤醒播放器"
                )
            }
        }

        pendingStage1Runnable = stage1
        pendingStage2Runnable = stage2
        mainHandler.postDelayed(stage1, 250L)
        mainHandler.postDelayed(stage2, 600L)
    }

    @Suppress("DEPRECATION")
    private fun requestTransientFocusPulse(context: Context) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val focusChangeListener = AudioManager.OnAudioFocusChangeListener { }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
                    .build()
                am.requestAudioFocus(req)
                mainHandler.postDelayed({
                    runCatching { am.abandonAudioFocusRequest(req) }
                }, 80L)
            } else {
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                mainHandler.postDelayed({
                    runCatching { am.abandonAudioFocus(focusChangeListener) }
                }, 80L)
            }
        }
    }

    private fun isOngoingCallStatus(
        sbn: StatusBarNotification,
        notif: Notification,
        rawText: String,
        ticker: String
    ): Boolean {
        val pkg = sbn.packageName
        val hasPersistentFlag = sbn.isOngoing ||
            ((notif.flags and (Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR)) != 0)

        return when (pkg) {
            "com.tencent.mm" -> {
                // 微信通中常驻通知固定 id=40 (含语音通话中、视频通话中及空文本过渡帧)
                (sbn.id == 40 && sbn.isOngoing) ||
                    (sbn.isOngoing && (rawText == "语音通话中" || rawText == "视频通话中" || ticker == "语音通话中" || ticker == "视频通话中"))
            }
            "com.ss.android.lark", "com.larksuite.suite" -> {
                // 飞书/Lark 通中常驻通知固定 id=2069666038 (flags=0x20060)，或具备常驻标志且文本精确匹配
                sbn.id == 2069666038 ||
                    (hasPersistentFlag && (rawText == "通话进行中" || rawText == "正在通话中" || rawText.equals("In a call", ignoreCase = true)))
            }
            "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite" -> {
                // QQ 通中复用 id=235 原地更新为无按钮的“正在视频通话 / 正在语音通话”
                sbn.isOngoing && notif.actions.isNullOrEmpty() &&
                    (rawText.startsWith("正在语音通话") || rawText.startsWith("正在视频通话") || rawText == "正在通话")
            }
            else -> false
        }
    }

    private fun parseIncomingCall(
        sbn: StatusBarNotification,
        notif: Notification,
        appName: String,
        rawTitle: String,
        rawText: String
    ): VoipCallSession? {
        // 严禁使用分组汇总通知建立或覆盖来电会话 (防止飞书/Lark 并发 Summary 通知引发误消卡)
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return null

        val pkg = sbn.packageName
        val carNotifId = computeVoipCarNotificationId(pkg)

        return when (pkg) {
            "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite" -> {
                if (rawText.startsWith("正在语音通话") || rawText.startsWith("正在视频通话") || rawText == "正在通话") {
                    return null
                }
                if (POST_CALL_KEYWORDS.any { rawText.contains(it) }) return null
                val isCallText = rawText.contains("正在呼叫你") ||
                    rawText.contains("邀请你语音通话") ||
                    rawText.contains("邀请你视频通话")
                if (!isCallText) return null

                var answerPi: PendingIntent? = null
                var declinePi: PendingIntent? = null
                val actions = notif.actions
                if (!actions.isNullOrEmpty()) {
                    for (act in actions) {
                        val t = act.title?.toString()?.trim() ?: ""
                        if (answerPi == null && (t.contains("接听") || t.equals("Answer", true) || t.equals("Accept", true))) {
                            answerPi = act.actionIntent
                        } else if (declinePi == null && (t.contains("拒绝") || t.contains("挂断") || t.equals("Decline", true) || t.equals("Reject", true))) {
                            declinePi = act.actionIntent
                        }
                    }
                }
                val caller = rawTitle.replace(qqBadgeRegex, "").trim().ifBlank { appName }
                val isVideo = rawText.contains("视频")
                VoipCallSession(
                    packageName = pkg,
                    sbnId = sbn.id,
                    carNotifId = carNotifId,
                    appName = appName,
                    callerName = caller,
                    isVideo = isVideo,
                    answerIntent = answerPi,
                    declineIntent = declinePi
                )
            }
            "com.tencent.mm" -> {
                if (rawText == "语音通话中" || rawText == "视频通话中") return null
                if (POST_CALL_KEYWORDS.any { rawText.contains(it) }) return null

                val isCallMeta = notif.category == Notification.CATEGORY_CALL || sbn.id == 41 || notif.fullScreenIntent != null
                val isDirectInviteText = rawText.endsWith("邀请你语音通话") ||
                    rawText.endsWith("邀请你视频通话") ||
                    rawText.endsWith("邀请你语音通话...") ||
                    rawText.endsWith("邀请你视频通话...")
                val isInviteWithCallMeta = (sbn.isOngoing || isCallMeta) && rawText.contains("邀请你") && rawText.contains("通话")
                if (!isCallMeta && !isDirectInviteText && !isInviteWithCallMeta) return null

                val prefixCaller = if (rawText.contains("邀请你")) {
                    rawText.substringBefore("邀请你").replace(Regex("""^\[\d+条\]\s*"""), "").trim()
                } else ""
                // 若非 Ongoing 通知的前缀中含有冒号（例如群聊普通消息 "张三: 李四邀请你语音通话"），且无 Call 元数据，排除误判
                if (!sbn.isOngoing && !isCallMeta && (prefixCaller.contains(':') || prefixCaller.contains('：'))) {
                    return null
                }

                val cleanTitle = if (rawTitle.equals("微信", true) || rawTitle.equals("WeChat", true)) "" else rawTitle
                val caller = cleanTitle.ifBlank { prefixCaller.ifBlank { appName } }
                val isVideo = rawText.contains("视频")
                VoipCallSession(
                    packageName = pkg,
                    sbnId = sbn.id,
                    carNotifId = carNotifId,
                    appName = appName,
                    callerName = caller,
                    isVideo = isVideo,
                    answerIntent = null,
                    declineIntent = null
                )
            }
            "com.ss.android.lark", "com.larksuite.suite" -> {
                if (rawText == "通话进行中" || rawText == "正在通话中" || rawText.equals("In a call", ignoreCase = true)) {
                    return null
                }
                if (POST_CALL_KEYWORDS.any { rawText.contains(it) }) return null

                val hasCallSignal = notif.category == Notification.CATEGORY_CALL || sbn.isOngoing || notif.fullScreenIntent != null
                val isInviteText = rawText.contains("邀请你") && (rawText.contains("通话") || rawText.contains("会议"))
                if (!hasCallSignal || !isInviteText) return null

                val prefixCaller = if (rawText.contains("邀请你")) {
                    rawText.substringBefore("邀请你").trim()
                } else ""
                val isGenericTitle = rawTitle.equals("飞书", true) || rawTitle.equals("Lark", true) || rawTitle.equals("Feishu", true)
                val caller = if (!isGenericTitle && rawTitle.isNotBlank()) {
                    rawTitle
                } else {
                    prefixCaller.ifBlank { appName }
                }
                val isVideo = rawText.contains("视频") || rawText.contains("会议")
                VoipCallSession(
                    packageName = pkg,
                    sbnId = sbn.id,
                    carNotifId = carNotifId,
                    appName = appName,
                    callerName = caller,
                    isVideo = isVideo,
                    answerIntent = null,
                    declineIntent = null
                )
            }
            else -> null
        }
    }
}
