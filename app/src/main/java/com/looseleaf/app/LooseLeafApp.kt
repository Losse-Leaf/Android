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
import com.looseleaf.app.utils.CrashReporter
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineExceptionHandler

class LooseLeafApp : Application(), ImageLoaderFactory {

    // MOB-P2-39：命名 + 异常兜底 —— 崩溃归因与日志都能带上作用域名
    private val appScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.IO +
            CoroutineName("looseleaf.app") +
            CoroutineExceptionHandler { _, throwable ->
                android.util.Log.e("LooseLeafApp", "app scope failure", throwable)
            }
    )

    override fun onCreate() {
        super.onCreate()

        // MOB-P1-63：尽早接管全局异常处理（必须在任何业务初始化之前）
        CrashReporter.install(this)

        // Token 通过回调惰性读取，避免冷启动在主线程创建 EncryptedSharedPreferences
        val credentialStore = CredentialStore.getInstance(this)
        val apiClient = APIClient.getInstance(this)
        apiClient.tokenProvider = { credentialStore.deviceToken }

        // 以下三件事都涉及磁盘 IO（SharedPreferences / KeyStore / WorkManager 入队），
        // 统一移到 IO：ProfileRepository.init 会同步读档案 JSON，
        // getProfile/getSiteConfig 里的「未初始化则 init」兜底保持不变，正常路径已避开主线程。
        appScope.launch {
            runCatching { com.looseleaf.feature.ledger.LedgerModule.init(this@LooseLeafApp) }
            runCatching { ProfileRepository.init(this@LooseLeafApp) }
            runCatching { credentialStore.warmUp() }
            // AND-7：SyncManager 的 Room / 计数 Flow 订阅改为显式后台启动，
            // 冷启动首帧不再构建数据库观察者。
            runCatching {
                val syncManager = SyncManager.getInstance(this@LooseLeafApp)
                syncManager.ensureStarted()
                // MOB-P2-28 / AND-7：只在**确有积压**时才唤醒 WorkManager，
                // 且用一次 COUNT 查询判断，无需先建立两条常驻 Flow。
                if (syncManager.pendingEventCountNow() > 0) {
                    syncManager.scheduleSync()
                }
            }
            runCatching {
                com.looseleaf.app.reminder.InstallmentReminderWorker.enqueuePeriodicWork(this@LooseLeafApp)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    // MOB-P2-32：25% 的堆在 128MB 机型上就是 32MB 常驻位图，
                    // 降到 15% 更安全（Coil 默认值就是 20%）
                    .maxSizePercent(0.15)
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
