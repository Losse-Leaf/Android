package com.looseleaf.app.data.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 生物识别守护策略 (Biometrics Guard)
 * 支持指纹、面容识别与设备凭据降级解锁。
 *
 * 兼容性：`BIOMETRIC_STRONG | DEVICE_CREDENTIAL` 组合只在 API 30+ 被支持，
 * 在 API 26~29 必须退回 `setDeviceCredentialAllowed(true)`，否则 authenticate() 会抛 IllegalArgumentException。
 */
object BiometricHelper {

    private val strongOrCredential: Int
        get() = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return manager.canAuthenticate(strongOrCredential) == BiometricManager.BIOMETRIC_SUCCESS
        }
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return true
        }
        // 没有强生物识别时，只要设备设置了锁屏口令，仍可用设备凭据解锁
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return keyguard?.isDeviceSecure == true
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "散页身份验证",
        subtitle: String = "请验证指纹或面容以进入私密生活档案",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(strongOrCredential)
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // 允许重试
            }
        })

        try {
            prompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            onError(e.message ?: "无法启动生物识别")
        }
    }
}
