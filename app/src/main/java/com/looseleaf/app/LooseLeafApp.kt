package com.looseleaf.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.ProfileRepository
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LooseLeafApp : Application(), ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Token 通过回调惰性读取，避免冷启动在主线程创建 EncryptedSharedPreferences
        val credentialStore = CredentialStore.getInstance(this)
        val apiClient = APIClient.getInstance(this)
        apiClient.tokenProvider = { credentialStore.deviceToken }

        // 以下三件事都涉及磁盘 IO（SharedPreferences / KeyStore / WorkManager 入队），
        // 统一移到 IO：ProfileRepository.init 会同步读档案 JSON，
        // getProfile/getSiteConfig 里的「未初始化则 init」兜底保持不变，正常路径已避开主线程。
        appScope.launch {
            runCatching { ProfileRepository.init(this@LooseLeafApp) }
            runCatching { credentialStore.warmUp() }
            runCatching { SyncManager.getInstance(this@LooseLeafApp).scheduleSync() }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024) // 128MB 磁盘持久缓存
                    .build()
            }
            .okHttpClient {
                APIClient.getInstance(this).okHttpClient
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
