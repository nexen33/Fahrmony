package com.fahrmony.app.nativebridge.lyrics.parser

import com.fahrmony.app.nativebridge.lyrics.LyricsCleanupManager
import com.fahrmony.app.nativebridge.lyrics.model.LyricEntry
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult
import java.util.regex.Pattern

/**
 * 高性能标准 LRC 时间戳解析引擎
 *
 * 特性：
 * 1. 毫秒级时间戳精准换算 ([mm:ss.xx] / [mm:ss.xxx] / [mm:ss]);
 * 2. 多时间戳行自动展开 ([00:01.00][00:05.00]歌词);
 * 3. 支持 [offset:+/-毫秒] 全局时间轴预校准;
 * 4. 自动协同 LyricsCleanupManager 进行噪声过滤与纯音乐智能判定;
 * 5. 严格异常容错，保障零崩溃与稳定升序输出.
 */
object LrcParser {

    // 时间戳正则: [01:23.45] 或 [01:23.456] 或 [01:23]
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    
    // offset 标签正则: [offset:+/-1000]
    private val OFFSET_PATTERN = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)

    fun parse(rawLrc: String?, convertToSimplified: Boolean = true): LyricResult {
        if (rawLrc.isNullOrBlank()) {
            return LyricResult.NotFound
        }

        val entries = ArrayList<LyricEntry>()
        var globalOffsetMs = 0L
        var hasPureMusicFlag = false

        // 1. 第一遍快速扫描：提取全局 [offset:xxx] 标签 (使用 lineSequence 避免中间数组分配)
        rawLrc.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                val offsetMatcher = OFFSET_PATTERN.matcher(trimmed)
                if (offsetMatcher.find()) {
                    try {
                        globalOffsetMs = offsetMatcher.group(1)?.toLong() ?: 0L
                    } catch (ignored: Exception) {}
                }
            }
        }

        // 2. 第二遍扫描：逐行提取时间戳与正文
        rawLrc.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !LyricsCleanupManager.isHeaderTag(trimmed)) {
                val matcher = TIME_TAG_PATTERN.matcher(trimmed)
                val lineTimestamps = ArrayList<Long>(2)
                var lastMatchEnd = 0

                while (matcher.find()) {
                    val minStr = matcher.group(1) ?: "0"
                    val secStr = matcher.group(2) ?: "0"
                    val msStr = matcher.group(3)

                    val minutes = minStr.toLongOrNull() ?: 0L
                    val seconds = secStr.toLongOrNull() ?: 0L
                    val milliseconds = when {
                        msStr == null -> 0L
                        msStr.length == 1 -> (msStr.toLongOrNull() ?: 0L) * 100
                        msStr.length == 2 -> (msStr.toLongOrNull() ?: 0L) * 10
                        else -> (msStr.take(3).toLongOrNull() ?: 0L)
                    }

                    val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds + globalOffsetMs
                    lineTimestamps.add(maxOf(0L, totalMs))
                    lastMatchEnd = matcher.end()
                }

                if (lineTimestamps.isNotEmpty()) {
                    // 提取时间戳后的歌词文本内容
                    val rawText = if (lastMatchEnd < trimmed.length) {
                        trimmed.substring(lastMatchEnd)
                    } else {
                        ""
                    }
                    val cleanedText = LyricsCleanupManager.cleanText(rawText, convertToSimplified)

                    // 检查是否命中纯音乐标记
                    if (LyricsCleanupManager.isPureMusicDeclaration(cleanedText)) {
                        hasPureMusicFlag = true
                    }

                    // 过滤作词/作曲等噪声行与纯空白行
                    if (!LyricsCleanupManager.isNoiseLine(cleanedText) && cleanedText.isNotBlank()) {
                        for (ts in lineTimestamps) {
                            entries.add(LyricEntry(timeMs = ts, text = cleanedText))
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            return if (hasPureMusicFlag) {
                LyricResult.Success(emptyList(), rawLrc, isPureMusic = true)
            } else {
                LyricResult.NotFound
            }
        }

        // 3. 结果按时间戳升序排序
        entries.sort()

        // 4. 纯音乐判定：若包含纯音乐声明，或经过清洗后仅剩 <= 1 行非有效歌词
        val isPureMusic = hasPureMusicFlag || entries.size <= 1

        return LyricResult.Success(
            entries = entries,
            rawLrc = rawLrc,
            isPureMusic = isPureMusic
        )
    }
}
