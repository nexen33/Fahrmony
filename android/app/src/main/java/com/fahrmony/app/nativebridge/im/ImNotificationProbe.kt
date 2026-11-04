package com.fahrmony.app.nativebridge.im

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.fahrmony.app.nativebridge.FahrmonyLogBuffer

/**
 * 四大 IM 通知与来电五维结构化 ADB 深度探针引擎
 * 监控指令: adb logcat -v time -s FAHRMONY_IM_PROBE:I FahrmonyProbe:I
 */
object ImNotificationProbe {

    private const val TAG = "FAHRMONY_IM_PROBE"

    fun inspectOnPosted(context: Context, sbn: StatusBarNotification, appName: String) {
        try {
            val notif = sbn.notification ?: return
            val extras = notif.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: ""

            // 1. 基础元数据与标志位
            val isOngoing = (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            val isInsistent = (notif.flags and Notification.FLAG_INSISTENT) != 0
            val metaSummary = "pkg=${sbn.packageName}($appName) | id=${sbn.id} | tag=${sbn.tag ?: "null"} | " +
                    "cat=${notif.category ?: "null"} | flags=0x${Integer.toHexString(notif.flags)}(ongoing=$isOngoing,insistent=$isInsistent) | " +
                    "fullScreenPI=${notif.fullScreenIntent != null} | contentPI=${notif.contentIntent != null} | " +
                    "ticker=${notif.tickerText ?: "null"} | title=[$title] | text=[$text]"
            Log.i(TAG, "[POST_1_META] $metaSummary")

            // 2. 标准 actions 槽位解剖
            val actionCount = NotificationCompat.getActionCount(notif)
            val stdActionsSummary = if (actionCount == 0) {
                "none"
            } else {
                (0 until actionCount).mapNotNull { idx ->
                    val act = NotificationCompat.getAction(notif, idx) ?: return@mapNotNull null
                    val rInputs = act.remoteInputs?.joinToString(",") {
                        "${it.resultKey}(label=${it.label},free=${it.allowFreeFormInput})"
                    } ?: "noRemoteInput"
                    "[$idx: title='${act.title}', sem=${act.semanticAction}, hasPI=${act.actionIntent != null}, remoteInputs=($rInputs)]"
                }.joinToString("; ")
            }
            Log.i(TAG, "[POST_2_STD_ACTIONS] count=$actionCount -> $stdActionsSummary")

            // 3. 穿戴扩展 (WearableExtender) / 隐式动作 (invisibleActions) / 车载扩展 (CarExtender) 解剖
            val wearActions = runCatching { NotificationCompat.WearableExtender(notif).actions }.getOrNull().orEmpty()
            val wearSummary = if (wearActions.isEmpty()) "none" else wearActions.joinToString("; ") { act ->
                val rInputs = act.remoteInputs?.joinToString(",") { "${it.resultKey}(${it.label})" } ?: "none"
                "[title='${act.title}', hasPI=${act.actionIntent != null}, remoteInputs=($rInputs)]"
            }
            val invActions = runCatching { NotificationCompat.getInvisibleActions(notif) }.getOrNull().orEmpty()
            val invSummary = if (invActions.isEmpty()) "none" else invActions.joinToString("; ") { act ->
                val rInputs = act.remoteInputs?.joinToString(",") { "${it.resultKey}(${it.label})" } ?: "none"
                "[title='${act.title}', hasPI=${act.actionIntent != null}, remoteInputs=($rInputs)]"
            }
            val carConv = runCatching { NotificationCompat.CarExtender(notif).unreadConversation }.getOrNull()
            val carSummary = if (carConv == null) {
                "none"
            } else {
                "hasReplyPI=${carConv.replyPendingIntent != null}, remoteInput=${carConv.remoteInput?.resultKey ?: "null"}"
            }
            Log.i(TAG, "[POST_3_EXT_ACTIONS] wear($wearSummary) | invisible($invSummary) | car($carSummary)")

            // 4. CallStyle 与关键 Extras 键值解剖
            val extraKeysSummary = if (extras == null) {
                "null"
            } else {
                val keys = extras.keySet().orEmpty()
                val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: "none"
                val hasAnswerPi = extras.get("android.answerIntent") != null
                val hasDeclinePi = extras.get("android.declineIntent") != null
                val hasHangUpPi = extras.get("android.hangUpIntent") != null
                val callType = extras.get("android.callType")?.toString() ?: "none"
                val allKeys = keys.joinToString(", ") { k ->
                    val v = runCatching { extras.get(k) }.getOrNull()
                    val vType = v?.javaClass?.simpleName ?: "null"
                    val vPreview = when (v) {
                        is PendingIntent -> "PendingIntent(${v.creatorPackage})"
                        is CharSequence -> "'${v.take(30)}'"
                        else -> v.toString().take(30)
                    }
                    "$k($vType)=$vPreview"
                }
                "template=$template, answerPI=$hasAnswerPi, declinePI=$hasDeclinePi, hangUpPI=$hasHangUpPi, callType=$callType | keys=[$allKeys]"
            }
            Log.i(TAG, "[POST_4_EXTRAS] $extraKeysSummary")

            // 5. 自定义 RemoteViews 反射探针 (针对微信/QQ 非标来电布局)
            val rvSummary = inspectRemoteViews(context, sbn.packageName, notif)
            Log.i(TAG, "[POST_5_REMOTEVIEWS] $rvSummary")

            // 截断摘要 (<= 500 字符) 写入 FahrmonyLogBuffer，保护跨进程 Messenger IPC 负载
            val compactProbe = "cat=${notif.category ?: "-"},ongoing=$isOngoing,fullScreen=${notif.fullScreenIntent != null}," +
                    "stdAct=$actionCount($stdActionsSummary),wear=${wearActions.size},inv=${invActions.size},rv=$rvSummary"
            FahrmonyLogBuffer.addLog(
                type = "IM_PROBE",
                tag = appName,
                title = "IM深度探针: ${title.ifBlank { sbn.packageName }}",
                content = compactProbe.take(480)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "[POST_PROBE_ERR] ${t.message}")
        }
    }

    fun inspectOnRemoved(sbn: StatusBarNotification, appName: String, reason: Int) {
        try {
            val notif = sbn.notification
            val isOngoing = notif != null && (notif.flags and Notification.FLAG_ONGOING_EVENT) != 0
            Log.i(
                TAG,
                "[REMOVED] pkg=${sbn.packageName}($appName) | id=${sbn.id} | tag=${sbn.tag ?: "null"} | " +
                        "cat=${notif?.category ?: "null"} | ongoing=$isOngoing | reason=$reason"
            )
        } catch (ignored: Throwable) {}
    }

    @Suppress("DEPRECATION")
    private fun inspectRemoteViews(context: Context, pkg: String, notif: Notification): String {
        val views = listOfNotNull(
            notif.contentView?.let { "content" to it },
            notif.bigContentView?.let { "big" to it },
            notif.headsUpContentView?.let { "headsUp" to it }
        )
        if (views.isEmpty()) return "none"

        val targetRes = runCatching { context.packageManager.getResourcesForApplication(pkg) }.getOrNull()
        return views.joinToString(" | ") { (label, rv) ->
            runCatching {
                val actionsField = RemoteViews::class.java.getDeclaredField("mActions").apply { isAccessible = true }
                val actionsList = actionsField.get(rv) as? Iterable<*> ?: return@runCatching "$label:mActions=null"
                val clickItems = mutableListOf<String>()
                for (actObj in actionsList) {
                    if (actObj == null) continue
                    val clsName = actObj.javaClass.simpleName
                    val viewId = extractViewId(actObj)
                    val idName = if (viewId != 0 && targetRes != null) {
                        runCatching { targetRes.getResourceEntryName(viewId) }.getOrDefault("0x${Integer.toHexString(viewId)}")
                    } else {
                        "0x${Integer.toHexString(viewId)}"
                    }
                    val hasPi = hasNestedPendingIntent(actObj)
                    if (hasPi || clsName.contains("Click", ignoreCase = true) || clsName.contains("Intent", ignoreCase = true)) {
                        clickItems.add("$clsName(id=$idName,hasPI=$hasPi)")
                    }
                }
                "$label(total=${actionsList.count()},clicks=${clickItems.joinToString(",")})"
            }.getOrElse { err ->
                "$label(restricted:API_${Build.VERSION.SDK_INT}:${err.javaClass.simpleName})"
            }
        }
    }

    private fun extractViewId(actionObj: Any): Int {
        var curr: Class<*>? = actionObj.javaClass
        while (curr != null && curr != Any::class.java) {
            for (field in curr.declaredFields) {
                if (field.name == "viewId" || field.name == "mViewId") {
                    runCatching {
                        field.isAccessible = true
                        return field.getInt(actionObj)
                    }
                }
            }
            curr = curr.superclass
        }
        return 0
    }

    private fun hasNestedPendingIntent(obj: Any, depth: Int = 0): Boolean {
        if (depth > 2) return false
        var curr: Class<*>? = obj.javaClass
        while (curr != null && curr != Any::class.java) {
            for (field in curr.declaredFields) {
                runCatching {
                    field.isAccessible = true
                    val v = field.get(obj) ?: return@runCatching
                    if (v is PendingIntent) return true
                    if (v.javaClass.name.contains("RemoteResponse") && hasNestedPendingIntent(v, depth + 1)) {
                        return true
                    }
                }
            }
            curr = curr.superclass
        }
        return false
    }
}

