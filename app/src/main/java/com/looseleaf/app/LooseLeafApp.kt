package com.looseleaf.app

import android.app.Application
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.SyncManager

class LooseLeafApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 绑定 APIClient 的 Token Provider
        val credentialStore = CredentialStore.getInstance(this)
        val apiClient = APIClient.getInstance(this)
        apiClient.tokenProvider = {
            credentialStore.deviceToken
        }

        // 启动时初始化同步发件箱队列
        val syncManager = SyncManager.getInstance(this)
        syncManager.scheduleSync()
    }
}
