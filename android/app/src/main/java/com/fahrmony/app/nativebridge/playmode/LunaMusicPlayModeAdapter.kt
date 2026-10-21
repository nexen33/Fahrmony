package com.fahrmony.app.nativebridge.playmode

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.fahrmony.app.nativebridge.CustomRepeatState

/**
 * 汽水音乐播放模式适配器 (开源基础空桩接口 / Stub Contract)
 */
object LunaMusicPlayModeAdapter {

    const val PACKAGE_NAME = "com.luna.music"

    fun isSupported(packageName: String?): Boolean {
        return packageName != null && packageName.equals(PACKAGE_NAME, ignoreCase = true)
    }

    fun isControlSupported(controller: MediaControllerCompat?): Boolean = false

    fun createCustomAction(): PlaybackStateCompat.CustomAction {
        return PlaybackStateCompat.CustomAction.Builder(
            "custom.action.PLAY_MODE",
            "播放模式",
            com.fahrmony.app.R.drawable.ic_car_repeat
        ).build()
    }

    fun resolvePlayMode(metadata: MediaMetadataCompat?): CustomRepeatState? = null

    fun nextMode(current: CustomRepeatState): CustomRepeatState = CustomRepeatState.REPEAT_ALL

    fun dispatchToggle(controller: MediaControllerCompat, currentMode: CustomRepeatState): Boolean = false
}
