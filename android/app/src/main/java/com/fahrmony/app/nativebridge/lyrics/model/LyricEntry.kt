package com.fahrmony.app.nativebridge.lyrics.model

/**
 * 单行同步歌词数据模型
 *
 * @param timeMs 歌词起始绝对时间戳 (毫秒)
 * @param text 歌词原文正文
 * @param translation 歌词翻译 (如有)
 */
data class LyricEntry(
    val timeMs: Long,
    val text: String,
    val translation: String? = null
) : Comparable<LyricEntry> {
    override fun compareTo(other: LyricEntry): Int {
        return this.timeMs.compareTo(other.timeMs)
    }
}
