package com.fahrmony.app.nativebridge.custom

import android.support.v4.media.session.PlaybackStateCompat

/**
 * 非预置自定义音源播放态高频心跳节流守卫。
 * 仅拦截连续 STATE_PLAYING 下的纯进度刷新，所有预置音源、状态切换、Seek 与保护窗均直接放行。
 */
object CustomPlaybackThrottle {

    private const val SEEK_DRIFT_THRESHOLD_MS = 2500L

    private var lastPackage: String? = null
    private var lastPushWallMs: Long = 0L
    private var lastPushPositionMs: Long = 0L
    private var lastActions: Long = -1L

    fun reset() {
        lastPackage = null
        lastPushWallMs = 0L
        lastPushPositionMs = 0L
        lastActions = -1L
    }

    fun shouldThrottleHeartbeat(
        packageName: String,
        isKnownPreset: Boolean,
        wasPlaying: Boolean,
        state: PlaybackStateCompat?,
        isActiveController: Boolean,
        hasActiveProtection: Boolean,
        nowMs: Long
    ): Boolean {
        if (isKnownPreset) return false

        val newState = state?.state ?: PlaybackStateCompat.STATE_NONE
        val pos = state?.position ?: 0L
        val actions = state?.actions ?: 0L

        if (!wasPlaying ||
            newState != PlaybackStateCompat.STATE_PLAYING ||
            !isActiveController ||
            hasActiveProtection ||
            packageName != lastPackage ||
            actions != lastActions
        ) {
            recordAnchor(packageName, nowMs, pos, actions)
            return false
        }

        val elapsedWall = nowMs - lastPushWallMs
        val deltaPos = pos - lastPushPositionMs
        val isSeekJump = (deltaPos < 0L) || (kotlin.math.abs(deltaPos - elapsedWall) > SEEK_DRIFT_THRESHOLD_MS)

        if (isSeekJump) {
            recordAnchor(packageName, nowMs, pos, actions)
            return false
        }

        return true
    }

    private fun recordAnchor(packageName: String, nowMs: Long, pos: Long, actions: Long) {
        lastPackage = packageName
        lastPushWallMs = nowMs
        lastPushPositionMs = pos
        lastActions = actions
    }
}

