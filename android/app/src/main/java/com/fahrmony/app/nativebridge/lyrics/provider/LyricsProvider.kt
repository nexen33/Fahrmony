package com.fahrmony.app.nativebridge.lyrics.provider

import android.support.v4.media.MediaMetadataCompat
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult

/**
 * 歌词提取与检索提供者统一接口契约
 */
interface LyricsProvider {

    /**
     * 判定当前提供者是否支持指定的目标应用
     *
     * @param packageName 目标音频应用包名
     */
    fun isSupported(packageName: String): Boolean

    /**
     * 从目标应用的会话元数据或上下文同步提取歌词
     *
     * @param packageName 目标音频应用包名
     * @param metadata 当前媒体会话的元数据对象
     * @return 歌词解析结果 (Success / NotFound / Error)
     */
    fun getLyrics(packageName: String, metadata: MediaMetadataCompat?): LyricResult
}
