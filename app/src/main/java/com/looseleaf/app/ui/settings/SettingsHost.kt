package com.looseleaf.app.ui.settings

import androidx.compose.runtime.*
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
import com.looseleaf.app.ui.components.ArchivePromptDialog
import com.looseleaf.app.ui.components.ArchiveToastManager
import com.looseleaf.app.updater.ReleaseInfo
import com.looseleaf.app.updater.UpdateCheckResult
import com.looseleaf.app.updater.UpdateChecker
import kotlinx.coroutines.launch

/**
 * 托管设置中心与其附属确认弹窗（解绑、设备改名、应用内更新）。
 * 从原 ProfileScreen 提升出来，供 MainActivity 顶层直接唤起。
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
    var localDeviceName by remember { mutableStateOf(credentialStore.deviceName) }
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showRenameDeviceDialog by remember { mutableStateOf(false) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    var pendingOutboxCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(showUnpairDialog) {
        if (showUnpairDialog) {
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

    SettingsScreen(
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
        localDeviceName = localDeviceName,
        localDeviceId = credentialStore.deviceId ?: "",
        onRenameLocalDevice = { showRenameDeviceDialog = true },
        isCheckingUpdate = isCheckingUpdate,
        onCheckUpdate = checkUpdate,
        onUnpair = { showUnpairDialog = true },
        onBack = onBack
    )

    if (showUnpairDialog) {
        ArchiveConfirmDialog(
            title = stringResource(R.string.profile_screen_title_01),
            content = if (pendingOutboxCount > 0) {
                "解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。" +
                    "注意：本机还有 $pendingOutboxCount 条未同步记录（含离线日记/账目/相纸），" +
                    "解绑将连同删除且**无法找回**。建议先联网同步完成后再解绑。"
            } else {
                "解除后将清除本机 KeyStore 中的 Device Token，本机将无法再向中枢提交任何记录，需重新扫码配对。"
            },
            confirmText = stringResource(R.string.profile_screen_confirmText_01),
            isDestructive = true,
            onConfirm = {
                val currentDeviceId = credentialStore.deviceId
                showUnpairDialog = false
                scope.launch {
                    if (!currentDeviceId.isNullOrBlank()) {
                        val revoke = runCatching { apiClient.revokeDevice(currentDeviceId) }
                        if (revoke.isFailure) {
                            val code = (revoke.exceptionOrNull() as? APIException)?.code
                            if (code !in listOf(401, 404)) {
                                ArchiveToastManager.show(
                                    "解除失败：无法联系中枢，本机数据与待同步记录均未清除，请联网后重试",
                                    isError = true
                                )
                                return@launch
                            }
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
            onDismiss = { showUnpairDialog = false }
        )
    }

    if (showRenameDeviceDialog) {
        ArchivePromptDialog(
            title = stringResource(R.string.profile_screen_title_02),
            initialValue = localDeviceName,
            placeholder = stringResource(R.string.pairing_screen_placeholder_02),
            label = stringResource(R.string.profile_screen_label_03),
            confirmText = stringResource(R.string.profile_screen_confirmText_02),
            onDismiss = { showRenameDeviceDialog = false },
            onConfirm = { newName ->
                val devId = credentialStore.deviceId
                if (!devId.isNullOrBlank()) {
                    scope.launch {
                        runCatching {
                            apiClient.updateDeviceName(devId, newName)
                            credentialStore.deviceName = newName
                            localDeviceName = newName
                            ArchiveToastManager.show("设备名称已更新")
                        }.onFailure { err ->
                            ArchiveToastManager.show("更新设备名称失败: ${err.message}", isError = true)
                        }
                        showRenameDeviceDialog = false
                    }
                } else {
                    credentialStore.deviceName = newName
                    localDeviceName = newName
                    showRenameDeviceDialog = false
                }
            }
        )
    }

    AppUpdateDialogHost(
        release = availableUpdate,
        onDismiss = { availableUpdate = null }
    )
}
