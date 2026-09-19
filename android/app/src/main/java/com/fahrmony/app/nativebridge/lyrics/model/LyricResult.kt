package com.fahrmony.app.nativebridge.lyrics.model

/**
 * 歌词获取与解析状态密封类
 */
sealed class LyricResult {
    /**
     * 成功获取并解析歌词
     *
     * @param entries 按时间戳排序的歌词行列表
     * @param rawLrc 原始 LRC 文本
     * @param isPureMusic 是否判定为纯音乐/无歌词曲目
     */
    data class Success(
        val entries: List<LyricEntry>,
        val rawLrc: String,
        val isPureMusic: Boolean = false
    ) : LyricResult()

    /**
     * 未检索到歌词 (触发静默回退)
     */
    object NotFound : LyricResult()

    /**
     * 解析或网络异常
     */
    data class Error(val message: String) : LyricResult()
}
