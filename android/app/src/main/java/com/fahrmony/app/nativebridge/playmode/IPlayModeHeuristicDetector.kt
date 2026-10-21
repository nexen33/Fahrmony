package com.fahrmony.app.nativebridge.playmode

import android.support.v4.media.session.MediaControllerCompat
import com.fahrmony.app.nativebridge.CustomRepeatState

/**
 * 播放模式启发式自愈探测接口
 * 负责在切歌、自然播放流转等事件中，依据播放行为特征（如连播同一首歌）静默识别出真实的播放模式，
 * 实现运行时免人工干预的相位误差自愈。
 */
interface IPlayModeHeuristicDetector {
    /**
     * 判断目标应用是否支持/启用启发式自愈探测
     */
    fun isSupported(packageName: String): Boolean

    /**
     * 在播放状态推进时持续采样物理进度，用于状态机武装与特征感知
     */
    fun onPlaybackPositionUpdated(
        controller: MediaControllerCompat,
        mediaId: String?,
        duration: Long,
        position: Long
    )

    /**
     * 在发生切歌或曲目状态变化时被调用
     * @return 若能 100% 确认真实模式，返回对应的 CustomRepeatState；否则返回 null 不做干预
     */
    fun onTrackChanged(
        controller: MediaControllerCompat,
        prevMediaId: String?,
        currMediaId: String?,
        prevDuration: Long,
        lastKnownPosition: Long
    ): CustomRepeatState?
}
