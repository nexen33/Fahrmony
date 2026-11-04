package com.fahrmony.app.nativebridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.fahrmony.app.nativebridge.im.ImReplyBridge
import com.fahrmony.app.nativebridge.im.ImVoipCallBridge

/**
 * 专用于承接 Android Auto 车载 MessagingStyle 消息的标准回调接收器
 * 严格遵循 Google Android Auto 规范：支持“标记已读”消除通知与“语音回复”跨进程转发
 */
class FahrmonyMessageReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_AS_READ = "com.fahrmony.app.ACTION_MARK_AS_READ"
        const val ACTION_REPLY = "com.fahrmony.app.ACTION_REPLY"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_VOICE_REPLY = "extra_voice_reply"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (action) {
            ACTION_MARK_AS_READ -> {
                if (notifId != -1) {
                    if (ImVoipCallBridge.onCarMarkAsRead(context, notifId)) {
                        return
                    }
                    ImReplyBridge.removeById(notifId)
                    try {
                        NotificationManagerCompat.from(context).cancel(notifId)
                        FahrmonyLogBuffer.addLog("IM_ACTION", "车机已读", "驾驶员在车机点击了标记已读", "已清除通知 ID: $notifId")
                    } catch (ignored: Exception) {}
                }
            }
            ACTION_REPLY -> {
                val bundle = RemoteInput.getResultsFromIntent(intent)
                val replyText = bundle?.getCharSequence(EXTRA_VOICE_REPLY)?.toString()?.trim() ?: ""
                if (notifId != -1 && ImVoipCallBridge.onCarVoiceReply(context, notifId, replyText)) {
                    return
                }
                if (notifId != -1 && replyText.isNotEmpty()) {
                    ImReplyBridge.dispatchVoiceReply(context, notifId, replyText)
                }
                if (notifId != -1) {
                    try {
                        NotificationManagerCompat.from(context).cancel(notifId)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }
}
