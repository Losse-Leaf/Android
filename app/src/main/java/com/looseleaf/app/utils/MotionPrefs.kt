package com.looseleaf.app.utils

import android.content.Context
import android.provider.Settings

/**
 * 系统动效偏好 (MOB-P2-49)
 *
 * 「开发者选项 → 动画时长 = 0」或系统「移除动画」辅助功能开启时，
 * 应用里的无限动画（扫码激光）、过渡动画应当停掉 ——
 * 前庭功能障碍用户会因此不适，无限动画也会持续耗电。
 */
object MotionPrefs {

    /** 系统动画总时长倍率（0 表示关闭动画） */
    fun animationScale(context: Context): Float {
        return runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }

    /** 是否允许应用播放动画 */
    fun animationsEnabled(context: Context): Boolean = animationScale(context) > 0f
}
