package com.looseleaf.app.data.local

import android.content.Context
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.SiteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 散页主理人档案与站点配置本地单例缓存仓储 (ProfileRepository)
 * 实现 Stale-While-Revalidate (SWR) 0ms 瞬间上屏策略：
 * 1. 启动或切换 Tab 时由本地持久化与内存即刻加载，杜绝闪烁与重置为空白
 * 2. 静默拉取服务端最新态并平滑同步
 */
object ProfileRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val _profileState = MutableStateFlow<CuratorProfile?>(null)
    val profileState: StateFlow<CuratorProfile?> = _profileState.asStateFlow()

    private val _siteConfigState = MutableStateFlow<SiteConfig?>(null)
    val siteConfigState: StateFlow<SiteConfig?> = _siteConfigState.asStateFlow()

    @Volatile
    private var isInitialized = false

    /**
     * 预热本地缓存快照。
     *
     * MOB-P1-21：原先在 `synchronized(this)` **持锁期间**做 JSON 反序列化 ——
     * Application 在 IO 线程预热时，主线程的 getProfile() → init() 会阻塞在同一把
     * monitor 上等待解码结束（资料 JSON 越大越久，典型 50~300ms），
     * 表现为「切到展台页掉一下帧」。
     * 现在把解码放到锁外，锁内只做状态赋值，主线程最多等一次极短的赋值。
     */
    fun init(context: Context) {
        if (isInitialized) return
        val creds = CredentialStore.getInstance(context)
        val profileRaw = creds.cachedProfileJson
        val configRaw = creds.cachedSiteConfigJson

        // —— 锁外解码 ——
        val decodedProfile = profileRaw?.let { raw ->
            runCatching { json.decodeFromString<CuratorProfile>(raw) }.getOrNull()
        }
        val decodedConfig = configRaw?.let { raw ->
            runCatching { json.decodeFromString<SiteConfig>(raw) }.getOrNull()
        }

        synchronized(this) {
            if (isInitialized) return
            decodedProfile?.let { _profileState.value = it }
            decodedConfig?.let { _siteConfigState.value = it }
            isInitialized = true
        }
    }

    fun getProfile(context: Context): CuratorProfile {
        if (!isInitialized) init(context)
        return _profileState.value ?: CuratorProfile()
    }

    fun getSiteConfig(context: Context): SiteConfig {
        if (!isInitialized) init(context)
        return _siteConfigState.value ?: SiteConfig()
    }

    fun updateProfile(context: Context, profile: CuratorProfile) {
        _profileState.value = profile
        val creds = CredentialStore.getInstance(context)
        runCatching {
            creds.cachedProfileJson = json.encodeToString(profile)
        }
    }

    fun updateSiteConfig(context: Context, config: SiteConfig) {
        _siteConfigState.value = config
        val creds = CredentialStore.getInstance(context)
        runCatching {
            creds.cachedSiteConfigJson = json.encodeToString(config)
        }
    }

    fun clearCache(context: Context) {
        _profileState.value = null
        _siteConfigState.value = null
        val creds = CredentialStore.getInstance(context)
        creds.cachedProfileJson = null
        creds.cachedSiteConfigJson = null
    }
}
