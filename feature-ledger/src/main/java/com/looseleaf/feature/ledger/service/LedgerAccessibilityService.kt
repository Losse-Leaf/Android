package com.looseleaf.feature.ledger.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.looseleaf.feature.ledger.data.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 微信 / 支付宝前台支付与转账完成页辅助识别服务 (LedgerAccessibilityService)
 *
 * 为什么需要此可选能力：
 * Android 微信在「零钱转账给好友」「点击接收好友转账（已收钱）」及前台扫码消费时，
 * 系统通知栏不会下发带金额的通知。通过严格限定仅监听微信与支付宝包名的无障碍事件，
 * 仅在明确命中「支付成功 / 转账成功 / 已收钱（近 5 分钟内）」完成页时提取金额与对象，
 * 并交由 [LedgerNotificationListenerService.processParsedBill] 统一去重与落库。
 */
class LedgerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastScanAtMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoLedgerDiagnostics.attachContext(applicationContext)
        AutoLedgerListenerStatus.setAccessibilityConnected(true)
        AutoLedgerDiagnostics.record(
            source = "无障碍辅助",
            outcome = "辅助识别已连接",
            rawDetail = "已就绪：可识别微信/支付宝完成页及零钱转账",
            isRecorded = false,
            context = applicationContext
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AutoLedgerListenerStatus.setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        AutoLedgerListenerStatus.setAccessibilityConnected(true)
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg !in TARGET_PACKAGES) return

        val appCtx = applicationContext
        if (!AutoLedgerListenerStatus.isAutoRecordEnabled(appCtx)) return

        val nowMs = System.currentTimeMillis()
        // 高频内容变化事件按 350ms 节流，窗口切换事件立刻响应
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            nowMs - lastScanAtMs < CONTENT_EVENT_THROTTLE_MS
        ) {
            return
        }
        lastScanAtMs = nowMs

        val collectedTexts = collectWindowTextsForPackage(pkg)
        if (collectedTexts.size < 2) return

        serviceScope.launch {
            try {
                LedgerRepository.ensureInitialized(appCtx)
                val snapshot = LedgerRepository.snapshot.value
                val parsed = AutoLedgerScreenParser.parse(
                    pkg = pkg,
                    rawTexts = collectedTexts,
                    expenseCategories = snapshot.expenseCategories,
                    incomeCategories = snapshot.incomeCategories,
                    expenseCategoryKeywords = snapshot.categoryKeywordsForType("expense"),
                    incomeCategoryKeywords = snapshot.categoryKeywordsForType("income"),
                    learnedExpenseRules = AutoLedgerCategoryMemory.lookupMap(appCtx, "expense"),
                    learnedIncomeRules = AutoLedgerCategoryMemory.lookupMap(appCtx, "income"),
                    nowMs = nowMs
                ) ?: return@launch

                val targetCats = if (parsed.bill.request.type == "income") snapshot.incomeCategories else snapshot.expenseCategories
                LedgerNotificationListenerService.processParsedBill(
                    context = appCtx,
                    pkg = pkg,
                    sbnKey = parsed.screenEventKey,
                    notificationTimestampMs = parsed.eventTimestampMs,
                    bill = parsed.bill,
                    availableCategoriesForNotification = targetCats,
                    nowMs = nowMs,
                    onCreate = { req -> LedgerRepository.createTransactionOptimistic(req) },
                    onEnrich = { localId, req -> LedgerRepository.updateTransactionOptimistic(-localId, req) }
                )
            } catch (e: Exception) {
                Log.e(TAG, "前台转账页面识别失败", e)
                AutoLedgerDiagnostics.record(
                    source = "无障碍辅助",
                    outcome = "识别异常",
                    rawDetail = e.message ?: e.javaClass.simpleName,
                    isRecorded = false,
                    nowMs = nowMs
                )
            }
        }
    }

    override fun onInterrupt() {
        AutoLedgerListenerStatus.setAccessibilityConnected(false)
    }

    override fun onDestroy() {
        AutoLedgerListenerStatus.setAccessibilityConnected(false)
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun collectWindowTextsForPackage(pkg: String): List<String> {
        val collected = ArrayList<String>(48)
        rootInActiveWindow?.let { root ->
            try {
                if (root.packageName?.toString().isNullOrEmpty() || root.packageName?.toString() == pkg) {
                    collectVisibleTexts(root, collected, depth = 0)
                }
            } finally {
                recycleCompat(root)
            }
        }
        if (collected.size >= 2) return collected

        // 若主窗口为空或来自弹窗蒙层，尝试从交互窗口列表定位目标 App 窗口
        val windowList = runCatching { windows }.getOrNull().orEmpty()
        for (win in windowList) {
            val wRoot = runCatching { win.root }.getOrNull() ?: continue
            try {
                if (wRoot.packageName?.toString() != pkg) continue
                collected.clear()
                collectVisibleTexts(wRoot, collected, depth = 0)
                if (collected.size >= 2) break
            } finally {
                recycleCompat(wRoot)
            }
        }
        return collected
    }

    private fun collectVisibleTexts(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        depth: Int
    ) {
        if (depth > MAX_TREE_DEPTH || out.size >= MAX_COLLECTED_NODES) return

        // 绝不读取密码输入框内容
        if (node.isPassword) return

        val childCount = node.childCount
        // 微信/支付宝部分容器节点 isVisibleToUser 可能误报 false，不能直接跳过整棵子树
        if (node.isVisibleToUser || childCount == 0) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            val desc = node.contentDescription?.toString()?.trim()
            if (!desc.isNullOrEmpty() && desc != node.text?.toString()?.trim()) {
                out.add(desc)
            }
        }

        for (i in 0 until childCount) {
            if (out.size >= MAX_COLLECTED_NODES) break
            val child = node.getChild(i) ?: continue
            try {
                collectVisibleTexts(child, out, depth + 1)
            } finally {
                recycleCompat(child)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleCompat(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { node.recycle() }
        }
    }

    companion object {
        private const val TAG = "LedgerAccessibility"
        private const val CONTENT_EVENT_THROTTLE_MS = 350L
        private const val MAX_TREE_DEPTH = 14
        private const val MAX_COLLECTED_NODES = 120

        private val TARGET_PACKAGES = setOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone"
        )
    }
}
