package com.looseleaf.feature.ledger.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 覆盖安装 / 升级完成广播接收器 (LedgerPackageReplacedReceiver)
 *
 * APK 覆盖更新后 Android 系统经常让 NotificationListenerService 处于挂起未绑定状态，
 * 收到 MY_PACKAGE_REPLACED 后立即标记检查、发起 requestRebind 并尝试恢复前台保活。
 */
class LedgerPackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appCtx = context.applicationContext
        Log.i(TAG, "收到 MY_PACKAGE_REPLACED，检查并尝试恢复通知监听绑定")
        AutoLedgerListenerStatus.markPackageReplaced(appCtx)
        AutoLedgerListenerStatus.checkAppUpdated(appCtx)
        AutoLedgerListenerStatus.ensureListening(appCtx, allowStartForegroundService = true)
    }

    companion object {
        private const val TAG = "LedgerPkgReplaced"
    }
}

/**
 * 开机广播监听恢复接收器 (LedgerBootAndWakeReceiver)
 */
class LedgerBootAndWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (context == null) return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        val appCtx = context.applicationContext
        if (!AutoLedgerListenerStatus.isAutoRecordEnabled(appCtx) ||
            !AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(appCtx)
        ) {
            return
        }
        Log.d(TAG, "收到开机广播 action=$action, 尝试恢复监听")
        AutoLedgerListenerStatus.ensureListening(appCtx, allowStartForegroundService = true)
    }

    companion object {
        private const val TAG = "LedgerBootWake"
    }
}
