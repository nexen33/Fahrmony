package com.fahrmony.app.nativebridge.im

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.fahrmony.app.nativebridge.FahrmonyLogBuffer
import java.util.Collections

/**
 * 四大 IM 快捷回复 (Direct Reply / RemoteInput) 嗅探缓存与跨进程转发桥接器
 */
object ImReplyBridge {

    private const val TAG = "FAHRMONY_IM_PROBE"
    private const val MAX_CACHE_SIZE = 32

    data class ReplyActionEntry(
        val appName: String,
        val sourceSlot: String,
        val pendingIntent: PendingIntent,
        val remoteInputs: Array<RemoteInput>
    )

    // 单锁原子 LRU 缓存 (容量上限 32 条，消除双容器竞态与内存泄漏)
    private val replyCache: MutableMap<Int, ReplyActionEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<Int, ReplyActionEntry>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ReplyActionEntry>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )

    /**
     * 基于包名、会话 Tag 与通知 ID 的组合哈希生成车机桥接通知 ID (10000..17999)
     * 防止微信/QQ 固定 sbn.id 引发跨会话 PendingIntent 覆写串台及 Int.MIN_VALUE 溢出
     */
    fun computeBridgeNotificationId(sbn: StatusBarNotification): Int {
        val compositeKey = "${sbn.packageName}|${sbn.tag.orEmpty()}|${sbn.id}"
        return 10000 + ((compositeKey.hashCode() and 0x7FFFFFFF) % 8000)
    }

    /**
     * 按四槽位优先级嗅探原 IM 通知中的快捷回复动作并统一收敛为 AndroidX RemoteInput
     */
    fun captureReplyAction(notificationId: Int, sbn: StatusBarNotification, appName: String): Boolean {
        val notif = sbn.notification ?: return false
        val extracted = extractFromNotification(notif)
        if (extracted != null) {
            val (slot, pi, remoteInputs) = extracted
            replyCache[notificationId] = ReplyActionEntry(
                appName = appName,
                sourceSlot = slot,
                pendingIntent = pi,
                remoteInputs = remoteInputs
            )
            val keysStr = remoteInputs.joinToString(",") { it.resultKey }
            Log.i(TAG, "[REPLY_CAPTURE_OK] notifId=$notificationId, app=$appName, slot=$slot, keys=[$keysStr]")
            return true
        }
        Log.i(TAG, "[REPLY_CAPTURE_MISS] notifId=$notificationId, app=$appName (原通知未挂载 RemoteInput)")
        return false
    }

    /**
     * 将驾驶员在车机端的语音回复内容填入原 IM 的 RemoteInput 并触发 PendingIntent
     */
    fun dispatchVoiceReply(context: Context, notificationId: Int, replyText: String): Boolean {
        val entry = replyCache.remove(notificationId)
        if (entry == null) {
            Log.w(TAG, "[IM_REPLY_UNSUPPORTED] notifId=$notificationId 无可用 RemoteInput 动作缓存，无法后台静默发信")
            FahrmonyLogBuffer.addLog(
                type = "IM_ACTION",
                tag = "车机语音回复",
                title = "原通知未开放快捷回复接口",
                content = "回复正文 [$replyText] 未能下发 (目标应用原通知未挂载 RemoteInput, 通知ID: $notificationId)"
            )
            return false
        }

        return try {
            val fillInIntent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val resultsBundle = Bundle()
            for (ri in entry.remoteInputs) {
                resultsBundle.putCharSequence(ri.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(entry.remoteInputs, fillInIntent, resultsBundle)

            sendPendingIntentCompat(context, entry.pendingIntent, fillInIntent)

            Log.i(TAG, "[IM_REPLY_OK] 已通过槽位 ${entry.sourceSlot} 向 ${entry.appName} 下发语音回复: $replyText")
            FahrmonyLogBuffer.addLog(
                type = "IM_ACTION",
                tag = entry.appName,
                title = "车机语音回复已发送 (${entry.sourceSlot})",
                content = "回复正文: $replyText (通知ID: $notificationId)"
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "[IM_REPLY_ERR] 向 ${entry.appName} 下发回复异常: ${e.message}", e)
            FahrmonyLogBuffer.addLog(
                type = "IM_ACTION",
                tag = entry.appName,
                title = "车机语音回复发送异常",
                content = "${e.javaClass.simpleName}: ${e.message ?: "未知异常"} (通知ID: $notificationId)"
            )
            false
        }
    }

    fun removeById(notificationId: Int) {
        replyCache.remove(notificationId)
    }

    fun clearAll() {
        replyCache.clear()
    }

    private fun extractFromNotification(notif: Notification): Triple<String, PendingIntent, Array<RemoteInput>>? {
        // 槽位 1: 标准 notification.actions (通过 NotificationCompat.getAction 自动转换为 AndroidX RemoteInput)
        val stdCount = NotificationCompat.getActionCount(notif)
        for (i in 0 until stdCount) {
            val act = NotificationCompat.getAction(notif, i) ?: continue
            val pi = act.actionIntent ?: continue
            val inputs = act.remoteInputs
            if (!inputs.isNullOrEmpty()) {
                return Triple("std_actions[$i]", pi, inputs)
            }
        }

        // 槽位 2: 穿戴设备扩展 WearableExtender.actions (微信/QQ 手表端快捷回复通道)
        val wearActions = runCatching { NotificationCompat.WearableExtender(notif).actions }.getOrNull().orEmpty()
        for ((i, act) in wearActions.withIndex()) {
            val pi = act.actionIntent ?: continue
            val inputs = act.remoteInputs
            if (!inputs.isNullOrEmpty()) {
                return Triple("wear_actions[$i]", pi, inputs)
            }
        }

        // 槽位 3: 隐式动作槽 getInvisibleActions
        val invActions = runCatching { NotificationCompat.getInvisibleActions(notif) }.getOrNull().orEmpty()
        for ((i, act) in invActions.withIndex()) {
            val pi = act.actionIntent ?: continue
            val inputs = act.remoteInputs
            if (!inputs.isNullOrEmpty()) {
                return Triple("invisible_actions[$i]", pi, inputs)
            }
        }

        // 槽位 4: 车载扩展槽 CarExtender.unreadConversation
        val carConv = runCatching { NotificationCompat.CarExtender(notif).unreadConversation }.getOrNull()
        val carPi = carConv?.replyPendingIntent
        val carInput = carConv?.remoteInput
        if (carPi != null && carInput != null) {
            return Triple("car_extender", carPi, arrayOf(carInput))
        }

        return null
    }

    @Suppress("DEPRECATION")
    internal fun sendPendingIntentCompat(context: Context, pendingIntent: PendingIntent, fillInIntent: Intent? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 36) {
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    )
                } else {
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }
                pendingIntent.send(context, 0, fillInIntent, null, null, null, options.toBundle())
                return
            } catch (ignored: Throwable) {
                // 部分 ROM 下 Broadcast 型 PendingIntent 对 ActivityOptions 校验异常时平滑回退标准三参数发送
            }
        }
        if (fillInIntent != null) {
            pendingIntent.send(context, 0, fillInIntent)
        } else {
            pendingIntent.send()
        }
    }
}
