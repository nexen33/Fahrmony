package com.fahrmony.app.nativebridge.lyrics.provider

import android.support.v4.media.MediaMetadataCompat
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult

/**
 * 酷狗音乐歌词提供者 (开源基础空桩接口 / Stub Contract)
 *
 * 开放源码版本为保障长期构建自包含性与接口稳定性，默认采用标准空桩实现。
 * 开发者可根据业务需求自行基于 LyricsProvider 契约扩展实现。
 */
object KugouLyricsProvider : LyricsProvider {

    private val KUGOU_ALIASES = setOf(
        "com.kugou.android",
        "kugou.service",
        "com.kugou.android.lite"
    )

    override fun isSupported(packageName: String): Boolean {
        return packageName.lowercase() in KUGOU_ALIASES
    }

    override fun getLyrics(packageName: String, metadata: MediaMetadataCompat?): LyricResult {
        // 开源标准空桩实现：直接回退至 NotFound，触发后续 Provider 兜底
        return LyricResult.NotFound
    }

    fun searchByTitleAndArtist(title: String, artist: String, durationMs: Long): LyricResult {
        return LyricResult.NotFound
    }
}
