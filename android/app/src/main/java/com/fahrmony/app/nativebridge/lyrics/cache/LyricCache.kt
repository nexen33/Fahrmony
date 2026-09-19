package com.fahrmony.app.nativebridge.lyrics.cache

import android.content.Context
import android.util.LruCache
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult
import com.fahrmony.app.nativebridge.lyrics.parser.LrcParser
import java.io.File
import java.security.MessageDigest

/**
 * 歌词双级缓存管理器 (L1 内存 LruCache + L2 磁盘沙盒持久化)
 */
object LyricCache {

    private const val L1_MAX_ENTRIES = 50
    private const val DIR_LYRICS = "fahrmony_lyrics_cache"

    // L1: 内存快速缓存 (定长 50 首，线程安全)
    private val l1Cache = LruCache<String, LyricResult.Success>(L1_MAX_ENTRIES)
    private val notFoundCache = LruCache<String, Boolean>(L1_MAX_ENTRIES)

    /**
     * 生成歌曲维度的唯一缓存 Key (基于 MD5 摘要，融入秒级歌曲时长以绝对绝缘旧错误缓存与同名异版)
     */
    fun buildKey(packageName: String, title: String, artist: String, durationMs: Long = 0L): String {
        val durationSec = if (durationMs > 0L) durationMs / 1000L else 0L
        val raw = "${packageName.trim().lowercase()}_${title.trim().lowercase()}_${artist.trim().lowercase()}_$durationSec"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            raw.replace(Regex("[^a-zA-Z0-9_]"), "_")
        }
    }

    fun isNotFound(key: String): Boolean {
        if (key.isBlank()) return false
        synchronized(notFoundCache) {
            return notFoundCache.get(key) == true
        }
    }

    fun putNotFound(key: String) {
        if (key.isBlank()) return
        synchronized(notFoundCache) {
            notFoundCache.put(key, true)
        }
    }

    /**
     * 读取缓存 (优先 L1 内存，未命中回退 L2 磁盘并自动回填 L1)
     */
    fun get(context: Context, key: String): LyricResult.Success? {
        if (key.isBlank()) return null

        // 1. 尝试从 L1 内存读取
        synchronized(l1Cache) {
            val memoryHit = l1Cache.get(key)
            if (memoryHit != null) {
                return memoryHit
            }
        }

        // 2. 尝试从 L2 磁盘沙盒读取
        try {
            val cacheDir = File(context.cacheDir, DIR_LYRICS)
            val file = File(cacheDir, "$key.lrc")
            if (file.exists() && file.isFile && file.length() > 0) {
                val rawLrc = file.readText(Charsets.UTF_8)
                val parseResult = LrcParser.parse(rawLrc)
                if (parseResult is LyricResult.Success) {
                    synchronized(l1Cache) {
                        l1Cache.put(key, parseResult)
                    }
                    return parseResult
                }
            }
        } catch (ignored: Exception) {}

        return null
    }

    /**
     * 写入双级缓存
     */
    fun put(context: Context, key: String, result: LyricResult.Success) {
        if (key.isBlank() || result.rawLrc.isBlank() || result.isPureMusic) return

        // 1. 写入 L1 内存
        synchronized(l1Cache) {
            l1Cache.put(key, result)
        }

        // 2. 异步持久化至 L2 磁盘
        try {
            val cacheDir = File(context.cacheDir, DIR_LYRICS)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val tempFile = File(cacheDir, "$key.tmp")
            val targetFile = File(cacheDir, "$key.lrc")
            tempFile.writeText(result.rawLrc, Charsets.UTF_8)
            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            }
        } catch (ignored: Exception) {}
    }

    /**
     * 清空缓存
     */
    fun clear(context: Context) {
        synchronized(l1Cache) {
            l1Cache.evictAll()
        }
        synchronized(notFoundCache) {
            notFoundCache.evictAll()
        }
        try {
            val cacheDir = File(context.cacheDir, DIR_LYRICS)
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (ignored: Exception) {}
    }
}
