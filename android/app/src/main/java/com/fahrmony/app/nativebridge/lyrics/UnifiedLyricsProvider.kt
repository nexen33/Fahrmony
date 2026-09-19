package com.fahrmony.app.nativebridge.lyrics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import com.fahrmony.app.nativebridge.lyrics.cache.LyricCache
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult
import com.fahrmony.app.nativebridge.lyrics.provider.KugouLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.provider.LrclibLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.provider.NeteaseLyricsProvider
import com.fahrmony.app.nativebridge.lyrics.provider.QQLyricsProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 统一歌词可插拔查询总控门面 (UnifiedLyricsProvider)
 *
 * 调度管道：
 * 1. L1 内存 / L2 磁盘双级缓存检测 (0ms 秒开)；
 * 2. QQ 音乐本地私有字段提取 (0ms 零网络)；
 * 3. 网易云音乐 MEDIA_ID 精准直出；
 * 4. 酷狗音乐官方公开接口专属提取；
 * 5. 酷我/汽水/波点/自定义音源通用双通道兜底 (云端检索 -> 通用开放歌词源)；
 * 6. 异步并发安全与在途请求去重 (In-Flight Mutex).
 */
object UnifiedLyricsProvider {

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    // 在途请求去重锁，避免同首歌曲高频触发重复网络请求
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()

    /**
     * 异步获取当前媒体会话歌词
     *
     * @param context Android 上下文
     * @param packageName 当前音频应用包名
     * @param metadata 媒体元数据
     * @param allowNetwork 是否允许使用移动/无线网络检索
     * @param callback 结果回调 (保证在主线程派发)
     */
    fun getLyrics(
        context: Context,
        packageName: String,
        metadata: MediaMetadataCompat?,
        allowNetwork: Boolean,
        callback: (LyricResult) -> Unit
    ) {
        if (metadata == null) {
            callback(LyricResult.NotFound)
            return
        }

        val title = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            ?: metadata.description?.title?.toString() ?: ""
        val artist = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            ?: metadata.description?.subtitle?.toString() ?: ""
        val durationMs = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)

        if (title.isBlank()) {
            callback(LyricResult.NotFound)
            return
        }

        val cacheKey = LyricCache.buildKey(packageName, title, artist, durationMs)

        // 1. 检查 L1 内存与 L2 磁盘缓存
        val cached = LyricCache.get(context, cacheKey)
        if (cached != null) {
            callback(cached)
            return
        }

        // 2. QQ 音乐本地私有字段零网络提取
        if (QQLyricsProvider.isSupported(packageName)) {
            val qqResult = QQLyricsProvider.getLyrics(packageName, metadata)
            if (qqResult is LyricResult.Success) {
                LyricCache.put(context, cacheKey, qqResult)
                callback(qqResult)
                return
            }
        }

        // 3. 若不允许联网，直接静默回退 NotFound
        if (!allowNetwork) {
            callback(LyricResult.NotFound)
            return
        }

        // 4. 在途请求防重检查
        if (!inFlightKeys.add(cacheKey)) {
            // 已有相同歌曲正在后台拉取中，无需重复发起
            return
        }

        // 5. 异步执行网络检索管道
        executor.execute {
            try {
                var result: LyricResult = LyricResult.NotFound

                // A. 网易云专有提取器 (MEDIA_ID 官方直连)
                if (NeteaseLyricsProvider.isSupported(packageName)) {
                    result = NeteaseLyricsProvider.getLyrics(packageName, metadata)
                }

                // B. 酷狗专有提取器 (lyrics.kugou.com 专属)
                if (result !is LyricResult.Success && KugouLyricsProvider.isSupported(packageName)) {
                    result = KugouLyricsProvider.getLyrics(packageName, metadata)
                }

                // C. 通用音源双通道检索 (云端检索 -> 通用开放歌词源)
                if (result !is LyricResult.Success) {
                    result = KugouLyricsProvider.searchByTitleAndArtist(title, artist, durationMs)
                    if (result !is LyricResult.Success) {
                        result = LrclibLyricsProvider.fetchLyrics(
                            title = title,
                            artist = artist,
                            album = metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "",
                            durationMs = durationMs
                        )
                    }
                }

                // 回写双级缓存
                if (result is LyricResult.Success) {
                    LyricCache.put(context, cacheKey, result)
                }

                // 回调主线程
                mainHandler.post {
                    callback(result)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    callback(LyricResult.Error("歌词检索异常: ${e.message}"))
                }
            } finally {
                inFlightKeys.remove(cacheKey)
            }
        }
    }
}
