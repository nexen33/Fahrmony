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

class FahrmonyNotificationListener : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "fahrmony_im_bridge"
        const val CHANNEL_NAME = "Fahrmony IM 车机通知"

        // 目标监听包名映射
        val TARGET_PACKAGES = mapOf(
            "com.tencent.mm" to "微信",
            "com.ss.android.lark" to "飞书",
            "com.alibaba.android.rimet" to "钉钉"
        )

        var isConnected = false
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        createNotificationChannel()
        FahrmonyLogBuffer.addLog("SYSTEM", "NotificationListener", "服务已连接", "成功绑定 Android 系统通知监听权")
        FahrmonyIpcBridge.notifyCarConnectedChanged(true)
        // 通知监听器就绪后，直接初始化并唤醒 MediaManager 会话抓取
        FahrmonyMediaManager.init(applicationContext)
        FahrmonyMediaManager.refresh(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        FahrmonyLogBuffer.addLog("SYSTEM", "NotificationListener", "服务已断开", "通知监听权被系统解绑")
        FahrmonyIpcBridge.notifyCarConnectedChanged(false)
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

        if (mediaToken != null) {
            FahrmonyMediaManager.attachToken(applicationContext, packageName, mediaToken, sbn.notification?.extras)
        } else if (packageName in FahrmonyMediaManager.KNOWN_PACKAGES.keys) {
            FahrmonyMediaManager.refresh(applicationContext)
        }

        val appName = TARGET_PACKAGES[packageName] ?: return
        
        // 校验用户是否在设置中启用了该应用的通知播报
        if (!FahrmonyConfig.isAppEnabled(applicationContext, packageName)) return

        val extras = sbn.notification?.extras ?: return

        // 提取标题与正文
        val rawTitle = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val rawText = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        // 过滤空通知或锁屏无内容占位
        if (rawTitle.isBlank() && rawText.isBlank()) return

        // 深度群聊判定 (支持国内渠道版微信与飞书非标特征提取)
        val isGroup = isGroupConversation(packageName, extras, rawTitle, rawText)
        if (isGroup && FahrmonyConfig.isFilterGroupChats(applicationContext)) {
            FahrmonyLogBuffer.addLog(
                type = "IM_FILTERED",
                tag = appName,
                title = "已过滤群聊消息: $rawTitle",
                content = rawText
            )
            return
        }

        // 记录捕获日志 (保持纯净用户级标签)
        FahrmonyLogBuffer.addLog(
            type = "IM_NOTIFICATION",
            tag = appName,
            title = rawTitle.ifBlank { "新消息" },
            content = rawText.ifBlank { "已收到消息（无文本摘要）" },
            rawExtras = extras.keySet().joinToString(", ") { "$it=${extras.get(it)}" }
        )

        // 转换为 Android Auto 规范的 MessagingStyle 单向只读通知 (支持单聊/群聊结构化呈现)
        forwardToCarMessagingStyle(appName, packageName, rawTitle, rawText, isGroup, sbn.id)
    }

    private fun isGroupConversation(packageName: String, extras: android.os.Bundle, title: String, text: String): Boolean {
        // A. 系统级标准标志位 (适用于 Google Play 版或已规范化的应用)
        if (extras.getBoolean("android.isGroupConversation", false)) return true
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString() ?: ""
        if (convTitle.isNotBlank()) return true

        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        // B. 国内版微信 (com.tencent.mm) 与钉钉 (com.alibaba.android.rimet) 深度特征识别:
        if (packageName == "com.tencent.mm" || packageName == "com.alibaba.android.rimet") {
            // 1. 强特征：标题以 "[群聊]" 开头，或标题末尾带有群成员人数括号 (如 "家庭群(4)"、"技术组（38）")
            if (title.contains(Regex("""[(\uff08]\s*\d{1,4}\s*[)\uff09]$""")) || title.startsWith("[群聊]")) {
                return true
            }
            // 2. 强特征：subText 明确包含 "群" 或 "Group"
            if (subText.contains("群") || subText.contains("Group", ignoreCase = true)) {
                return true
            }
            // 3. 正文冒号协同验证：只有在会话具有群聊特征暗示时才进行冒号发言人切分，严禁将私聊正文中的冒号一票否决
            val colonIdx = text.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..20 && !text.startsWith("http:", ignoreCase = true) && !text.startsWith("https:", ignoreCase = true)) {
                val candidate = text.substring(0, colonIdx).trim()
                // 排除多条单聊合并摘要 (如 title 为 "张三", text 为 "张三: 在吗" 或 "[2条]张三: 在吗")
                val isSelfSummary = candidate.equals(title, ignoreCase = true) ||
                        candidate.endsWith(title, ignoreCase = true) ||
                        title.endsWith(candidate, ignoreCase = true)
                // 排除日常单聊常见消息前缀 (避免 "时间：明天"、"地点：公司"、"注意：..." 等被误判为群发言人)
                val isCommonMessagePrefix = candidate in setOf("时间", "地点", "注意", "提示", "链接", "电话", "地址", "开会", "电影", "回复", "提醒", "通知", "PS", "ps")

                if (candidate.isNotEmpty() && !candidate.contains('\n') && !isSelfSummary && !isCommonMessagePrefix) {
                    // 仅当标题明确包含群字样或带有群标识时，方可判定为群聊
                    if (title.contains("群") || subText.isNotBlank()) {
                        return true
                    }
                }
            }
            return false
        }

        // C. 飞书 (com.ss.android.lark) 深度特征识别:
        if (packageName == "com.ss.android.lark") {
            if (title.contains(Regex("""[(\uff08]\s*\d{1,4}\s*[)\uff09]$""")) || title.startsWith("[群聊]") || title.contains("群")) {
                return true
            }
            if (subText.contains("群") || subText.contains("Group", ignoreCase = true)) {
                return true
            }
            val colonIdx = text.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..20 && !text.startsWith("http:", ignoreCase = true) && !text.startsWith("https:", ignoreCase = true)) {
                val candidate = text.substring(0, colonIdx).trim()
                val isSelfSummary = candidate.equals(title, ignoreCase = true) || title.endsWith(candidate, ignoreCase = true)
                val isCommonMessagePrefix = candidate in setOf("时间", "地点", "注意", "提示", "链接", "电话", "地址", "开会", "提醒")
                if (candidate.isNotEmpty() && !candidate.contains('\n') && !isSelfSummary && !isCommonMessagePrefix && subText.isNotBlank()) {
                    return true
                }
            }
            return false
        }

        return false
    }

    private fun getAppIconBitmap(context: Context, packageName: String): Bitmap? {
        try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            // 严格采用车载标准 96x96 安全尺寸，杜绝跨进程 Binder Parcel 与纹理过载
            val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, 96, 96)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    private fun forwardToCarMessagingStyle(
        appName: String,
        packageName: String,
        rawTitle: String,
        rawText: String,
        isGroup: Boolean,
        originalId: Int
    ) {
        val context = applicationContext
        val notificationId = 10000 + (Math.abs(originalId) % 8000)

        // 智能解构群聊或单聊的发言人与正文
        var senderName = rawTitle.ifBlank { appName }
        var messageBody = rawText
        var conversationName = appName

        // 清洗正文中微信常见的汇总条数前缀 (如 "[2条]" 或 "[3条]")
        val cleanedText = rawText.replace(Regex("""^\[\d+条\]\s*"""), "").trim()

        if (isGroup) {
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..25) {
                senderName = cleanedText.substring(0, colonIdx).trim()
                messageBody = cleanedText.substring(colonIdx + 1).trim()
            } else {
                messageBody = cleanedText
            }
            // 清理群名称后置的成员数字 "(18)"，呈现纯净群名
            conversationName = rawTitle.replace(Regex("""[(\uff08]\d{1,4}[)\uff09]$"""), "").trim().ifBlank { appName }
        } else {
            // 单聊场景：若正文包含发信人自身前缀 (如 "Lutz: 到哪里了？")，自动剔除冒号前缀，呈现纯净消息内容
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..25) {
                val prefix = cleanedText.substring(0, colonIdx).trim()
                if (prefix.equals(senderName, ignoreCase = true) || senderName.endsWith(prefix, ignoreCase = true)) {
                    messageBody = cleanedText.substring(colonIdx + 1).trim()
                } else {
                    messageBody = cleanedText
                }
            } else {
                messageBody = cleanedText
            }
        }

        // 遵循 i18n 规范构建发件人抬头，例如 "来自Lutz:" / "From Lutz:"
        val senderHeader = FahrmonyCarI18n.getFromSenderPrefix(context, senderName)
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
        val brandColor = when (packageName) {
            "com.tencent.mm" -> 0xFF07C160.toInt()
            "com.alibaba.android.rimet" -> 0xFF0089FF.toInt()
            else -> 0xFF0052D9.toInt()
        }
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

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            val areEnabled = notificationManager.areNotificationsEnabled()
            if (!areEnabled) {
                FahrmonyLogBuffer.addLog("SYSTEM", "IM_Bridge", "通知发送受限", "系统 POST_NOTIFICATIONS 权限未授予，无法推送到车机")
                return
            }
            notificationManager.notify(notificationId, notification)
            FahrmonyLogBuffer.addLog(
                type = "IM_CAR_POST",
                tag = appName,
                title = "已推流车机MessagingStyle",
                content = "挂载AA官方Action与CarExtender就绪: [$conversationName] $senderName: $messageBody (通知ID: $notificationId)"
            )
        } catch (e: SecurityException) {
            FahrmonyLogBuffer.addLog("SYSTEM", "IM_Bridge", "重发布权限异常", e.message ?: "未知错误")
        } catch (e: Exception) {
            FahrmonyLogBuffer.addLog("SYSTEM", "IM_Bridge", "重发布异常", e.message ?: "未知错误")
        }
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
}
