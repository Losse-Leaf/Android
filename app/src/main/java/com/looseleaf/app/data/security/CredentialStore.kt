package com.looseleaf.app.data.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** 外观三态（AOPT-08c）：跟随系统 / 暖色棉纸 / 暗房安全灯 */
enum class AppearanceMode {
    SYSTEM,
    PAPER,
    DARKROOM
}

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
        private const val KEY_APPEARANCE_MODE = "appearance_mode"
        private const val KEY_CACHED_PROFILE = "cached_profile_json"
        private const val KEY_CACHED_SITE_CONFIG = "cached_site_config_json"

        /**
         * MOB-P0-10：配对态镜像标记（普通 Prefs，非机密）。
         * 组合期读取配对态时若去碰 EncryptedSharedPreferences，
         * 会把 KeyStore 初始化 + 文件 IO 拖到主线程，冷启动首帧直接被阻塞。
         */
            private const val KEY_PAIRED_FLAG = "paired_flag"

        /** MOB-P2-45：高对比度模式开关（Android 未公开高对比度 API，故由应用内提供） */
        private const val KEY_HIGH_CONTRAST = "high_contrast"

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

    /**
     * MOB-P1-39：device_token 的内存镜像。
     * EncryptedSharedPreferences 每次读取都要做一次 AES-GCM 解密 + Base64，
     * 而 APIClient 每个 HTTP 请求都会读一次 token ⇒ 白白多出数十次解密。
     */
    @Volatile
    private var cachedDeviceToken: String? = null

    @Volatile
    private var tokenLoaded = false

    var deviceToken: String?
        get() {
            if (tokenLoaded) return cachedDeviceToken
            synchronized(this) {
                if (!tokenLoaded) {
                    cachedDeviceToken = securePrefs?.getString(KEY_DEVICE_TOKEN, null)
                        ?.takeIf { it.isNotBlank() }
                    tokenLoaded = true
                }
            }
            return cachedDeviceToken
        }
        set(value) {
            cachedDeviceToken = value?.takeIf { it.isNotBlank() }
            tokenLoaded = true
            securePrefs?.edit()?.putString(KEY_DEVICE_TOKEN, value)?.apply()
            // 同步配对态镜像：让主线程可以零 KeyStore 成本判断「是否已入网」
            settings.edit()
                .putBoolean(KEY_PAIRED_FLAG, !value.isNullOrBlank())
                .apply()
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

    /**
     * MOB-P2-45：高对比度模式。
     * 开启后次级文字与分隔线会显著加强对比，便于低视力用户阅读。
     */
    var highContrast: Boolean
        get() = settings.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) {
            settings.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()
        }

    var isDarkroomMode: Boolean
        get() = settings.getBoolean(KEY_DARKROOM_MODE, false)
        set(value) {
            settings.edit().putBoolean(KEY_DARKROOM_MODE, value).apply()
        }

    /**
     * 外观三态（AOPT-08c）：跟随系统 / 暖色棉纸 / 暗房安全灯。
     *
     * 存储上仍以 isDarkroomMode 布尔为事实源并追加 mode 键：
     * 旧版只写了布尔值，读取时若 mode 键缺失则按布尔回退，升级用户外观不跳变。
     */
    var appearanceMode: AppearanceMode
        get() {
            val raw = settings.getString(KEY_APPEARANCE_MODE, null)
            if (raw != null) {
                return runCatching { AppearanceMode.valueOf(raw) }.getOrDefault(AppearanceMode.SYSTEM)
            }
            return if (settings.getBoolean(KEY_DARKROOM_MODE, false)) {
                AppearanceMode.DARKROOM
            } else {
                AppearanceMode.PAPER
            }
        }
        set(value) {
            settings.edit()
                .putString(KEY_APPEARANCE_MODE, value.name)
                // 同步旧布尔键，保证未读取新键的代码路径行为一致
                .putBoolean(KEY_DARKROOM_MODE, value == AppearanceMode.DARKROOM)
                .apply()
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

    /**
     * 是否已入网。
     *
     * MOB-P0-10：正常路径只读普通 Prefs 的镜像标记，**绝不触碰 KeyStore**，
     * 因此组合期调用不会把加密存储初始化拖到主线程。
     * 只有升级上来的老设备（还没有镜像标记）才会回退一次安全存储判定，
     * 并顺手补写镜像，此后不再产生 KeyStore 开销。
     */
    fun isPaired(): Boolean {
        if (settings.contains(KEY_PAIRED_FLAG)) {
            return settings.getBoolean(KEY_PAIRED_FLAG, false)
        }
        val paired = isSecureStorageAvailable && !deviceToken.isNullOrBlank()
        settings.edit().putBoolean(KEY_PAIRED_FLAG, paired).apply()
        return paired
    }

    fun clearCredentials() {
        cachedDeviceToken = null
        tokenLoaded = true
        securePrefs?.edit()
            ?.remove(KEY_DEVICE_TOKEN)
            ?.remove(KEY_DEVICE_ID)
            ?.remove(KEY_DEVICE_NAME)
            ?.apply()
        settings.edit()
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_CACHED_PROFILE)
            .remove(KEY_CACHED_SITE_CONFIG)
            .remove(KEY_PAIRED_FLAG)
            .apply()
    }
}
