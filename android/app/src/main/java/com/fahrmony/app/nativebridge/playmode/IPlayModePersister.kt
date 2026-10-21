package com.fahrmony.app.nativebridge.playmode

import com.fahrmony.app.nativebridge.CustomRepeatState

/**
 * 播放模式状态持久化策略接口
 * 负责在音频 App 自身不暴露模式读取 API 时，持久化记录车机端与手机端当前已对齐的播放模式，
 * 避免连接断开或应用重启后发生相位重置与错位。
 */
interface IPlayModePersister {
    /**
     * 判断目标应用是否支持/启用播放模式持久化记忆
     */
    fun isPersistenceSupported(packageName: String): Boolean

    /**
     * 保存指定应用的当前播放模式
     */
    fun saveMode(packageName: String, mode: CustomRepeatState)

    /**
     * 读取指定应用的最后已知播放模式
     */
    fun getSavedMode(packageName: String): CustomRepeatState?
}
