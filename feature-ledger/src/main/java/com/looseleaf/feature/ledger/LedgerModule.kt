package com.looseleaf.feature.ledger

import android.content.Context
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.feature.ledger.data.LedgerRepository

/**
 * 账本模块唯一对外暴露的初始化配置入口 (LedgerModule)
 */
object LedgerModule {
    fun init(
        context: Context,
        credentialStore: CredentialStore? = null,
        apiClient: APIClient? = null
    ) {
        LedgerRepository.init(context, credentialStore, apiClient)
    }
}
