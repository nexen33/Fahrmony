package com.fahrmony.app.nativebridge.playmode

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.fahrmony.app.nativebridge.CustomRepeatState

/**
 * 酷狗音乐播放模式适配器 (开源基础空桩接口 / Stub Contract)
 */
object KugouMusicPlayModeAdapter {

    const val PACKAGE_MAIN = "com.kugou.android"
    const val PACKAGE_LITE = "com.kugou.android.lite"
    const val PACKAGE_SERVICE = "kugou.service"

    fun isSupported(packageName: String?): Boolean {
        if (packageName == null) return false
        return packageName.equals(PACKAGE_MAIN, ignoreCase = true) ||
               packageName.equals(PACKAGE_LITE, ignoreCase = true) ||
               packageName.equals(PACKAGE_SERVICE, ignoreCase = true)
    }

    fun isLite(packageName: String?): Boolean {
        return packageName != null && packageName.equals(PACKAGE_LITE, ignoreCase = true)
    }

    fun isMain(packageName: String?): Boolean {
        return packageName != null && (packageName.equals(PACKAGE_MAIN, ignoreCase = true) || packageName.equals(PACKAGE_SERVICE, ignoreCase = true))
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

    fun nextMode(current: CustomRepeatState): Pair<CustomRepeatState, Int> = CustomRepeatState.REPEAT_ALL to 1

    fun dispatchToggle(controller: MediaControllerCompat, currentMode: CustomRepeatState): Boolean = false
}
