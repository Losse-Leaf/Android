package com.looseleaf.feature.ledger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.looseleaf.core.R as CoreR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 自动记账前台保活与监听重连巡检服务 (LedgerKeepAliveService)
 *
 * 借鉴开源项目 `huanghhcri/SmartLedger` (MIT License) KeepAliveService：
 * 1. 当通知读取权与自动记账均已开启时维持低打扰前台常驻通知，降低国产 ROM 后台冻结/杀进程概率；
 * 2. 动态注册 ACTION_USER_PRESENT 解锁广播，亮屏解锁时检测并尝试重连未绑定的监听；
 * 3. 若系统设置已开启但 Binder 尚未连接，周期性触发 requestRebind 并在连续未连上时升级为安全的
 *    component toggle 强制重连；一旦已连接则只做低频心跳巡检，绝不拆掉健康连接。
 */
class LedgerKeepAliveService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var failStreak = 0
    private var unlockReceiverRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_USER_PRESENT) return
            val appCtx = applicationContext
            if (!AutoLedgerListenerStatus.isAutoRecordEnabled(appCtx) ||
                !AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(appCtx)
            ) {
                return
            }
            if (!AutoLedgerListenerStatus.isListenerBinderConnected.value) {
                AutoLedgerListenerStatus.requestRebind(appCtx, force = true)
            }
        }
    }

    private val recoverRunnable = object : Runnable {
        override fun run() {
            val nextDelayMs = tickRecover() ?: return
            mainHandler.postDelayed(this, nextDelayMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        runCatching {
            ContextCompat.registerReceiver(
                this,
                unlockReceiver,
                IntentFilter(Intent.ACTION_USER_PRESENT),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            unlockReceiverRegistered = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AutoLedgerListenerStatus.isAutoRecordEnabled(this) ||
            !AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(this)
        ) {
            isServiceRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        promoteForeground()
        if (isServiceRunning && !AutoLedgerListenerStatus.isListenerBinderConnected.value) {
            AutoLedgerListenerStatus.requestRebind(this, force = true)
        }
        mainHandler.removeCallbacks(recoverRunnable)
        mainHandler.postDelayed(recoverRunnable, 2_500L)
        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        if (unlockReceiverRegistered) {
            runCatching { unregisterReceiver(unlockReceiver) }
            unlockReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteForeground() {
        val notification = buildNotification(this)
        val promoted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.getOrElse { e ->
            Log.w(TAG, "启动前台保活通知失败: ${e.message}")
            false
        }
        isServiceRunning = promoted
        if (!promoted) {
            stopSelf()
        }
    }

    private fun tickRecover(): Long? {
        if (!AutoLedgerListenerStatus.isAutoRecordEnabled(this) ||
            !AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(this)
        ) {
            failStreak = 0
            isServiceRunning = false
            stopSelf()
            return null
        }

        if (!isServiceRunning) return null
        refreshNotification(this)

        if (AutoLedgerListenerStatus.isListenerBinderConnected.value) {
            failStreak = 0
            return RECOVER_SLOW_MS
        }

        failStreak++
        AutoLedgerListenerStatus.requestRebind(this, force = true)

        if (failStreak == 3 || (failStreak > 3 && failStreak % 10 == 0)) {
            val appCtx = applicationContext
            serviceScope.launch {
                AutoLedgerListenerStatus.forceReconnect(appCtx, bypassCooldown = false)
                mainHandler.post { refreshNotification(appCtx) }
            }
        }
        return RECOVER_FAST_MS
    }

    companion object {
        private const val TAG = "LedgerKeepAlive"
        private const val CHANNEL_ID = "ledger_auto_keepalive"
        private const val CHANNEL_NAME = "自动记账后台监听"
        private const val NOTIFICATION_ID = 4102
        private const val RECOVER_FAST_MS = 15_000L
        private const val RECOVER_SLOW_MS = 120_000L

        @Volatile
        internal var isServiceRunning = false
            private set

        fun start(context: Context) {
            val appCtx = context.applicationContext
            if (!AutoLedgerListenerStatus.isAutoRecordEnabled(appCtx) ||
                !AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(appCtx)
            ) {
                return
            }
            ensureChannel(appCtx)
            runCatching {
                val intent = Intent(appCtx, LedgerKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            }.onFailure { e ->
                // Android 12+ 在非豁免后台场景禁止 startForegroundService，必须静默降级不崩溃
                Log.w(TAG, "无法在当前后台上下文启动前台保活服务: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            isServiceRunning = false
            runCatching {
                appCtx.stopService(Intent(appCtx, LedgerKeepAliveService::class.java))
            }
        }

        fun refreshNotification(context: Context) {
            if (!isServiceRunning) return
            val appCtx = context.applicationContext
            runCatching {
                ensureChannel(appCtx)
                val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return
                nm.notify(NOTIFICATION_ID, buildNotification(appCtx))
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "保持微信、支付宝及银行动账自动记账监听处于活跃状态"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            nm.createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context): Notification {
            val enabled = AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(context)
            val connected = AutoLedgerListenerStatus.isListenerBinderConnected.value
            val statusText = when {
                !enabled -> "通知读取权未开启 · 点击去设置"
                connected -> "正在后台监听微信、支付宝与银行动账"
                else -> "正在尝试重新连接系统通知监听..."
            }
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            val contentPi = launchIntent?.let {
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                PendingIntent.getActivity(context, NOTIFICATION_ID, it, flags)
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_auto_record)
                .setContentTitle("散页自动记账")
                .setContentText(statusText)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .apply {
                    if (contentPi != null) setContentIntent(contentPi)
                }
                .build()
        }
    }
}
