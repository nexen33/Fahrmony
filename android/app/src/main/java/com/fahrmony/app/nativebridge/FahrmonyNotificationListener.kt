package com.fahrmony.app.nativebridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.fahrmony.app.R
import com.fahrmony.app.nativebridge.im.ImNotificationProbe
import com.fahrmony.app.nativebridge.im.ImNotificationRouter
import com.fahrmony.app.nativebridge.im.ImReplyBridge
import com.fahrmony.app.nativebridge.im.ImVoipCallBridge
import com.fahrmony.app.nativebridge.im.ParsedImMessage

class FahrmonyNotificationListener : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "fahrmony_im_bridge"
        const val CHANNEL_NAME = "Fahrmony IM 车机通知"

        // 目标监听包名映射
        val TARGET_PACKAGES = mapOf(
            "com.tencent.mm" to "微信",
            "com.tencent.mobileqq" to "QQ",
            "com.tencent.tim" to "TIM",
            "com.tencent.qqlite" to "QQ",
            "com.ss.android.lark" to "飞书",
            "com.larksuite.suite" to "Lark",
            "com.alibaba.android.rimet" to "钉钉"
        )

        var isConnected = false
            private set

        @Volatile private var hasCompensatedColdStart = false

        private val lastNotificationArtworkMap = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

        /**
         * 自动扫描并消除由 Fahrmony 发出的所有 IM 车载转接通知残留
         * 严格基于渠道隔离，绝不影响常驻前台保活服务通知 (ID: 1001)
         */
        fun clearBridgeNotifications(context: Context) {
            ImReplyBridge.clearAll()
            ImVoipCallBridge.clearAll(context)
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                val activeNotifications = nm.activeNotifications ?: return
                for (sbn in activeNotifications) {
                    val isBridge = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        sbn.notification.channelId == CHANNEL_ID
                    } else {
                        sbn.id in 10000..19099
                    }
                    if (isBridge && sbn.id != FahrmonyForegroundService.NOTIFICATION_ID) {
                        nm.cancel(sbn.id)
                    }
                }
            } catch (ignored: Exception) {}
        }

        fun getAppIconBitmap(context: Context, packageName: String): Bitmap? {
            return try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                // 严格采用车载标准 96x96 安全尺寸，杜绝跨进程 Binder Parcel 与纹理过载
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, 96, 96)
                drawable.draw(canvas)
                bitmap
            } catch (e: Exception) {
                null
            }
        }

        /**
         * 构建并发送符合 Google Android Auto 规范的 MessagingStyle 车载横幅卡片
         * 供普通 IM 消息与车载 VoIP 来电提醒大卡共用
         */
        fun postCarMessagingCard(
            context: Context,
            notificationId: Int,
            packageName: String,
            appName: String,
            senderHeader: String,
            messageBody: String,
            conversationName: String,
            isGroup: Boolean,
            brandColor: Int,
            logType: String = "IM_CAR_POST",
            logTitle: String = "已推流车机MessagingStyle"
        ): Boolean {
            val appBitmap = getAppIconBitmap(context, packageName)

            val senderPersonBuilder = Person.Builder()
                .setName(senderHeader)
                .setBot(false)
            if (appBitmap != null) {
                senderPersonBuilder.setIcon(IconCompat.createWithBitmap(appBitmap))
            }
            val senderPerson = senderPersonBuilder.build()

            // 严格遵循 Google 官方 Android Auto 规范：单聊严禁设置 ConversationTitle，确保大标题直截显示发信人抬头
            val messagingStyle = NotificationCompat.MessagingStyle(senderPerson)
                .setGroupConversation(isGroup)
                .addMessage(messageBody, System.currentTimeMillis(), senderPerson)
            if (isGroup) {
                messagingStyle.setConversationTitle(conversationName)
            }

            // 1. 挂载“已读”语义动作 (用户在车机端可语音消除或点击已读)
            val markAsReadIntent = Intent(context, FahrmonyMessageReceiver::class.java).apply {
                action = FahrmonyMessageReceiver.ACTION_MARK_AS_READ
                putExtra(FahrmonyMessageReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val markAsReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val markAsReadLabel = FahrmonyCarI18n.getMarkAsReadLabel(context)
            val markAsReadAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                markAsReadLabel,
                markAsReadPendingIntent
            )
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()

            // 2. 严格对齐 Google 官方规范：挂载 Reply 语音回复动作与 RemoteInput 实体 (车机 HUD 浮窗准入硬性依赖)
            val replyLabel = if (FahrmonyConfig.getLanguage(context).startsWith("en", ignoreCase = true)) "Reply" else "回复"
            val remoteInput = RemoteInput.Builder(FahrmonyMessageReceiver.EXTRA_VOICE_REPLY)
                .setLabel(replyLabel)
                .build()
            val replyIntent = Intent(context, FahrmonyMessageReceiver::class.java).apply {
                action = FahrmonyMessageReceiver.ACTION_REPLY
                putExtra(FahrmonyMessageReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 100000,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            )
            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()

            // 挂载 NotificationCompat.CarExtender 车规扩展，注入应用官方品牌主色与 96x96 高清头像
            val carExtender = NotificationCompat.CarExtender()
                .setColor(brandColor)
            if (appBitmap != null) {
                carExtender.setLargeIcon(appBitmap)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_car_notification_badge)
                .setColor(0xFFFFFFFF.toInt())
                .setStyle(messagingStyle)
                .addAction(markAsReadAction)
                .addAction(replyAction)
                .extend(carExtender)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build()

            return try {
                val notificationManager = NotificationManagerCompat.from(context)
                val areEnabled = notificationManager.areNotificationsEnabled()
                if (!areEnabled) {
                    FahrmonyLogBuffer.addLog("SYSTEM", "IM_Bridge", "通知发送受限", "系统 POST_NOTIFICATIONS 权限未授予，无法推送到车机")
                    return false
                }
                notificationManager.notify(notificationId, notification)
                FahrmonyLogBuffer.addLog(
                    type = logType,
                    tag = appName,
                    title = logTitle,
                    content = "挂载AA官方Action与CarExtender就绪: [$conversationName] $senderHeader: $messageBody (通知ID: $notificationId)"
                )
                true
            } catch (e: SecurityException) {
                FahrmonyLogBuffer.addLog("SYSTEM", "IM_Bridge", "重发布权限异常", e.message ?: "未知错误")
                false
            } catch (e: Exception) {
                FahrmonyLogBuffer.addLog("SYSTEM", "IM_Bridge", "重发布异常", e.message ?: "未知错误")
                false
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        createNotificationChannel()
        FahrmonyLogBuffer.addLog("SYSTEM", "NotificationListener", "服务已连接", "成功绑定 Android 系统通知监听权")
        // 通知监听器就绪后，初始化车机连接探针与 MediaManager 会话抓取
        FahrmonyCarConnectionTracker.init(applicationContext)
        FahrmonyMediaManager.init(applicationContext)
        FahrmonyMediaManager.refresh(applicationContext)
        ImVoipCallBridge.cleanupLegacyPhoneAccount(applicationContext)

        // 副路：冷启动延迟就绪补偿（扫描服务挂载期间到达的白名单有效通知，增量补推车机）
        compensateColdStartNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        hasCompensatedColdStart = false
        ImReplyBridge.clearAll()
        ImVoipCallBridge.clearAll(applicationContext)
        FahrmonyLogBuffer.addLog("SYSTEM", "NotificationListener", "服务已断开", "通知监听权被系统解绑")
    }

    /**
     * 副路：冷启动延迟就绪补偿（扫描服务挂载期间到达的白名单有效通知，增量补推车机）
     */
    private fun compensateColdStartNotifications() {
        if (hasCompensatedColdStart) return
        hasCompensatedColdStart = true
        try {
            val activeNotifs = try {
                activeNotifications
            } catch (e: Exception) {
                FahrmonyLogBuffer.addLog("SYSTEM", "NotificationListener", "冷启动补偿异常", "获取activeNotifications失败: ${e.message}")
                null
            }
            val now = System.currentTimeMillis()
            val processUptimeMs = android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()
            val baselineTimestamp = (now - processUptimeMs) - 5000L
            val targetSbns = activeNotifs?.filter { it != null && it.packageName in TARGET_PACKAGES } ?: emptyList()

            val pendingList = targetSbns.filter { sbn ->
                sbn.postTime >= baselineTimestamp &&
                sbn.postTime >= (now - 120_000L)
            }.sortedBy { it.postTime }

            if (pendingList.isNotEmpty()) {
                FahrmonyLogBuffer.addLog(
                    "SYSTEM",
                    "NotificationListener",
                    "副路冷启动补偿",
                    "捕获到服务挂载前到达的 ${pendingList.size} 条通知，执行增量转接"
                )
                for (sbn in pendingList) {
                    onNotificationPosted(sbn)
                }
            }
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("SYSTEM", "NotificationListener", "冷启动补偿异常", "流程异常: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        // 杜绝自身重发布通知的回环拦截
        if (packageName == applicationContext.packageName) return

        // 若是媒体播放通知，优先提取 android.mediaSession Token 直接绑定并刷新 (对齐糯米播放器嗅探原理)
        val mediaToken = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sbn.notification?.extras?.getParcelable("android.mediaSession", android.media.session.MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            sbn.notification?.extras?.getParcelable("android.mediaSession")
        }

        // 提取状态栏通知的真实高清专辑封面 (基于 Android 官方规范 Icon.loadDrawable 逆向解码)
        var notificationArtwork: Bitmap? = null
        try {
            val icon = sbn.notification?.getLargeIcon()
            if (icon != null) {
                val drawable = icon.loadDrawable(applicationContext)
                if (drawable != null) {
                    val decoded = drawableToBitmap(drawable)
                    val prevBmp = lastNotificationArtworkMap[packageName]
                    if (prevBmp != null && !prevBmp.isRecycled && decoded.width == prevBmp.width && decoded.height == prevBmp.height && decoded.sameAs(prevBmp)) {
                        if (decoded != prevBmp && !decoded.isRecycled) {
                            decoded.recycle()
                        }
                        notificationArtwork = prevBmp
                    } else {
                        lastNotificationArtworkMap[packageName] = decoded
                        notificationArtwork = decoded
                    }
                }
            }
        } catch (ignored: Exception) {}

        val isSupportedMedia = FahrmonyMediaManager.isSupportedPackage(applicationContext, packageName)
        val isCandidateMedia = !packageName.startsWith("com.shopee.") && 
                packageName !in FahrmonyMediaManager.SMART_MEDIA_BLACKLIST && 
                packageName != applicationContext.packageName

        if (isSupportedMedia || (mediaToken != null && isCandidateMedia)) {
            if (notificationArtwork != null) {
                FahrmonyLogBuffer.addLog(
                    "ARTWORK_IN",
                    "通知栏封面捕获",
                    "来源: StatusBarIcon, 尺寸: ${notificationArtwork.width}x${notificationArtwork.height}, genId: ${notificationArtwork.generationId}",
                    packageName
                )
            }
            android.util.Log.i("Fahrmony_CUSTOM", "[NOTIF_POSTED] pkg: $packageName, isSupported: $isSupportedMedia, hasMediaToken: ${mediaToken != null}")
            val notif = sbn.notification
            val nExtras = notif?.extras
            if (nExtras != null) {
                try {
                    val notifKeys = nExtras.keySet() ?: emptySet<String>()
                    val notifDump = notifKeys.joinToString("; ") { k ->
                        val v = nExtras.get(k)?.toString() ?: "null"
                        val preview = if (v.length > 80) v.take(80) + "..." else v
                        "$k=[$preview]"
                    }
                    FahrmonyLogBuffer.addLog(
                        type = "NOTIF_EXTRA_PROBE",
                        tag = packageName,
                        title = "Notification Extras (${notifKeys.size} keys)",
                        content = notifDump
                    )
                } catch (ignored: Exception) {}
            }

        }

        if (mediaToken != null && (isSupportedMedia || isCandidateMedia)) {
            FahrmonyMediaManager.attachToken(applicationContext, packageName, mediaToken, sbn.notification?.extras, notificationArtwork)
        } else if (isSupportedMedia) {
            FahrmonyMediaManager.updateNotificationMeta(packageName, sbn.notification?.extras, notificationArtwork)
            FahrmonyMediaManager.refresh(applicationContext)
        }

        val appName = TARGET_PACKAGES[packageName] ?: return

        // 专项五维 ADB 深度探针 (置于车机连接与空文本门禁之前，支持脱机 ADB 抓包与自定义 RemoteViews 来电解剖)
        ImNotificationProbe.inspectOnPosted(applicationContext, sbn, appName)
        
        // 门禁：仅在车机处于活跃投屏/连接状态时处理并转译 IM 消息，离车自动静默杜绝手机端重复弹窗
        if (!FahrmonyCarConnectionTracker.isCarConnected(applicationContext)) return

        // 校验用户是否在设置中启用了该应用的通知播报
        if (!FahrmonyConfig.isAppEnabled(applicationContext, packageName)) return

        // 拦截 IM 音视频来电与通话中状态通知 (置于空文本门禁之前，以拦截微信 id=40 空文本过渡帧及触发车载 Telecom 来电大卡)
        if (ImVoipCallBridge.interceptIfVoipRelated(applicationContext, sbn, appName)) return

        val extras = sbn.notification?.extras ?: return

        // 提取标题与正文
        val rawTitle = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val rawText = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        // 过滤空通知或锁屏无内容占位
        if (rawTitle.isBlank() && rawText.isBlank()) return

        // 基于独立适配器路由判定群聊与单聊
        val adapter = ImNotificationRouter.getAdapter(packageName) ?: return
        val isGroup = adapter.isGroup(extras, rawTitle, rawText)
        if (isGroup && FahrmonyConfig.isFilterGroupChats(applicationContext)) {
            FahrmonyLogBuffer.addLog(
                type = "IM_FILTERED",
                tag = appName,
                title = "已过滤群聊消息: $rawTitle",
                content = rawText
            )
            return
        }

        // 针对各 IM 专属规则执行独立解构与净化
        val parsedMsg = adapter.parseMessage(rawTitle, rawText, isGroup, appName)

        // 记录捕获日志 (保持纯净用户级标签)
        FahrmonyLogBuffer.addLog(
            type = "IM_NOTIFICATION",
            tag = appName,
            title = parsedMsg.senderName.ifBlank { rawTitle.ifBlank { "新消息" } },
            content = parsedMsg.messageBody.ifBlank { rawText.ifBlank { "已收到消息（无文本摘要）" } },
            rawExtras = extras.keySet().joinToString(", ") { "$it=${extras.get(it)}" }
        )

        // 转换为 Android Auto 规范的 MessagingStyle 通知 (支持单聊/群聊结构化呈现与 RemoteInput 快捷回复转发)
        forwardToCarMessagingStyle(appName, packageName, parsedMsg, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        if (sbn == null) return
        val appName = TARGET_PACKAGES[sbn.packageName] ?: return
        ImNotificationProbe.inspectOnRemoved(sbn, appName, reason)
        ImVoipCallBridge.onNotificationRemoved(applicationContext, sbn, reason)
    }

    private fun forwardToCarMessagingStyle(
        appName: String,
        packageName: String,
        msg: ParsedImMessage,
        sbn: StatusBarNotification
    ) {
        val context = applicationContext
        val notificationId = ImReplyBridge.computeBridgeNotificationId(sbn)
        ImReplyBridge.captureReplyAction(notificationId, sbn, appName)

        // 遵循 i18n 规范构建发件人抬头，例如 "来自Alice:" / "From Alice:"
        val senderHeader = FahrmonyCarI18n.getFromSenderPrefix(context, msg.senderName)
        postCarMessagingCard(
            context = context,
            notificationId = notificationId,
            packageName = packageName,
            appName = appName,
            senderHeader = senderHeader,
            messageBody = msg.messageBody,
            conversationName = msg.conversationName,
            isGroup = msg.isGroup,
            brandColor = msg.brandColor
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "将中国国内社交软件通知转译为 Android Auto 规范消息"
                enableVibration(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 256
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 256
        val bitmap = Bitmap.createBitmap(width.coerceAtMost(512), height.coerceAtMost(512), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
