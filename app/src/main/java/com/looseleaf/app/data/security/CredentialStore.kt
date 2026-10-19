package com.looseleaf.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 本机凭据保险箱 (CredentialStore)
 *
 * 分两层存放，避免把「界面开关」也绑死在 KeyStore 上：
 * - securePrefs：device_token / device_id 等真正敏感的设备凭据，KeyStore 硬件加密；
 * - settings：暗房模式、生物识别开关、档案 JSON 缓存等非机密状态，普通 Prefs。
 *
 * 若 KeyStore 初始化失败，**不再降级成明文存 token**：isSecureStorageAvailable 置 false，
 * 配对页会明确拒绝入网，而不是悄悄把长期凭据写在明文里。
 */
class CredentialStore private constructor(context: Context) {

    companion object {
        private const val TAG = "CredentialStore"
        private const val SECURE_PREFS_FILE = "looseleaf_secure_creds"
        private const val SETTINGS_PREFS_FILE = "looseleaf_settings"

        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        private const val KEY_DARKROOM_MODE = "darkroom_mode"
        private const val KEY_CACHED_PROFILE = "cached_profile_json"
        private const val KEY_CACHED_SITE_CONFIG = "cached_site_config_json"

        @Volatile
        private var instance: CredentialStore? = null

        fun getInstance(context: Context): CredentialStore {
            return instance ?: synchronized(this) {
                instance ?: CredentialStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context

    private val settings: SharedPreferences by lazy {
        appContext.getSharedPreferences(SETTINGS_PREFS_FILE, Context.MODE_PRIVATE)
    }

    private val securePrefs: SharedPreferences? by lazy { createSecurePrefs() }

    /** KeyStore 不可用时为 false，此时不允许配对（宁可不入网，也不明文存 token）。 */
    val isSecureStorageAvailable: Boolean
        get() = securePrefs != null

    /**
     * 在后台线程预热 [securePrefs]。
     *
     * `EncryptedSharedPreferences.create` 会走 KeyStore + 读文件，属于 IO。
     * MainActivity 组合期要读 `isPaired()`，若此时还没初始化，这笔 IO 就落到了主线程，
     * 因此 Application.onCreate 里先在 IO 上把它建好。
     */
    fun warmUp() {
        securePrefs
    }

    private fun createSecurePrefs(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { migrateLegacyFlags(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Keystore unavailable, secure credential storage disabled: ${e.message}")
            null
        }
    }

    /** 旧版本把界面开关也存在加密 Prefs 里，一次性搬到普通 Prefs，避免升级后设置被重置。 */
    private fun migrateLegacyFlags(legacy: SharedPreferences) {
        val editor = settings.edit()
        var dirty = false
        if (!settings.contains(KEY_DARKROOM_MODE) && legacy.contains(KEY_DARKROOM_MODE)) {
            editor.putBoolean(KEY_DARKROOM_MODE, legacy.getBoolean(KEY_DARKROOM_MODE, false))
            dirty = true
        }
        if (!settings.contains(KEY_BIOMETRICS_ENABLED) && legacy.contains(KEY_BIOMETRICS_ENABLED)) {
            editor.putBoolean(KEY_BIOMETRICS_ENABLED, legacy.getBoolean(KEY_BIOMETRICS_ENABLED, false))
            dirty = true
        }
        if (!settings.contains(KEY_CACHED_PROFILE) && legacy.contains(KEY_CACHED_PROFILE)) {
            editor.putString(KEY_CACHED_PROFILE, legacy.getString(KEY_CACHED_PROFILE, null))
            dirty = true
        }
        if (!settings.contains(KEY_CACHED_SITE_CONFIG) && legacy.contains(KEY_CACHED_SITE_CONFIG)) {
            editor.putString(KEY_CACHED_SITE_CONFIG, legacy.getString(KEY_CACHED_SITE_CONFIG, null))
            dirty = true
        }
        if (!settings.contains(KEY_DEVICE_NAME) && legacy.contains(KEY_DEVICE_NAME)) {
            editor.putString(KEY_DEVICE_NAME, legacy.getString(KEY_DEVICE_NAME, null))
            dirty = true
        }
        if (dirty) editor.apply()
    }

    var deviceToken: String?
        get() = securePrefs?.getString(KEY_DEVICE_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            securePrefs?.edit()?.putString(KEY_DEVICE_TOKEN, value)?.apply()
        }

    var deviceId: String?
        get() = securePrefs?.getString(KEY_DEVICE_ID, null)
        set(value) {
            securePrefs?.edit()?.putString(KEY_DEVICE_ID, value)?.apply()
        }

    var deviceName: String
        get() = settings.getString(KEY_DEVICE_NAME, null)
            ?: (Build.MODEL ?: "Android Device")
        set(value) {
            settings.edit().putString(KEY_DEVICE_NAME, value).apply()
        }

    var isBiometricsEnabled: Boolean
        get() = settings.getBoolean(KEY_BIOMETRICS_ENABLED, false)
        set(value) {
            settings.edit().putBoolean(KEY_BIOMETRICS_ENABLED, value).apply()
        }

    var isDarkroomMode: Boolean
        get() = settings.getBoolean(KEY_DARKROOM_MODE, false)
        set(value) {
            settings.edit().putBoolean(KEY_DARKROOM_MODE, value).apply()
        }

    var cachedProfileJson: String?
        get() = settings.getString(KEY_CACHED_PROFILE, null)
        set(value) {
            settings.edit().putString(KEY_CACHED_PROFILE, value).apply()
        }

    var cachedSiteConfigJson: String?
        get() = settings.getString(KEY_CACHED_SITE_CONFIG, null)
        set(value) {
            settings.edit().putString(KEY_CACHED_SITE_CONFIG, value).apply()
        }

    fun isPaired(): Boolean {
        return isSecureStorageAvailable && !deviceToken.isNullOrBlank()
    }

    fun clearCredentials() {
        securePrefs?.edit()
            ?.remove(KEY_DEVICE_TOKEN)
            ?.remove(KEY_DEVICE_ID)
            ?.remove(KEY_DEVICE_NAME)
            ?.apply()
        settings.edit()
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_CACHED_PROFILE)
            .remove(KEY_CACHED_SITE_CONFIG)
            .apply()
    }
}
