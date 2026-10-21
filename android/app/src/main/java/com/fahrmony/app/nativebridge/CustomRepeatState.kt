package com.fahrmony.app.nativebridge

/**
 * 车载播放模式枚举状态定义
 * 支持标准列表循环、随机播放、单曲循环以及网易云等专属的心动模式 (Heartbeat)。
 */
enum class CustomRepeatState(val title: String, val iconRes: Int) {
    REPEAT_ALL("列表循环", com.fahrmony.app.R.drawable.ic_car_repeat),
    SHUFFLE("随机播放", com.fahrmony.app.R.drawable.ic_car_shuffle),
    REPEAT_ONE("单曲循环", com.fahrmony.app.R.drawable.ic_car_repeat_one),
    HEARTBEAT("心动模式", com.fahrmony.app.R.drawable.ic_car_heartbeat);

    fun next(isNetEase: Boolean = false): CustomRepeatState {
        return when (this) {
            REPEAT_ALL -> SHUFFLE
            SHUFFLE -> REPEAT_ONE
            REPEAT_ONE -> if (isNetEase) HEARTBEAT else REPEAT_ALL
            HEARTBEAT -> REPEAT_ALL
        }
    }
}
