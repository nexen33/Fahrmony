package com.fahrmony.app.nativebridge.lyrics.provider

import android.support.v4.media.MediaMetadataCompat
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult

/**
 * 汽水音乐歌词提供者 (开源基础空桩接口 / Stub Contract)
 *
 * 开放源码版本为保障长期构建自包含性与合规安全，默认采用标准空桩实现。
 * 开发者可根据业务需求自行基于 LyricsProvider 契约扩展实现。
 */
object LunaLyricsProvider : LyricsProvider {

    const val LUNA_PACKAGE = "com.luna.music"

    override fun isSupported(packageName: String): Boolean {
        return packageName.equals(LUNA_PACKAGE, ignoreCase = true)
    }

    override fun getLyrics(packageName: String, metadata: MediaMetadataCompat?): LyricResult {
        return LyricResult.NotFound
    }

    fun onVivoExtrasReceived(mediaId: String?, rawLrc: String?) {
        // 开源标准空桩
    }

    fun fetchLyricsByMediaId(mediaId: String): LyricResult {
        return LyricResult.NotFound
    }
}
