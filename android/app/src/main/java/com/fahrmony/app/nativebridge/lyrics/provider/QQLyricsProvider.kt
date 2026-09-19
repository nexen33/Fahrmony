package com.fahrmony.app.nativebridge.lyrics.provider

import android.support.v4.media.MediaMetadataCompat
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult
import com.fahrmony.app.nativebridge.lyrics.parser.LrcParser

/**
 * QQ 音乐车载私有元数据本地歌词提供者 (零网络、零流量消耗)
 *
 * 依据实机探针 (LYRICS_PROBE) 验证：
 * QQ 音乐 (com.tencent.qqmusic) 在播放时会在 MediaMetadataCompat 中直接注入
 * 私有字段 `ucar.media.metadata.LYRICS_WHOLE`，内含完整的标准 LRC 格式文本。
 */
object QQLyricsProvider : LyricsProvider {

    const val PACKAGE_NAME = "com.tencent.qqmusic"
    const val KEY_QQ_LYRICS_WHOLE = "ucar.media.metadata.LYRICS_WHOLE"

    override fun isSupported(packageName: String): Boolean {
        return packageName.equals(PACKAGE_NAME, ignoreCase = true)
    }

    override fun getLyrics(packageName: String, metadata: MediaMetadataCompat?): LyricResult {
        if (!isSupported(packageName) || metadata == null) {
            return LyricResult.NotFound
        }

        try {
            // 优先通过标准 MediaMetadataCompat 接口读取
            var rawLrc = metadata.getString(KEY_QQ_LYRICS_WHOLE)

            // 若标准方法未命中，回退读取底层 Bundle
            if (rawLrc.isNullOrBlank()) {
                val bundle = metadata.bundle
                rawLrc = bundle.getString(KEY_QQ_LYRICS_WHOLE)
                    ?: bundle.getCharSequence(KEY_QQ_LYRICS_WHOLE)?.toString()
            }

            if (rawLrc.isNullOrBlank()) {
                return LyricResult.NotFound
            }

            return LrcParser.parse(rawLrc, convertToSimplified = false)
        } catch (e: Exception) {
            return LyricResult.Error("QQ音乐歌词提取异常: ${e.message}")
        }
    }
}
