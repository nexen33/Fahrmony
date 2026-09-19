package com.fahrmony.app.nativebridge.lyrics.sync

/**
 * 歌词毫秒级同步驱动监听器
 */
interface LyricSyncListener {

    /**
     * 演唱行跃迁通知 (换行差量触发，句内持续静默)
     *
     * @param currentLine 当前正在演唱的歌词行正文
     * @param nextLine 下一句待演唱歌词正文 (若已为最后一句则为 null)
     * @param entryIndex 当前歌词所在列表索引 (0-based)
     * @param totalEntries 当前歌曲有效歌词行总数
     */
    fun onLyricLineChanged(
        currentLine: String,
        nextLine: String?,
        entryIndex: Int,
        totalEntries: Int
    )

    /**
     * 歌词同步复原通知 (在切歌、暂停、纯音乐触发或歌词关闭时触发，指示上层恢复原始歌曲/艺术家展示)
     */
    fun onLyricsStateReset()
}
