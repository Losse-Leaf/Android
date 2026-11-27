package com.looseleaf.feature.ledger.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 自动记账监听连接状态管理器 (AutoLedgerListenerStatus)
 *
 * 严格区分「系统设置开关是否打开」与「系统 Binder 是否真正连通」：
 * - 覆盖安装升级或系统省电冻结后，系统设置里开关仍显示开启，但 NotificationListenerService
 *   可能尚未绑定（onListenerConnected 未触发，收不到任何通知）；
 * - 统一管理 requestRebind、安全冷却的 component toggle 强重连与版本升级检测。
 */
internal object AutoLedgerListenerStatus {

    private const val TAG = "AutoLedgerStatus"
    private const val PREFS_NAME = "looseleaf_ledger_settings"
    private const val KEY_AUTO_RECORD_ENABLED = "auto_record_enabled"
    private const val KEY_LAST_VERSION_CODE = "nls_last_version_code"
    private const val KEY_NEEDS_REBIND_AFTER_UPDATE = "nls_needs_rebind_after_update"
    private const val KEY_LAST_FORCE_RECONNECT_MS = "nls_last_force_reconnect_ms"

    private val binderConnected = AtomicBoolean(false)
    private val lastForceAt = AtomicLong(0L)

    private val _isListenerBinderConnected = MutableStateFlow(false)
    val isListenerBinderConnected: StateFlow<Boolean> = _isListenerBinderConnected.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _lastNotificationAliveAtMs = MutableStateFlow(0L)
    val lastNotificationAliveAtMs: StateFlow<Long> = _lastNotificationAliveAtMs.asStateFlow()

    private val _notificationsSeenCount = MutableStateFlow(0)
    val notificationsSeenCount: StateFlow<Int> = _notificationsSeenCount.asStateFlow()

    private val _needsRebindAfterUpdate = MutableStateFlow(false)
    val needsRebindAfterUpdate: StateFlow<Boolean> = _needsRebindAfterUpdate.asStateFlow()

    fun isAutoRecordEnabled(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_RECORD_ENABLED, true)
    }

    fun isNotificationAccessEnabledInSettings(context: Context): Boolean {
        val appCtx = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val granted = runCatching {
                val nm = appCtx.getSystemService(NotificationManager::class.java)
                val cn = ComponentName(appCtx, LedgerNotificationListenerService::class.java)
                nm?.isNotificationListenerAccessGranted(cn) == true
            }.getOrDefault(false)
            if (granted) return true
        }
        val flat = runCatching {
            Settings.Secure.getString(appCtx.contentResolver, "enabled_notification_listeners")
        }.getOrNull().orEmpty()
        if (flat.contains(appCtx.packageName) && flat.contains("LedgerNotificationListenerService")) {
            return true
        }
        return NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
    }

    fun markListenerConnected(context: Context) {
        val appCtx = context.applicationContext
        binderConnected.set(true)
        _isListenerBinderConnected.value = true
        _needsRebindAfterUpdate.value = false
        _lastNotificationAliveAtMs.value = System.currentTimeMillis()
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NEEDS_REBIND_AFTER_UPDATE, false)
            .apply()
    }

    fun markListenerDisconnected(context: Context) {
        binderConnected.set(false)
        _isListenerBinderConnected.value = false
    }

    fun markNotificationAlive(context: Context) {
        val appCtx = context.applicationContext
        val wasConnected = binderConnected.getAndSet(true)
        _isListenerBinderConnected.value = true
        _lastNotificationAliveAtMs.value = System.currentTimeMillis()
        _notificationsSeenCount.value = _notificationsSeenCount.value + 1
        if (_needsRebindAfterUpdate.value || !wasConnected) {
            _needsRebindAfterUpdate.value = false
            appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NEEDS_REBIND_AFTER_UPDATE, false)
                .apply()
        }
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun checkAppUpdated(context: Context): Boolean {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = currentVersionCode(appCtx)
        val last = prefs.getLong(KEY_LAST_VERSION_CODE, -1L)
        if (last < 0L) {
            prefs.edit().putLong(KEY_LAST_VERSION_CODE, current).apply()
            _needsRebindAfterUpdate.value =
                prefs.getBoolean(KEY_NEEDS_REBIND_AFTER_UPDATE, false) && !binderConnected.get()
            return false
        }
        if (current > 0L && current != last) {
            // 若当前进程已收到 onListenerConnected / 通知回调，切勿覆盖已连通的实时状态
            val alreadyConnected = binderConnected.get()
            if (!alreadyConnected) {
                _isListenerBinderConnected.value = false
                _needsRebindAfterUpdate.value = true
            }
            prefs.edit()
                .putLong(KEY_LAST_VERSION_CODE, current)
                .putBoolean(KEY_NEEDS_REBIND_AFTER_UPDATE, !alreadyConnected)
                .apply()
            Log.i(TAG, "检测到应用覆盖升级 ($last -> $current)，connected=$alreadyConnected")
            return !alreadyConnected
        }
        _needsRebindAfterUpdate.value = prefs.getBoolean(KEY_NEEDS_REBIND_AFTER_UPDATE, false) &&
            !binderConnected.get()
        return false
    }

    fun markPackageReplaced(context: Context) {
        val appCtx = context.applicationContext
        if (binderConnected.get()) return
        _isListenerBinderConnected.value = false
        _needsRebindAfterUpdate.value = true
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NEEDS_REBIND_AFTER_UPDATE, true)
            .apply()
    }

    fun requestRebind(context: Context, force: Boolean = false): Boolean {
        val appCtx = context.applicationContext
        if (!isNotificationAccessEnabledInSettings(appCtx)) {
            markListenerDisconnected(appCtx)
            return false
        }
        if (!force && binderConnected.get()) {
            return true
        }
        return runCatching {
            val cn = ComponentName(appCtx, LedgerNotificationListenerService::class.java)
            ensureComponentEnabled(appCtx, cn)
            NotificationListenerService.requestRebind(cn)
            Log.d(TAG, "已向系统发起 NotificationListenerService.requestRebind(force=$force)")
            true
        }.getOrElse { e ->
            Log.w(TAG, "requestRebind 调用失败: ${e.message}")
            false
        }
    }

    /**
     * 仅在后台 IO 线程调用：通过先禁用再启用监听组件触发系统重新绑定。
     * 包含默认冷却保护，避免短时间内高频切换导致系统拒绝绑定。
     */
    fun forceReconnect(
        context: Context,
        bypassCooldown: Boolean = false,
        cooldownMs: Long = 12 * 60 * 1000L
    ): Boolean {
        val appCtx = context.applicationContext
        if (!isNotificationAccessEnabledInSettings(appCtx)) {
            markListenerDisconnected(appCtx)
            return false
        }
        val now = System.currentTimeMillis()
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val last = maxOf(lastForceAt.get(), prefs.getLong(KEY_LAST_FORCE_RECONNECT_MS, 0L))
        if (!bypassCooldown && now - last < cooldownMs) {
            return requestRebind(appCtx, force = true)
        }
        return runCatching {
            val pm = appCtx.packageManager
            val cn = ComponentName(appCtx, LedgerNotificationListenerService::class.java)
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Thread.sleep(400L)
            pm.setComponentEnabledSetting(
                cn,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Thread.sleep(200L)
            NotificationListenerService.requestRebind(cn)
            lastForceAt.set(now)
            prefs.edit().putLong(KEY_LAST_FORCE_RECONNECT_MS, now).apply()
            Log.i(TAG, "已完成组件开关重绑与 requestRebind")
            true
        }.getOrElse { e ->
            Log.w(TAG, "forceReconnect 执行失败: ${e.message}")
            false
        }
    }

    fun ensureListening(
        context: Context,
        allowStartForegroundService: Boolean = true
    ) {
        val appCtx = context.applicationContext
        if (!isAutoRecordEnabled(appCtx)) return
        if (!isNotificationAccessEnabledInSettings(appCtx)) {
            markListenerDisconnected(appCtx)
            return
        }
        if (allowStartForegroundService) {
            LedgerKeepAliveService.start(appCtx)
        }
        if (!binderConnected.get()) {
            requestRebind(appCtx, force = true)
        }
    }

    private fun ensureComponentEnabled(context: Context, cn: ComponentName) {
        runCatching {
            val pm = context.packageManager
            val state = pm.getComponentEnabledSetting(cn)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            ) {
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private fun currentVersionCode(context: Context): Long {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
    }
}
