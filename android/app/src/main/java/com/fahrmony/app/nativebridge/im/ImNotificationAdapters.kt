package com.fahrmony.app.nativebridge.im

import android.os.Bundle

/**
 * 跨 IM 归一化消息实体 (提供给 Android Auto MessagingStyle 渲染)
 */
data class ParsedImMessage(
    val senderName: String,
    val messageBody: String,
    val conversationName: String,
    val isGroup: Boolean,
    val brandColor: Int
)

/**
 * IM 通知独立适配策略接口
 */
interface ImNotificationAdapter {
    fun isGroup(extras: Bundle, title: String, text: String): Boolean
    fun parseMessage(rawTitle: String, rawText: String, isGroup: Boolean, appName: String): ParsedImMessage
}

/**
 * 微信专属适配器 (保留并固化原有的成熟稳定逻辑)
 */
class WeChatNotificationAdapter : ImNotificationAdapter {
    override fun isGroup(extras: Bundle, title: String, text: String): Boolean {
        if (extras.getBoolean("android.isGroupConversation", false)) return true
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString() ?: ""
        if (convTitle.isNotBlank()) return true

        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

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
            val isSelfSummary = candidate.equals(title, ignoreCase = true) ||
                    candidate.endsWith(title, ignoreCase = true) ||
                    title.endsWith(candidate, ignoreCase = true)
            val isCommonMessagePrefix = candidate in setOf("时间", "地点", "注意", "提示", "链接", "电话", "地址", "开会", "电影", "回复", "提醒", "通知", "PS", "ps")

            if (candidate.isNotEmpty() && !candidate.contains('\n') && !isSelfSummary && !isCommonMessagePrefix) {
                if (title.contains("群") || subText.isNotBlank()) {
                    return true
                }
            }
        }
        return false
    }

    override fun parseMessage(rawTitle: String, rawText: String, isGroup: Boolean, appName: String): ParsedImMessage {
        var senderName = rawTitle.ifBlank { appName }
        var messageBody = rawText
        var conversationName = appName

        val cleanedText = rawText.replace(Regex("""^\[\d+条\]\s*"""), "").trim()

        if (isGroup) {
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..25) {
                senderName = cleanedText.substring(0, colonIdx).trim()
                messageBody = cleanedText.substring(colonIdx + 1).trim()
            } else {
                messageBody = cleanedText
            }
            conversationName = rawTitle.replace(Regex("""[(\uff08]\d{1,4}[)\uff09]$"""), "").trim().ifBlank { appName }
        } else {
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
        return ParsedImMessage(
            senderName = senderName,
            messageBody = messageBody,
            conversationName = conversationName,
            isGroup = isGroup,
            brandColor = 0xFF07C160.toInt() // 微信绿
        )
    }
}

/**
 * QQ 专属适配器 (独立单聊/群聊与前缀清洗)
 */
class QQNotificationAdapter : ImNotificationAdapter {
    override fun isGroup(extras: Bundle, title: String, text: String): Boolean {
        if (extras.getBoolean("android.isGroupConversation", false)) return true
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString() ?: ""
        if (convTitle.isNotBlank()) return true

        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        // 1. QQ 群与讨论组特征：标题带人数括号 (45)、以 [群聊] 开头，或包含 "群" / "讨论组" / "频道"
        if (title.contains(Regex("""[(\uff08]\s*\d{1,4}\s*[)\uff09]$""")) ||
            title.startsWith("[群聊]") ||
            title.contains("群") ||
            title.contains("讨论组") ||
            title.contains("频道")) {
            return true
        }
        if (subText.contains("群") || subText.contains("讨论组") || subText.contains("Group", ignoreCase = true)) {
            return true
        }

        // 2. 正文冒号切分：QQ 群发信人通常为 "昵称: 内容" 或 "[2条]昵称: 内容"
        val cleanCandidateText = text.replace(Regex("""^\[\d+条\]\s*"""), "").trim()
        val colonIdx = cleanCandidateText.indexOfAny(charArrayOf(':', '：'))
        if (colonIdx in 1..25 && !cleanCandidateText.startsWith("http:", ignoreCase = true) && !cleanCandidateText.startsWith("https:", ignoreCase = true)) {
            val candidate = cleanCandidateText.substring(0, colonIdx).trim()
            val isSelfSummary = candidate.equals(title, ignoreCase = true) ||
                    candidate.endsWith(title, ignoreCase = true) ||
                    title.endsWith(candidate, ignoreCase = true)
            val isCommonMessagePrefix = candidate in setOf("时间", "地点", "注意", "提示", "链接", "电话", "地址", "开会", "提醒", "通知")

            if (candidate.isNotEmpty() && !candidate.contains('\n') && !isSelfSummary && !isCommonMessagePrefix) {
                return true
            }
        }
        return false
    }

    override fun parseMessage(rawTitle: String, rawText: String, isGroup: Boolean, appName: String): ParsedImMessage {
        var senderName = rawTitle.ifBlank { appName }
        var messageBody = rawText
        var conversationName = appName

        // 清洗 QQ 常见的汇总条数前缀与特殊标识
        var cleanedText = rawText.replace(Regex("""^\[\d+条\]\s*"""), "").trim()
        cleanedText = cleanedText.replace(Regex("""^\[(特别关心|特别关注|QQ电话|语音通话|视频通话)\]\s*"""), "").trim()

        if (isGroup) {
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..25) {
                var candidateSender = cleanedText.substring(0, colonIdx).trim()
                // 清洗 QQ 群发言人附带的头衔后缀 (如 "张三(群主)"、"李四(管理员)")
                candidateSender = candidateSender.replace(Regex("""[(\uff08](群主|管理员|堂主|长老|护法|帮主)[)\uff09]"""), "").trim()
                senderName = candidateSender.ifBlank { rawTitle }
                messageBody = cleanedText.substring(colonIdx + 1).trim()
            } else {
                messageBody = cleanedText
            }
            conversationName = rawTitle.replace(Regex("""[(\uff08]\d{1,4}[)\uff09]$"""), "").trim().ifBlank { appName }
        } else {
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
        return ParsedImMessage(
            senderName = senderName,
            messageBody = messageBody,
            conversationName = conversationName,
            isGroup = isGroup,
            brandColor = 0xFF12B7F5.toInt() // QQ 官方标志蓝
        )
    }
}

/**
 * 飞书专属适配器 (解除 subText 强约束，清洗飞书特有 @ 标签)
 */
class FeishuNotificationAdapter : ImNotificationAdapter {
    private fun isGenericAppTitle(title: String): Boolean {
        val t = title.trim()
        return t.equals("飞书", ignoreCase = true) ||
               t.equals("Lark", ignoreCase = true) ||
               t.equals("Feishu", ignoreCase = true)
    }

    override fun isGroup(extras: Bundle, title: String, text: String): Boolean {
        if (extras.getBoolean("android.isGroupConversation", false)) return true
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString() ?: ""
        if (convTitle.isNotBlank()) return true

        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        if (title.contains(Regex("""[(\uff08]\s*\d{1,4}\s*[)\uff09]$""")) || title.startsWith("[群聊]") || title.contains("群")) {
            return true
        }
        if (subText.contains("群") || subText.contains("Group", ignoreCase = true)) {
            return true
        }
        // 当通知标题仅为应用名（如 Lark / 飞书）且无显式群特征时，判定为单聊（发件人位于正文冒号前）
        if (isGenericAppTitle(title)) {
            return false
        }

        // 飞书工作群：剥离条数与 @ 前缀后，若发言人与群名不同，精准认定为群聊 (彻底解除对 subText 的依赖)
        val cleanCandidateText = text.replace(Regex("""^\[\d+条\]\s*"""), "")
            .replace(Regex("""^\[(有人@了你|@所有人|有人回复了你|有人提到你)\]\s*"""), "").trim()
        val colonIdx = cleanCandidateText.indexOfAny(charArrayOf(':', '：'))
        if (colonIdx in 1..35 && !cleanCandidateText.startsWith("http:", ignoreCase = true) && !cleanCandidateText.startsWith("https:", ignoreCase = true)) {
            val candidate = cleanCandidateText.substring(0, colonIdx).trim()
            val isSelfSummary = candidate.equals(title, ignoreCase = true) || title.endsWith(candidate, ignoreCase = true)
            val isCommonMessagePrefix = candidate in setOf("时间", "地点", "注意", "提示", "链接", "电话", "地址", "开会", "提醒")
            if (candidate.isNotEmpty() && !candidate.contains('\n') && !isSelfSummary && !isCommonMessagePrefix) {
                return true
            }
        }
        return false
    }

    override fun parseMessage(rawTitle: String, rawText: String, isGroup: Boolean, appName: String): ParsedImMessage {
        var senderName = rawTitle.ifBlank { appName }
        var messageBody = rawText
        var conversationName = appName

        var cleanedText = rawText.replace(Regex("""^\[\d+条\]\s*"""), "").trim()
        cleanedText = cleanedText.replace(Regex("""^\[(有人@了你|@所有人|有人回复了你|有人提到你)\]\s*"""), "").trim()

        if (isGroup) {
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..35) {
                senderName = cleanedText.substring(0, colonIdx).trim()
                messageBody = cleanedText.substring(colonIdx + 1).trim()
            } else {
                messageBody = cleanedText
            }
            conversationName = rawTitle.replace(Regex("""[(\uff08]\d{1,4}[)\uff09]$"""), "").trim().ifBlank { appName }
        } else {
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..35 && !cleanedText.startsWith("http:", ignoreCase = true) && !cleanedText.startsWith("https:", ignoreCase = true)) {
                val prefix = cleanedText.substring(0, colonIdx).trim()
                if (isGenericAppTitle(rawTitle)) {
                    senderName = prefix.ifBlank { appName }
                    messageBody = cleanedText.substring(colonIdx + 1).trim()
                } else if (prefix.equals(senderName, ignoreCase = true) || senderName.endsWith(prefix, ignoreCase = true)) {
                    messageBody = cleanedText.substring(colonIdx + 1).trim()
                } else {
                    messageBody = cleanedText
                }
            } else {
                messageBody = cleanedText
            }
        }
        return ParsedImMessage(
            senderName = senderName,
            messageBody = messageBody,
            conversationName = conversationName,
            isGroup = isGroup,
            brandColor = 0xFF00D6B9.toInt() // 飞书蓝绿
        )
    }
}

/**
 * 钉钉专属适配器 (独立处理企业架构与提示标签剥离)
 */
class DingTalkNotificationAdapter : ImNotificationAdapter {
    override fun isGroup(extras: Bundle, title: String, text: String): Boolean {
        if (extras.getBoolean("android.isGroupConversation", false)) return true
        val convTitle = extras.getCharSequence("android.conversationTitle")?.toString() ?: ""
        if (convTitle.isNotBlank()) return true

        val subText = extras.getCharSequence("android.subText")?.toString() ?: ""

        if (title.contains(Regex("""[(\uff08]\s*\d{1,4}\s*[)\uff09]$""")) || title.startsWith("[群聊]") || title.contains("群")) {
            return true
        }
        if (subText.contains("群") || subText.contains("Group", ignoreCase = true)) {
            return true
        }

        // 钉钉企业群：剥离提示标签后切分发言人
        val cleanCandidateText = text.replace(Regex("""^\[\d+条\]\s*"""), "")
            .replace(Regex("""^\[(有人@我|@所有人|特别关注|DING|重要)\]\s*"""), "").trim()
        val colonIdx = cleanCandidateText.indexOfAny(charArrayOf(':', '：'))
        if (colonIdx in 1..25 && !cleanCandidateText.startsWith("http:", ignoreCase = true) && !cleanCandidateText.startsWith("https:", ignoreCase = true)) {
            val candidate = cleanCandidateText.substring(0, colonIdx).trim()
            val isSelfSummary = candidate.equals(title, ignoreCase = true) || title.endsWith(candidate, ignoreCase = true)
            val isCommonMessagePrefix = candidate in setOf("时间", "地点", "注意", "提示", "链接", "电话", "地址", "开会", "提醒")
            if (candidate.isNotEmpty() && !candidate.contains('\n') && !isSelfSummary && !isCommonMessagePrefix) {
                return true
            }
        }
        return false
    }

    override fun parseMessage(rawTitle: String, rawText: String, isGroup: Boolean, appName: String): ParsedImMessage {
        var senderName = rawTitle.ifBlank { appName }
        var messageBody = rawText
        var conversationName = appName

        var cleanedText = rawText.replace(Regex("""^\[\d+条\]\s*"""), "").trim()
        cleanedText = cleanedText.replace(Regex("""^\[(有人@我|@所有人|特别关注|DING|重要)\]\s*"""), "").trim()

        if (isGroup) {
            val colonIdx = cleanedText.indexOfAny(charArrayOf(':', '：'))
            if (colonIdx in 1..25) {
                senderName = cleanedText.substring(0, colonIdx).trim()
                messageBody = cleanedText.substring(colonIdx + 1).trim()
            } else {
                messageBody = cleanedText
            }
            conversationName = rawTitle.replace(Regex("""[(\uff08]\d{1,4}[)\uff09]$"""), "").trim().ifBlank { appName }
        } else {
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
        return ParsedImMessage(
            senderName = senderName,
            messageBody = messageBody,
            conversationName = conversationName,
            isGroup = isGroup,
            brandColor = 0xFF0089FF.toInt() // 钉钉蓝
        )
    }
}

/**
 * IM 策略路由总线 (单例持有各独立适配器，杜绝分支逻辑交叉污染)
 */
object ImNotificationRouter {
    private val weChatAdapter = WeChatNotificationAdapter()
    private val qqAdapter = QQNotificationAdapter()
    private val feishuAdapter = FeishuNotificationAdapter()
    private val dingTalkAdapter = DingTalkNotificationAdapter()

    fun getAdapter(packageName: String): ImNotificationAdapter? {
        return when (packageName) {
            "com.tencent.mm" -> weChatAdapter
            "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.qqlite" -> qqAdapter
            "com.ss.android.lark", "com.larksuite.suite" -> feishuAdapter
            "com.alibaba.android.rimet" -> dingTalkAdapter
            else -> null
        }
    }
}

