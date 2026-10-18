package com.looseleaf.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.looseleaf.app.audio.AudioEngine
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.local.ProfileRepository
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager

class LooseLeafApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // 预热主理人档案与站点配置本地缓存 (0ms SWR)
        ProfileRepository.init(this)

        // 绑定 APIClient 的 Token Provider 与全局音效设置
        val credentialStore = CredentialStore.getInstance(this)
        AudioEngine.isSoundEnabled = credentialStore.isSoundEnabled
        val apiClient = APIClient.getInstance(this)
        apiClient.tokenProvider = {
            credentialStore.deviceToken
        }

        // 启动时初始化同步发件箱队列
        try {
            val syncManager = SyncManager.getInstance(this)
            syncManager.scheduleSync()
        } catch (e: Exception) {
            e.printStackTrace()
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
