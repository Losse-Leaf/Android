package com.looseleaf.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.api.APIException
import com.looseleaf.app.data.security.AppearanceMode
import com.looseleaf.app.data.security.CredentialStore
import com.looseleaf.app.data.sync.LocalDataResetCoordinator
import com.looseleaf.app.data.sync.ResetReason
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.reminder.InstallmentReminderWorker
import com.looseleaf.app.ui.components.AppUpdateDialogHost
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import kotlinx.coroutines.launch

/**
 * 托管设置中心与其附属确认弹窗（账号登出、应用内更新）。
 * 从 MainActivity 顶层直接唤起。
 */
@Composable
internal fun SettingsHost(
    appearanceMode: AppearanceMode,
    onAppearanceModeChanged: (AppearanceMode) -> Unit,
    onUnpair: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { APIClient.getInstance(context) }
    val credentialStore = remember { CredentialStore.getInstance(context) }
    val syncManager = remember { SyncManager.getInstance(context) }

    var isBiometricsEnabled by remember { mutableStateOf(credentialStore.isBiometricsEnabled) }
    var isInstallmentReminderEnabled by remember { mutableStateOf(credentialStore.isInstallmentReminderEnabled) }
    var accountName by remember { mutableStateOf(credentialStore.accountName.orEmpty()) }
    var accountEmail by remember { mutableStateOf(credentialStore.accountEmail.orEmpty()) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var pendingOutboxCount by remember { mutableIntStateOf(0) }

    // 静默刷新当前会话对应的账号摘要（兼容旧会话自动补齐昵称与邮箱）
    LaunchedEffect(Unit) {
        val session = runCatching { apiClient.fetchSession() }.getOrNull()
        val sessName = session?.account?.name?.trim().orEmpty()
        val sessEmail = session?.account?.email?.trim().orEmpty()
        if (sessName.isNotEmpty()) {
            accountName = sessName
            credentialStore.accountName = sessName
        }
        if (sessEmail.isNotEmpty()) {
            accountEmail = sessEmail
            credentialStore.accountEmail = sessEmail
        }
        if (accountName.isBlank() || accountEmail.isBlank()) {
            val profile = runCatching { apiClient.fetchCuratorProfile() }.getOrNull()
            if (accountName.isBlank() && !profile?.name.isNullOrBlank()) {
                accountName = profile!!.name.trim()
                credentialStore.accountName = accountName
            }
            if (accountEmail.isBlank() && !profile?.email.isNullOrBlank()) {
                accountEmail = profile!!.email!!.trim()
                credentialStore.accountEmail = accountEmail
            }
        }
    }

    LaunchedEffect(showLogoutDialog) {
        if (showLogoutDialog) {
            pendingOutboxCount = LocalDataResetCoordinator.getUnsyncedCount(context)
        }
    }

    val checkUpdate: () -> Unit = {
        scope.launch {
            isCheckingUpdate = true
            try {
                when (val result = UpdateChecker.checkForUpdates()) {
                    is UpdateCheckResult.HasUpdate -> {
                        availableUpdate = result.release
                        ArchiveToastManager.show("发现新版本: v${result.release.versionName}")
                    }
                    is UpdateCheckResult.AlreadyLatest -> {
                        ArchiveToastManager.show("当前已是最新版本 (v${UpdateChecker.currentVersionName})")
                    }
                    is UpdateCheckResult.Error -> {
                        ArchiveToastManager.show("检测更新失败: ${result.message}", isError = true)
                    }
                }
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    val serverHost = remember(apiClient.baseUrl) {
        apiClient.baseUrl
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
            .ifBlank { APIClient.DEFAULT_BASE_URL.removePrefix("https://") }
    }
    val displayAccountName = accountName.ifBlank {
        accountEmail.ifBlank { "Vesper Auth 账号" }
    }
    val displayAccountSubtitle = when {
        accountName.isNotBlank() && accountEmail.isNotBlank() -> accountEmail
        else -> serverHost
    }

    SettingsScreen(
        accountDisplayName = displayAccountName,
        accountSubtitle = displayAccountSubtitle,
        serverHost = serverHost,
        appearanceMode = appearanceMode,
        onAppearanceModeChanged = { mode ->
            credentialStore.appearanceMode = mode
            onAppearanceModeChanged(mode)
        },
        isBiometricsEnabled = isBiometricsEnabled,
        onBiometricsChanged = { enabled ->
            isBiometricsEnabled = enabled
            credentialStore.isBiometricsEnabled = enabled
        },
        isInstallmentReminderEnabled = isInstallmentReminderEnabled,
        onInstallmentReminderChanged = { enabled ->
            isInstallmentReminderEnabled = enabled
            credentialStore.isInstallmentReminderEnabled = enabled
            if (enabled) {
                InstallmentReminderWorker.enqueuePeriodicWork(context)
            } else {
                InstallmentReminderWorker.cancelWork(context)
            }
        },
        isCheckingUpdate = isCheckingUpdate,
        onCheckUpdate = checkUpdate,
        onLogout = { showLogoutDialog = true },
        onBack = onBack
    )

    if (showLogoutDialog) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.settings_logout_title),
            content = if (pendingOutboxCount > 0) {
                "退出登录将清除本机登录状态与本地缓存，之后可随时通过 Vesper Auth 重新登录同步。" +
                    "注意：本机还有 $pendingOutboxCount 条未同步记录（含离线日记/账目/相纸），" +
                    "退出登录将连同清除且无法找回。建议先联网同步完成后再退出。"
            } else {
                "退出登录将清除本机登录状态与本地缓存，之后可随时通过 Vesper Auth 重新登录同步。"
            },
            confirmText = stringResource(R.string.settings_logout_confirm),
            isDestructive = true,
            onConfirm = {
                showLogoutDialog = false
                scope.launch {
                    val logoutResult = runCatching { apiClient.logout() }
                    if (logoutResult.isFailure && pendingOutboxCount > 0) {
                        val code = (logoutResult.exceptionOrNull() as? APIException)?.code
                        if (code !in listOf(401, 404)) {
                            ArchiveToastManager.show(
                                "退出失败：无法连接服务器，待同步记录尚未清除，请联网同步后重试",
                                isError = true
                            )
                            return@launch
                        }
                    }
                    LocalDataResetCoordinator.resetAll(
                        context = context,
                        syncManager = syncManager,
                        reason = ResetReason.UNPAIR
                    )
                    onBack()
                    onUnpair()
                }
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    AppUpdateDialogHost(
        release = availableUpdate,
        onDismiss = { availableUpdate = null }
    )
}
