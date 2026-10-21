package com.fahrmony.app.nativebridge.playmode

import android.content.Context
import android.content.SharedPreferences
import com.fahrmony.app.nativebridge.CustomRepeatState

/**
 * 基于 SharedPreferences 的播放模式状态持久化实现类
 * 当前默认仅对白名单中的网易云音乐 (com.netease.cloudmusic) 启用状态记忆，
 * 为未来其他定制播放模式的音频 App 预留标准接入通道。
 */
class SharedPreferencesPlayModePersister(context: Context) : IPlayModePersister {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "fahrmony_playmode_persistence"
        private const val KEY_PREFIX = "play_mode_"
        // 白名单策略：针对定制播放模式的网易云音乐、QQ音乐、汽水音乐、波点音乐、酷我音乐与酷狗音乐正式版启用状态持久化
        private val SUPPORTED_PACKAGES = setOf(
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.luna.music",
            "cn.wenyu.bodian",
            "cn.kuwo.player",
            "com.kugou.android",
            "kugou.service"
        )
    }

    override fun isPersistenceSupported(packageName: String): Boolean {
        return SUPPORTED_PACKAGES.contains(packageName)
    }

    override fun saveMode(packageName: String, mode: CustomRepeatState) {
        if (!isPersistenceSupported(packageName)) return
        prefs.edit().putString(KEY_PREFIX + packageName, mode.name).apply()
    }

    override fun getSavedMode(packageName: String): CustomRepeatState? {
        if (!isPersistenceSupported(packageName)) return null
        val savedName = prefs.getString(KEY_PREFIX + packageName, null) ?: return null
        return try {
            CustomRepeatState.valueOf(savedName)
        } catch (_: Exception) {
            null
        }
    }
}
