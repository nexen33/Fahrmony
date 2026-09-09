package com.fahrmony.app.nativebridge

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Fahrmony 原生多端分布式配置管理器
 * 负责在 SharedPreferences 与内存间维护一致的过滤规则、默认播放器及车载自动播放策略
 */
object FahrmonyConfig {

    private const val PREF_NAME = "fahrmony_settings"
    private const val KEY_WECHAT = "wechat"
    private const val KEY_FEISHU = "feishu"
    private const val KEY_DINGTALK = "dingtalk"
    private const val KEY_QQMUSIC = "qqmusic"
    private const val KEY_NETEASE = "netease"
    private const val KEY_KUGOU = "kugou"
    private const val KEY_KUWO = "kuwo"
    private const val KEY_XIMALAYA = "ximalaya"
    private const val KEY_XIAOYUZHOU = "xiaoyuzhou"
    private const val KEY_AUTO_PLAY = "autoPlayOnConnect"
    private const val KEY_DEFAULT_PLAYER = "defaultPlayerPackage"
    private const val KEY_FILTER_GROUP = "filterGroupChats"
    private const val KEY_HIDE_PREVIEW = "hidePreviewContent"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun updateConfig(context: Context, json: JSONObject) {
        val editor = getPrefs(context).edit()
        if (json.has(KEY_WECHAT)) editor.putBoolean(KEY_WECHAT, json.optBoolean(KEY_WECHAT, true))
        if (json.has(KEY_FEISHU)) editor.putBoolean(KEY_FEISHU, json.optBoolean(KEY_FEISHU, true))
        if (json.has(KEY_DINGTALK)) editor.putBoolean(KEY_DINGTALK, json.optBoolean(KEY_DINGTALK, true))
        if (json.has(KEY_QQMUSIC)) editor.putBoolean(KEY_QQMUSIC, json.optBoolean(KEY_QQMUSIC, true))
        if (json.has(KEY_NETEASE)) editor.putBoolean(KEY_NETEASE, json.optBoolean(KEY_NETEASE, true))
        if (json.has(KEY_KUGOU)) editor.putBoolean(KEY_KUGOU, json.optBoolean(KEY_KUGOU, true))
        if (json.has(KEY_KUWO)) editor.putBoolean(KEY_KUWO, json.optBoolean(KEY_KUWO, true))
        if (json.has(KEY_XIMALAYA)) editor.putBoolean(KEY_XIMALAYA, json.optBoolean(KEY_XIMALAYA, true))
        if (json.has(KEY_XIAOYUZHOU)) editor.putBoolean(KEY_XIAOYUZHOU, json.optBoolean(KEY_XIAOYUZHOU, true))
        if (json.has(KEY_AUTO_PLAY)) editor.putBoolean(KEY_AUTO_PLAY, json.optBoolean(KEY_AUTO_PLAY, true))
        if (json.has(KEY_DEFAULT_PLAYER)) editor.putString(KEY_DEFAULT_PLAYER, json.optString(KEY_DEFAULT_PLAYER, "com.tencent.qqmusic"))
        if (json.has(KEY_FILTER_GROUP)) editor.putBoolean(KEY_FILTER_GROUP, json.optBoolean(KEY_FILTER_GROUP, false))
        if (json.has(KEY_HIDE_PREVIEW)) editor.putBoolean(KEY_HIDE_PREVIEW, json.optBoolean(KEY_HIDE_PREVIEW, false))
        editor.apply()
    }

    fun getConfig(context: Context): JSONObject {
        val sp = getPrefs(context)
        return JSONObject().apply {
            put(KEY_WECHAT, sp.getBoolean(KEY_WECHAT, true))
            put(KEY_FEISHU, sp.getBoolean(KEY_FEISHU, true))
            put(KEY_DINGTALK, sp.getBoolean(KEY_DINGTALK, true))
            put(KEY_QQMUSIC, sp.getBoolean(KEY_QQMUSIC, true))
            put(KEY_NETEASE, sp.getBoolean(KEY_NETEASE, true))
            put(KEY_KUGOU, sp.getBoolean(KEY_KUGOU, true))
            put(KEY_KUWO, sp.getBoolean(KEY_KUWO, true))
            put(KEY_XIMALAYA, sp.getBoolean(KEY_XIMALAYA, true))
            put(KEY_XIAOYUZHOU, sp.getBoolean(KEY_XIAOYUZHOU, true))
            put(KEY_AUTO_PLAY, sp.getBoolean(KEY_AUTO_PLAY, true))
            put(KEY_DEFAULT_PLAYER, sp.getString(KEY_DEFAULT_PLAYER, "com.tencent.qqmusic"))
            put(KEY_FILTER_GROUP, sp.getBoolean(KEY_FILTER_GROUP, false))
            put(KEY_HIDE_PREVIEW, sp.getBoolean(KEY_HIDE_PREVIEW, false))
        }
    }

    fun isAppEnabled(context: Context, packageName: String): Boolean {
        val sp = getPrefs(context)
        return when (packageName) {
            "com.tencent.mm" -> sp.getBoolean(KEY_WECHAT, true)
            "com.ss.android.lark" -> sp.getBoolean(KEY_FEISHU, true)
            "com.alibaba.android.rimet" -> sp.getBoolean(KEY_DINGTALK, true)
            "com.tencent.qqmusic" -> sp.getBoolean(KEY_QQMUSIC, true)
            "com.netease.cloudmusic" -> sp.getBoolean(KEY_NETEASE, true)
            "kugou.service", "com.kugou.android" -> sp.getBoolean(KEY_KUGOU, true)
            "cn.kuwo.player" -> sp.getBoolean(KEY_KUWO, true)
            "com.ximalaya.ting.android" -> sp.getBoolean(KEY_XIMALAYA, true)
            "app.podcast.cosmos" -> sp.getBoolean(KEY_XIAOYUZHOU, true)
            else -> true
        }
    }

    fun isFilterGroupChats(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FILTER_GROUP, false)
    }

    fun isAutoPlayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_PLAY, true)
    }

    fun getDefaultPlayer(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_PLAYER, "com.tencent.qqmusic") ?: "com.tencent.qqmusic"
    }

    fun getLanguage(context: Context): String {
        try {
            val capPrefs = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
            val lang = capPrefs.getString("fahrmony_lang", null)
            if (!lang.isNullOrBlank()) return lang

            val appPrefs = getPrefs(context)
            val custom = appPrefs.getString("fahrmony_lang", null)
            if (!custom.isNullOrBlank()) return custom
        } catch (ignored: Exception) {}
        return "zh-CN"
    }
}

/**
 * 车机端专属国际化文案字典 (精简适配 Coolwalk 1/3 卡片宽度)
 * 中文显示 "Fahrmony 合拍"，英/德/日显示 "Fahrmony Plugin"
 */
object FahrmonyCarI18n {
    fun getAppTitle(context: Context): String {
        val lang = FahrmonyConfig.getLanguage(context)
        return if (lang.startsWith("zh", ignoreCase = true)) {
            "Fahrmony 合拍"
        } else {
            "Fahrmony Plugin"
        }
    }

    fun getWaitingSubtitle(context: Context): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "Waiting for audio"
            "de-DE" -> "Warte auf Audio"
            "ja-JP" -> "再生待機中"
            else -> "等待音乐播放中"
        }
    }

    fun getReadySubtitle(context: Context): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "Ready · Tap to play"
            "de-DE" -> "Bereit · Starten"
            "ja-JP" -> "準備完了 · タップで再生"
            else -> "已就绪 · 点击开始播放"
        }
    }

    fun getActiveSourceSubtitle(context: Context): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "Active Source"
            "de-DE" -> "Aktive Quelle"
            "ja-JP" -> "現在の音源"
            else -> "当前播放源 (活跃)"
        }
    }

    fun getSwitchSourceSubtitle(context: Context): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "Tap to switch"
            "de-DE" -> "Tippen zum Wechseln"
            "ja-JP" -> "タップして切替"
            else -> "点击切换到该应用"
        }
    }

    fun getNowPlayingDefault(context: Context): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "Now Playing"
            "de-DE" -> "Aktuell läuft"
            "ja-JP" -> "再生中"
            else -> "正在播放"
        }
    }

    fun getFromSenderPrefix(context: Context, senderName: String): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "From $senderName "
            "de-DE" -> "Von $senderName "
            "ja-JP" -> "${senderName} より "
            else -> "来自 ${senderName} "
        }
    }

    fun getMarkAsReadLabel(context: Context): String {
        return when (FahrmonyConfig.getLanguage(context)) {
            "en-US" -> "Mark as read"
            "de-DE" -> "Gelesen"
            "ja-JP" -> "既読"
            else -> "已读"
        }
    }
}

