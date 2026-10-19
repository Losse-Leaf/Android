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

        // 预热主理人档案与站点配置本地缓存（普通 Prefs，不触碰 KeyStore）
        ProfileRepository.init(this)

        // Token 通过回调惰性读取，避免冷启动在主线程创建 EncryptedSharedPreferences
        val credentialStore = CredentialStore.getInstance(this)
        val apiClient = APIClient.getInstance(this)
        apiClient.tokenProvider = { credentialStore.deviceToken }

        // 发件箱调度属于后台工作，不占用冷启动主线程；
        // 顺带在 IO 上预热 KeyStore 加密 Prefs——MainActivity 组合期要读 isPaired()，
        // 若等它自己触发初始化，这笔 IO 就会掉到主线程上。
        appScope.launch {
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
