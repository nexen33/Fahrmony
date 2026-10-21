package com.fahrmony.app.nativebridge.lyrics.sanitizer

import android.support.v4.media.MediaMetadataCompat

/**
 * 车载蓝牙歌词注入净化器 (开源基础空桩接口 / Stub Contract)
 */
object LunaMetadataSanitizer {

    const val PACKAGE_NAME = "com.luna.music"

    data class SanitizedTrackInfo(
        val title: String,
        val artist: String,
        val trackKey: String
    )

    fun isSupported(packageName: String): Boolean {
        return packageName.equals(PACKAGE_NAME, ignoreCase = true)
    }

    fun resolve(
        packageName: String,
        metadata: MediaMetadataCompat?,
        rawTitle: String,
        rawArtist: String,
        currentLyricLine: String? = null
    ): SanitizedTrackInfo {
        val key = "$packageName|$rawTitle|$rawArtist"
        return SanitizedTrackInfo(rawTitle, rawArtist, key)
    }

    fun getStableTrackKey(
        packageName: String,
        metadata: MediaMetadataCompat?,
        fallbackTitle: String,
        fallbackArtist: String
    ): String {
        return "$packageName|$fallbackTitle|$fallbackArtist"
    }

    fun reset() {}
}
