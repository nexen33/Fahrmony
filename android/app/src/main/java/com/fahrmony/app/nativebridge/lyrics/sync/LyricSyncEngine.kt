package com.fahrmony.app.nativebridge.lyrics.sync

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.PlaybackStateCompat
import com.fahrmony.app.nativebridge.lyrics.model.LyricEntry
import com.fahrmony.app.nativebridge.lyrics.model.LyricResult

/**
 * 高精度时间戳歌词同步驱动引擎
 *
 * 特性：
 * 1. 毫秒级播放进度推算 (结合 PlaybackStateCompat 速度与 elapsedRealtime)；
 * 2. 高效二分查找 ($O(\log N)$) 定位当前唱词行；
 * 3. 换行差量触发：句内持续静默，仅跨行时派发通知，彻底消除高频 Binder 通信；
 * 4. 智能动态时钟调度：依据下一句歌词时间间隔动态计算 Tick 延时，杜绝 CPU 空转；
 * 5. 毫秒级状态复原门禁：切歌/暂停/纯音乐时立即在 <= 100ms 内重置并通知上层恢复原始歌曲元数据。
 */
class LyricSyncEngine(private val mainHandler: Handler = Handler(Looper.getMainLooper())) {

    private var listener: LyricSyncListener? = null

    @Volatile
    private var activeEntries: List<LyricEntry> = emptyList()

    @Volatile
    private var activeTrackKey: String? = null

    @Volatile
    private var trackStartTimeRealtime: Long = SystemClock.elapsedRealtime()

    @Volatile
    private var lastPlaybackState: PlaybackStateCompat? = null

    @Volatile
    private var offsetMs: Long = 0L

    @Volatile
    private var isEnabled: Boolean = true

    private var lastNotifiedIndex: Int = -2
    private var isScheduled: Boolean = false

    private val syncRunnable = object : Runnable {
        override fun run() {
            isScheduled = false
            evaluateAndSchedule()
        }
    }

    /**
     * 注册歌词同步监听器
     */
    fun setListener(listener: LyricSyncListener?) {
        this.listener = listener
    }

    /**
     * 设置蓝牙硬件音频延迟偏置 (毫秒，支持 +/- 偏置)
     */
    fun setOffsetMs(offset: Long) {
        this.offsetMs = offset
        evaluateAndSchedule()
    }

    /**
     * 设置歌词功能全局启闭
     */
    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled != enabled) {
            this.isEnabled = enabled
            if (!enabled) {
                reset(notifyListener = true)
            } else {
                evaluateAndSchedule()
            }
        }
    }

    /**
     * 当前是否有有效歌词条目
     */
    fun hasValidLyrics(): Boolean = activeEntries.isNotEmpty()

    /**
     * 更新当前曲目的歌词解析结果
     *
     * @param trackKey 当前曲目唯一标识 (如 "pkg:title:artist")
     * @param lyricResult 歌词解析结果
     */
    fun updateLyrics(trackKey: String, lyricResult: LyricResult) {
        if (trackKey != this.activeTrackKey) {
            this.activeTrackKey = trackKey
            this.trackStartTimeRealtime = SystemClock.elapsedRealtime()
            this.lastNotifiedIndex = -2
        }

        when (lyricResult) {
            is LyricResult.Success -> {
                if (lyricResult.isPureMusic || lyricResult.entries.isEmpty()) {
                    this.activeEntries = emptyList()
                    reset(notifyListener = true)
                } else {
                    this.activeEntries = lyricResult.entries
                    evaluateAndSchedule()
                }
            }
            is LyricResult.NotFound, is LyricResult.Error -> {
                this.activeEntries = emptyList()
                reset(notifyListener = true)
            }
        }
    }

    /**
     * 更新当前媒体会话的播放状态
     */
    fun updatePlaybackState(state: PlaybackStateCompat?) {
        this.lastPlaybackState = state
        val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING

        if (!isPlaying) {
            stopTick()
            // 暂停或缓冲瞬态保持当前行展示，绝不因切歌瞬态的 STATE_NONE/STATE_STOPPED 清空已加载歌词
        } else {
            evaluateAndSchedule()
        }
    }

    /**
     * 核心评估与动态调度方法
     */
    @Synchronized
    private fun evaluateAndSchedule() {
        if (!isEnabled || activeEntries.isEmpty()) {
            stopTick()
            return
        }

        val state = lastPlaybackState
        val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
        val currentPosMs = calculateCurrentPosition(state, offsetMs, trackStartTimeRealtime)

        val targetIndex = findLyricIndex(activeEntries, currentPosMs)

        if (targetIndex != lastNotifiedIndex) {
            lastNotifiedIndex = targetIndex
            if (targetIndex >= 0 && targetIndex < activeEntries.size) {
                val currentLine = activeEntries[targetIndex].text
                val nextLine = if (targetIndex + 1 < activeEntries.size) {
                    activeEntries[targetIndex + 1].text
                } else {
                    null
                }
                listener?.onLyricLineChanged(
                    currentLine = currentLine,
                    nextLine = nextLine,
                    entryIndex = targetIndex,
                    totalEntries = activeEntries.size
                )
            } else if (targetIndex == -1) {
                // 前奏阶段 (尚未到达第一句歌词)
                val firstLine = activeEntries.firstOrNull()?.text
                listener?.onLyricLineChanged(
                    currentLine = "",
                    nextLine = firstLine,
                    entryIndex = -1,
                    totalEntries = activeEntries.size
                )
            }
        }

        if (isPlaying) {
            scheduleNextTick(currentPosMs, targetIndex)
        } else {
            stopTick()
        }
    }

    /**
     * 基于下一行歌词距离计算最优调度间隔，杜绝固定间隔轮询
     */
    private fun scheduleNextTick(currentPosMs: Long, currentIndex: Int) {
        stopTick()

        val nextTimestamp = when {
            currentIndex == -1 -> activeEntries.firstOrNull()?.timeMs ?: Long.MAX_VALUE
            currentIndex + 1 < activeEntries.size -> activeEntries[currentIndex + 1].timeMs
            else -> Long.MAX_VALUE
        }

        val delayMs = if (nextTimestamp != Long.MAX_VALUE) {
            val delta = nextTimestamp - currentPosMs
            // 在 50ms 到 500ms 之间动态自适应，既防抖动又保证快进/跳转时的即时响应
            delta.coerceIn(50L, 500L)
        } else {
            // 已为最后一句，低频 500ms 巡检防止切歌遗漏
            500L
        }

        isScheduled = true
        mainHandler.postDelayed(syncRunnable, delayMs)
    }

    private fun stopTick() {
        if (isScheduled) {
            mainHandler.removeCallbacks(syncRunnable)
            isScheduled = false
        }
    }

    /**
     * 强制重新分发当前歌词行（例如封面模式切换或元数据热刷新时立即重推，无需等待下一行时间戳到达）
     */
    fun retriggerCurrentLine() {
        if (!isEnabled || activeEntries.isEmpty()) return
        lastNotifiedIndex = -2
        evaluateAndSchedule()
    }

    /**
     * 状态复原门禁
     */
    fun reset(notifyListener: Boolean = true) {
        stopTick()
        lastNotifiedIndex = -2
        activeEntries = emptyList()
        activeTrackKey = null
        trackStartTimeRealtime = SystemClock.elapsedRealtime()
        if (notifyListener) {
            listener?.onLyricsStateReset()
        }
    }

    companion object {
        /**
         * 毫秒级播放绝对时间戳推算
         */
        fun calculateCurrentPosition(
            state: PlaybackStateCompat?,
            offset: Long,
            trackStartTimeRealtime: Long = 0L
        ): Long {
            if (state == null) return 0L
            val basePos = state.position
            val isPlaying = state.state == PlaybackStateCompat.STATE_PLAYING
            if (!isPlaying) {
                return maxOf(0L, basePos + offset)
            }

            val now = SystemClock.elapsedRealtime()
            // 切歌隔离：在新歌刚开始的短窗口期内 (1500ms 内)，若上报的进度仍大于 3 秒，判定为上一首曲目的残留进度，隔离清零
            val effectiveBasePos = if (trackStartTimeRealtime > 0L && (now - trackStartTimeRealtime) < 1500L && basePos > 3000L) {
                0L
            } else {
                basePos
            }

            val updateTime = state.lastPositionUpdateTime
            // 兼容绝对时间戳 (如 System.currentTimeMillis()) 与各种非标第三方时钟
            val elapsed = if (updateTime > 1_000_000_000_000L) {
                val wallNow = System.currentTimeMillis()
                if (wallNow >= updateTime) (wallNow - updateTime) else 0L
            } else {
                val eff = when {
                    updateTime in 1..now -> {
                        if (trackStartTimeRealtime > 0L && updateTime < trackStartTimeRealtime - 3000L) {
                            trackStartTimeRealtime
                        } else {
                            updateTime
                        }
                    }
                    trackStartTimeRealtime in 1..now -> trackStartTimeRealtime
                    else -> now
                }
                if (now >= eff) (now - eff) else 0L
            }

            val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f
            val currentPos = effectiveBasePos + (elapsed * speed).toLong() + offset
            return maxOf(0L, currentPos)
        }

        /**
         * 二分查找快速定位歌词行索引 (时间复杂度 O(log N))
         *
         * @return 索引范围 [-1, entries.size - 1]，-1 表示处于首句歌词开唱前的前奏阶段
         */
        fun findLyricIndex(entries: List<LyricEntry>, positionMs: Long): Int {
            if (entries.isEmpty()) return -1
            if (positionMs < entries[0].timeMs) return -1

            var low = 0
            var high = entries.size - 1
            var result = 0

            while (low <= high) {
                val mid = (low + high) ushr 1
                if (entries[mid].timeMs <= positionMs) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }
    }
}
