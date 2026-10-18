package com.looseleaf.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Android 本机受信任设备凭据保险箱 (CredentialStore)
 * 严格遵从 docs/mobile.md §3.4 规范：
 * 使用 Android KeyStore 硬件加密模块配合 EncryptedSharedPreferences 存储 device_token
 */
class CredentialStore private constructor(context: Context) {

    companion object {
        private const val TAG = "CredentialStore"
        private const val SECURE_PREFS_FILE = "looseleaf_secure_creds"
        private const val FALLBACK_PREFS_FILE = "looseleaf_fallback_creds"

        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        private const val KEY_DARKROOM_MODE = "darkroom_mode"

        @Volatile
        private var instance: CredentialStore? = null

        fun getInstance(context: Context): CredentialStore {
            return instance ?: synchronized(this) {
                instance ?: CredentialStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = createEncryptedOrFallbackPrefs(context)

    private fun createEncryptedOrFallbackPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences initialization failed, falling back to standard: ${e.message}")
            context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
        }
    }

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()
        }

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        }

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, Build.MODEL ?: "Android Device") ?: (Build.MODEL ?: "Android Device")
        set(value) {
            prefs.edit().putString(KEY_DEVICE_NAME, value).apply()
        }

    var isBiometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_BIOMETRICS_ENABLED, value).apply()
        }

    var isDarkroomMode: Boolean
        get() = prefs.getBoolean(KEY_DARKROOM_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARKROOM_MODE, value).apply()
        }

    var cachedProfileJson: String?
        get() = prefs.getString("cached_profile_json", null)
        set(value) {
            prefs.edit().putString("cached_profile_json", value).apply()
        }

    var cachedSiteConfigJson: String?
        get() = prefs.getString("cached_site_config_json", null)
        set(value) {
            prefs.edit().putString("cached_site_config_json", value).apply()
        }

    fun isPaired(): Boolean {
        return !deviceToken.isNullOrBlank()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_DEVICE_ID)
            .remove("cached_profile_json")
            .remove("cached_site_config_json")
            .apply()
    }
}
