package com.looseleaf.feature.ledger.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.ui.components.ArchiveDivider
import com.looseleaf.app.ui.components.ArchiveHeaderIconButton
import com.looseleaf.app.ui.components.ArchivePageHeader
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.feature.ledger.service.AutoLedgerCategoryMemory
import com.looseleaf.feature.ledger.service.AutoLedgerDiagnosticEvent
import com.looseleaf.feature.ledger.service.AutoLedgerDiagnostics
import com.looseleaf.feature.ledger.service.AutoLedgerListenerStatus
import com.looseleaf.feature.ledger.service.AutoLedgerParser
import com.looseleaf.feature.ledger.service.LearnedCategoryRule
import com.looseleaf.feature.ledger.service.LedgerAccessibilityService
import com.looseleaf.feature.ledger.service.LedgerKeepAliveService
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动记账与后台保活检查页 (LedgerKeepAliveScreen)
 *
 * - 顶部状态卡：核心两项就绪时为松绿「已就绪」，否则为朱砂「还差 N 步」；
 * - 三张检查卡：通知读取、忽略电池优化、微信/支付宝转账完成页辅助识别（可选推荐）；
 * - 近期检测记录：展示本机内存中的最近捕获与过滤状态，便于排查未识别通知；
 * - 机型指南：常见厂商后台保活指引。
 */
@Composable
fun LedgerKeepAliveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val colors = LedgerTheme.colors
    val coroutineScope = rememberCoroutineScope()

    var isNotificationAccessGranted by remember {
        mutableStateOf(AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(context))
    }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptIgnored(context)) }
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityAccess(context)) }
    var isSmsPermissionGranted by remember { mutableStateOf(checkSmsPermission(context)) }
    var isReconnecting by remember { mutableStateOf(false) }

    val isListenerBinderConnected by AutoLedgerListenerStatus.isListenerBinderConnected.collectAsStateWithLifecycle()
    val isAccessibilityConnected by AutoLedgerListenerStatus.isAccessibilityConnected.collectAsStateWithLifecycle()
    val notificationsSeenCount by AutoLedgerListenerStatus.notificationsSeenCount.collectAsStateWithLifecycle()
    val needsRebindAfterUpdate by AutoLedgerListenerStatus.needsRebindAfterUpdate.collectAsStateWithLifecycle()
    val diagnosticEvents by AutoLedgerDiagnostics.events.collectAsStateWithLifecycle()
    val learnedRules by AutoLedgerCategoryMemory.rulesFlow.collectAsStateWithLifecycle()
    var feedbackNotifEnabled by remember {
        mutableStateOf(AutoLedgerCategoryMemory.isFeedbackNotificationEnabled(context))
    }
    var showAllRules by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            AutoLedgerDiagnostics.ensureLoaded(context)
            AutoLedgerCategoryMemory.getAllRules(context)
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isSmsPermissionGranted = granted || checkSmsPermission(context)
        if (!granted) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isNotificationAccessGranted = AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(context)
        isBatteryOptIgnored = checkBatteryOptIgnored(context)
        isAccessibilityEnabled = checkAccessibilityAccess(context)
        isSmsPermissionGranted = checkSmsPermission(context)
        AutoLedgerListenerStatus.ensureListening(context, allowStartForegroundService = true)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ArchivePageHeader(
                title = "自动记账",
                subtitle = "通知监听、微信转账辅助与保活",
                navigationIcon = {
                    ArchiveHeaderIconButton(
                        onClick = onNavigateBack,
                        contentDescription = "返回",
                        imageVector = FolioGlyphs.ChevronLeft,
                        iconSize = 24.dp
                    )
                }
            )
        },
        containerColor = colors.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val corePending = listOf(isNotificationAccessGranted, isBatteryOptIgnored).count { !it }
            val needsBinderAttention = isNotificationAccessGranted && !isListenerBinderConnected
            StatusOverviewBanner(
                pendingSteps = corePending,
                needsBinderAttention = needsBinderAttention,
                needsRebindAfterUpdate = needsRebindAfterUpdate,
                hasScreenAssist = isAccessibilityEnabled,
                notificationsSeenCount = notificationsSeenCount
            )

            CheckCard(
                index = 1,
                title = "通知读取权限",
                desc = when {
                    !isNotificationAccessGranted ->
                        "实时捕获微信、支付宝、云闪付、银行 App 及动账短信，自动提取金额并跨端去重。"
                    isListenerBinderConnected ->
                        "系统通知监听绑定正常，本次已接收系统通知 $notificationsSeenCount 条（后台保活运行中）。"
                    else ->
                        "系统权限已授权，但后台监听服务暂未建立连接。覆盖更新或系统省电可能会挂起监听，可点击下方「立即重连监听」，若仍未连接请在系统设置里关闭再重开一次。"
                },
                isPassed = isNotificationAccessGranted && isListenerBinderConnected,
                statusText = when {
                    isNotificationAccessGranted && isListenerBinderConnected -> "监听已连接"
                    isNotificationAccessGranted -> "权限已开 · 未连接"
                    else -> null
                },
                statusTint = if (isListenerBinderConnected) colors.income else colors.accent,
                actionText = if (isNotificationAccessGranted) {
                    if (isReconnecting) "重连中..." else "立即重连监听"
                } else {
                    "去开启"
                },
                onAction = {
                    if (!isNotificationAccessGranted) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    } else if (!isReconnecting) {
                        isReconnecting = true
                        coroutineScope.launch {
                            val reconnected = withContext(Dispatchers.IO) {
                                AutoLedgerListenerStatus.forceReconnect(context, bypassCooldown = true)
                            }
                            LedgerKeepAliveService.start(context)
                            delay(1_200L)
                            isReconnecting = false
                            AutoLedgerDiagnostics.record(
                                source = "系统监听",
                                outcome = if (AutoLedgerListenerStatus.isListenerBinderConnected.value) {
                                    "重连成功"
                                } else if (reconnected) {
                                    "已请求系统重绑"
                                } else {
                                    "重绑未成功"
                                },
                                rawDetail = if (AutoLedgerListenerStatus.isListenerBinderConnected.value) {
                                    "通知监听服务已恢复连接"
                                } else {
                                    "若仍显示未连接，请点右侧「去系统重开」关闭后再开启通知使用权"
                                },
                                isRecorded = false
                            )
                        }
                    }
                },
                secondaryActionText = if (needsBinderAttention) "去系统重开" else null,
                onSecondaryAction = if (needsBinderAttention) {
                    {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                } else {
                    null
                }
            )

            CheckCard(
                index = 2,
                title = "忽略电池优化",
                desc = "避免系统在息屏或待机时结束后台监听服务，确保账单即时捕获。",
                isPassed = isBatteryOptIgnored,
                actionText = "去加入白名单",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                }
            )

            CheckCard(
                index = 3,
                title = "微信转账页面辅助识别（推荐）",
                desc = "微信零钱转账与点击接收好友转账（已收钱）不会推送带金额的通知栏消息。开启无障碍辅助后可直接识别微信与支付宝的支付/转账完成页，不读取聊天记录或密码。",
                isPassed = isAccessibilityEnabled,
                statusText = when {
                    !isAccessibilityEnabled -> null
                    isAccessibilityConnected -> "已连接"
                    else -> "已开启 · 等待触发"
                },
                statusTint = colors.income,
                actionText = "去无障碍设置开启",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
                secondaryActionText = if (isAccessibilityEnabled && !isAccessibilityConnected) "检查无障碍开关" else null,
                onSecondaryAction = if (isAccessibilityEnabled && !isAccessibilityConnected) {
                    {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }
                } else {
                    null
                }
            )

            CheckCard(
                index = 4,
                title = "银行动账短信直读（推荐）",
                desc = "农业银行(95599)等入账或扣款短信被系统短信箱折叠、延迟推送时，通过短信广播直读可 100% 捕获入账与扣款，并与银行 App / 支付宝 / 微信通知自动去重合并。",
                isPassed = isSmsPermissionGranted,
                actionText = "授权读取短信",
                onAction = {
                    smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }
            )

            FeedbackNotificationToggleCard(
                enabled = feedbackNotifEnabled,
                onToggle = { next ->
                    feedbackNotifEnabled = next
                    AutoLedgerCategoryMemory.setFeedbackNotificationEnabled(context, next)
                }
            )

            LearnedRulesCard(
                rules = learnedRules,
                expanded = showAllRules,
                onToggleExpand = { showAllRules = !showAllRules },
                onDeleteRule = { rule ->
                    val removed = AutoLedgerCategoryMemory.removeRule(context, rule.txType, rule.matchKey)
                    val sid = removed?.serverId ?: rule.serverId
                    if (sid != null && sid > 0) {
                        coroutineScope.launch(Dispatchers.IO) {
                            runCatching { APIClient.getInstance(context).deleteCategoryRule(sid) }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "近期检测记录（仅存本机）",
                    style = ArchiveTextStyles.overline,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "运行自检",
                    style = ArchiveTextStyles.meta,
                    color = colors.accent,
                    modifier = Modifier
                        .clickable(role = Role.Button) {
                            runSelfDiagnostics(context)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                if (diagnosticEvents.isNotEmpty()) {
                    Text(
                        text = "清空",
                        style = ArchiveTextStyles.meta,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clickable(role = Role.Button) { AutoLedgerDiagnostics.clear() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            DiagnosticsCard(events = diagnosticEvents)

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "常见机型后台防杀指南",
                style = ArchiveTextStyles.overline,
                color = colors.textMuted,
                modifier = Modifier.padding(start = 6.dp)
            )
            OemGuideCard()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun runSelfDiagnostics(context: Context) {
    val notifEnabled = AutoLedgerListenerStatus.isNotificationAccessEnabledInSettings(context)
    val binderConnected = AutoLedgerListenerStatus.isListenerBinderConnected.value
    val seenCount = AutoLedgerListenerStatus.notificationsSeenCount.value
    val accessEnabled = checkAccessibilityAccess(context)
    val accessConnected = AutoLedgerListenerStatus.isAccessibilityConnected.value
    val smsGranted = checkSmsPermission(context)
    val batteryOk = checkBatteryOptIgnored(context)

    val alipayOk = AutoLedgerParser.parse(
        content = "支付宝 账单提醒：你有一笔28.50元的支出",
        pkg = "com.eg.android.AlipayGphone"
    )?.request?.amount == 2850L
    val wechatOk = AutoLedgerParser.parse(
        content = "微信支付：已支付¥36.50",
        pkg = "com.tencent.mm"
    )?.request?.amount == 3650L
    val abcOk = AutoLedgerParser.parse(
        content = "【中国农业银行】您尾号4321账户完成转存交易人民币5000.00，余额15000.00。",
        pkg = "com.android.mms"
    )?.request?.amount == 500000L
    val catCoffee = AutoLedgerParser.parse(
        content = "微信支付 微信支付凭证：你已向星巴克付款36.50元",
        pkg = "com.tencent.mm"
    )?.request?.category == "咖啡饮品"
    val catDrug = AutoLedgerParser.parse(
        content = "支付宝 你已向美团买药付款39.90元",
        pkg = "com.eg.android.AlipayGphone"
    )?.request?.category == "医疗"
    val catTaxi = AutoLedgerParser.parse(
        content = "微信支付 向美团打车付款24.50元",
        pkg = "com.tencent.mm"
    )?.request?.category == "交通"
    val catTelecom = AutoLedgerParser.parse(
        content = "微信支付 向中国移动话费充值100.00元",
        pkg = "com.tencent.mm"
    )?.request?.category == "通讯网费"
    val parserOk = alipayOk && wechatOk && abcOk && catCoffee && catDrug && catTaxi && catTelecom

    val statusSummary = buildString {
        append("通知权=").append(if (notifEnabled) "开" else "关")
        append(" | 监听连接=").append(if (binderConnected) "已连(已收${seenCount}条)" else "未连接")
        append(" | 无障碍=").append(
            when {
                !accessEnabled -> "关"
                accessConnected -> "已连"
                else -> "待激活"
            }
        )
        append(" | 短信=").append(if (smsGranted) "开" else "关")
        append(" | 电池白名单=").append(if (batteryOk) "开" else "关")
        append(" | 解析引擎=").append(if (parserOk) "正常" else "异常")
    }
    AutoLedgerDiagnostics.record(
        source = "本机自检",
        outcome = if (notifEnabled && binderConnected && parserOk) "通道健康" else "待检查连接",
        rawDetail = statusSummary,
        isRecorded = notifEnabled && binderConnected && parserOk
    )
}

private fun checkBatteryOptIgnored(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true
}

private fun checkSmsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun checkAccessibilityAccess(context: Context): Boolean {
    val serviceName = LedgerAccessibilityService::class.java.name
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabledFromManager = runCatching {
        am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { info ->
                val si = info.resolveInfo?.serviceInfo
                si != null && si.packageName == context.packageName && si.name == serviceName
            } == true
    }.getOrDefault(false)
    if (enabledFromManager) return true

    val rawSetting = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    }.getOrNull().orEmpty()
    return rawSetting.contains("${context.packageName}/$serviceName") ||
        rawSetting.contains("${context.packageName}/.service.LedgerAccessibilityService")
}

@Composable
private fun StatusOverviewBanner(
    pendingSteps: Int,
    needsBinderAttention: Boolean,
    needsRebindAfterUpdate: Boolean,
    hasScreenAssist: Boolean,
    notificationsSeenCount: Int
) {
    val colors = LedgerTheme.colors
    val isAllReady = pendingSteps == 0 && !needsBinderAttention
    val tint = if (isAllReady) colors.income else colors.accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.xl)
            .background(if (isAllReady) colors.incomeSoft else colors.accentSoft)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(ArchiveShapes.md)
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            if (isAllReady) {
                Icon(
                    imageVector = FolioGlyphs.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = if (needsBinderAttention) "!" else pendingSteps.toString(),
                    fontFamily = ArchiveFontFamilies.Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                    color = colors.onAccent
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = when {
                    pendingSteps > 0 -> "还差 $pendingSteps 步核心授权"
                    needsBinderAttention -> "通知监听未建立连接"
                    hasScreenAssist -> "自动记账已全面就绪"
                    else -> "通知监听运行中"
                },
                style = ArchiveTextStyles.essayTitle,
                color = colors.textPrimary
            )
            Text(
                text = when {
                    pendingSteps > 0 -> "完成下方核心授权，后台才能稳定捕获每一笔账单。"
                    needsBinderAttention && needsRebindAfterUpdate ->
                        "检测到刚更新过安装包，系统可能挂起了后台监听，点击下方「立即重连监听」或在系统设置中关闭再重开一次。"
                    needsBinderAttention ->
                        "权限已开但监听服务暂未绑定，请点击下方「立即重连监听」恢复连接。"
                    hasScreenAssist ->
                        "通知监听（已收 $notificationsSeenCount 条）与微信转账完成页识别均已就绪。"
                    else ->
                        "通知监听已连接（已收 $notificationsSeenCount 条），建议再开启第 3 项捕获微信零钱转账。"
                },
                style = ArchiveTextStyles.meta,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun CheckCard(
    index: Int,
    title: String,
    desc: String,
    isPassed: Boolean,
    actionText: String,
    onAction: () -> Unit,
    statusText: String? = if (isPassed) "已开启" else null,
    statusTint: androidx.compose.ui.graphics.Color = LedgerTheme.colors.income,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    val colors = LedgerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.leaf)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isPassed) colors.income else colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (isPassed) {
                    Icon(
                        imageVector = FolioGlyphs.Check,
                        contentDescription = "已完成",
                        tint = colors.onAccent,
                        modifier = Modifier.size(15.dp)
                    )
                } else {
                    Text(
                        text = index.toString(),
                        style = ArchiveTextStyles.meta,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = ArchiveTextStyles.rowTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (statusText != null) {
                Text(
                    text = statusText,
                    style = ArchiveTextStyles.meta,
                    fontWeight = FontWeight.Medium,
                    color = statusTint
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            style = ArchiveTextStyles.body,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 38.dp)
        )
        if (!isPassed || secondaryActionText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(start = 38.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isPassed) {
                    Box(
                        modifier = Modifier
                            .clip(ArchiveShapes.full)
                            .background(colors.accent)
                            .clickable(role = Role.Button, onClick = onAction)
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = actionText,
                            style = ArchiveTextStyles.label,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onAccent
                        )
                    }
                }
                if (secondaryActionText != null && onSecondaryAction != null) {
                    Box(
                        modifier = Modifier
                            .clip(ArchiveShapes.full)
                            .background(colors.surfaceElevated)
                            .clickable(role = Role.Button, onClick = onSecondaryAction)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = secondaryActionText,
                            style = ArchiveTextStyles.label,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(events: List<AutoLedgerDiagnosticEvent>) {
    val colors = LedgerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.leaf)
    ) {
        if (events.isEmpty()) {
            Text(
                text = "本次启动后暂未收到支付或转账捕获记录。",
                style = ArchiveTextStyles.meta,
                color = colors.textMuted,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            events.take(8).forEachIndexed { i, ev ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${ev.timestampText} · ${ev.source}",
                            style = ArchiveTextStyles.meta,
                            color = colors.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = ev.outcome,
                            style = ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = if (ev.isRecorded) colors.income else colors.textSecondary
                        )
                    }
                    if (ev.detail.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = ev.detail,
                            style = ArchiveTextStyles.body,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (i < minOf(events.size, 8) - 1) {
                    ArchiveDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun OemGuideCard() {
    val colors = LedgerTheme.colors
    val guides = listOf(
        "小米 / 澎湃 OS" to "多任务卡片长按锁定；设置 / 应用设置 / 自启动，允许自启动；省电策略设为「无限制」",
        "华为 / 荣耀" to "手机管家 / 应用启动管理，找到本应用，关闭「自动管理」，开启自启动、关联启动与后台活动",
        "OPPO / 一加" to "多任务界面右上角菜单中锁定；设置 / 电池 / 应用耗电管理，允许前后台运行",
        "vivo / iQOO" to "多任务界面下滑加锁；设置 / 电池 / 后台耗电管理，允许高耗电后台运行"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.leaf)
    ) {
        guides.forEachIndexed { i, (name, steps) ->
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = name,
                    style = ArchiveTextStyles.label,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = steps,
                    style = ArchiveTextStyles.meta.copy(lineHeight = 19.sp),
                    color = colors.textSecondary
                )
            }
            if (i < guides.lastIndex) {
                ArchiveDivider(modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun FeedbackNotificationToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val colors = LedgerTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.leaf)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "自动记账分类提醒通知",
                style = ArchiveTextStyles.rowTitle,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "每次自动入账后发一条无声通知，可直接在通知栏点选一键改分类并记住该商户。",
                style = ArchiveTextStyles.meta.copy(lineHeight = 18.sp),
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun LearnedRulesCard(
    rules: List<LearnedCategoryRule>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDeleteRule: (LearnedCategoryRule) -> Unit
) {
    val colors = LedgerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.leaf)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "已学习商户归类规则（${rules.size} 条）",
                    style = ArchiveTextStyles.rowTitle,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (rules.isEmpty()) "修改任一商户分类后，系统会自动记住并同步跨端" else "点击展开查看或删除已记住的商户分类映射",
                    style = ArchiveTextStyles.meta,
                    color = colors.textSecondary
                )
            }
            Icon(
                imageVector = if (expanded) FolioGlyphs.ChevronUp else FolioGlyphs.ChevronDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded && rules.isNotEmpty()) {
            ArchiveDivider()
            val visibleRules = rules.take(30)
            visibleRules.forEachIndexed { idx, rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.displayName,
                            style = ArchiveTextStyles.label,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${if (rule.txType == "income") "收入" else "支出"} · 归类为「${rule.category}」· 命中 ${rule.hitCount} 次",
                            style = ArchiveTextStyles.meta,
                            color = colors.textSecondary
                        )
                    }
                    Text(
                        text = "移除",
                        style = ArchiveTextStyles.meta,
                        color = colors.alertRed,
                        modifier = Modifier
                            .clickable(role = Role.Button) { onDeleteRule(rule) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (idx < visibleRules.lastIndex) {
                    ArchiveDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}
