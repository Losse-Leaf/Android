package com.looseleaf.app.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.looseleaf.app.data.security.CredentialStore

/**
 * 细腻物理触感反馈引擎 (Physical Haptics Engine)
 * 严格遵循 docs/mobile.md §3.6 与 docs/ecosystem.md §4.4 规范
 */
class HapticsEngine private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: HapticsEngine? = null

        fun getInstance(context: Context): HapticsEngine {
            return instance ?: synchronized(this) {
                instance ?: HapticsEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val credentialStore = CredentialStore.getInstance(context)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * 记账按键微触反馈 (打字机键帽轻触 - Light Impact)
     */
    fun tapKey() {
        if (!credentialStore.isHapticsEnabled) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(12)
        }
    }

    /**
     * 盖章记下沉稳反馈 (木印章盖在棉纸上 - Rigid Impact)
     */
    fun stampSave() {
        if (!credentialStore.isHapticsEnabled) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 35, 40, 60), -1)
        }
    }

    /**
     * 机械快门收放双脉冲反馈
     */
    fun shutterRelease() {
        if (!credentialStore.isHapticsEnabled) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 20, 30, 40)
            val amplitudes = intArrayOf(0, 180, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 20, 30, 40), -1)
        }
    }

    /**
     * 撤销/删除便签撕下阻尼感
     */
    fun tearDelete() {
        if (!credentialStore.isHapticsEnabled) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    }

    /**
     * 确认/锁扣扣合利落微触 (Snap Confirm)
     */
    fun snapConfirm() {
        if (!credentialStore.isHapticsEnabled) return
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(18)
        }
    }
}
