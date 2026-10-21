package com.fahrmony.app.nativebridge.playmode

import android.support.v4.media.session.MediaControllerCompat
import com.fahrmony.app.nativebridge.CustomRepeatState

/**
 * 针对网易云音乐 (com.netease.cloudmusic) 的启发式自愈探测器
 * 核心机制：曲尾闭环观测锁 (Armed Lock)
 * 1. 必须先在播放中确凿观测到前一首曲目进度抵达曲尾 (lastKnownPosition >= prevDuration - 3000L 且总时长 > 10000L)；
 * 2. 并且切歌或重播后的新曲目 currMediaId == armedTrackMediaId；
 * 3. 彻底杜绝同一首歌起播初期的元数据二次刷新引起的单曲循环虚假误判。
 */
class NetEaseHeuristicDetector : IPlayModeHeuristicDetector {

    companion object {
        private const val NETEASE_PACKAGE = "com.netease.cloudmusic"
    }

    // 曲尾就绪锁标记与对应的曲目指纹
    private var isArmedAtTrackEnd = false
    private var armedTrackMediaId: String? = null

    override fun isSupported(packageName: String): Boolean {
        return packageName == NETEASE_PACKAGE
    }

    override fun onPlaybackPositionUpdated(
        controller: MediaControllerCompat,
        mediaId: String?,
        duration: Long,
        position: Long
    ) {
        if (!isSupported(controller.packageName)) return
        // 阶段一：在播放过程中，确凿观测到曲目播放至接近曲尾 (距结尾 <= 3000ms 且总时长有效 > 10000ms)
        if (duration > 10000L && position >= duration - 3000L) {
            isArmedAtTrackEnd = true
            armedTrackMediaId = mediaId
        }
    }

    override fun onTrackChanged(
        controller: MediaControllerCompat,
        prevMediaId: String?,
        currMediaId: String?,
        prevDuration: Long,
        lastKnownPosition: Long
    ): CustomRepeatState? {
        if (!isSupported(controller.packageName)) return null
        if (currMediaId.isNullOrBlank()) return null

        // 阶段二：仅当此前在曲尾确凿观测到武装上膛，且重播曲目与曲尾曲目严格一致时，方可 100% 确认自然发生单曲循环重播
        if (isArmedAtTrackEnd && armedTrackMediaId == currMediaId) {
            isArmedAtTrackEnd = false
            armedTrackMediaId = null
            return CustomRepeatState.REPEAT_ONE
        }

        // 若切到其他不同歌曲，重置曲尾标记
        if (armedTrackMediaId != null && armedTrackMediaId != currMediaId) {
            isArmedAtTrackEnd = false
            armedTrackMediaId = null
        }

        return null
    }
}
