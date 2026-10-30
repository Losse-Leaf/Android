package com.looseleaf.feature.ledger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.feature.ledger.service.LedgerNotificationListenerService
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.launch

/**
 * 账本后台保活与自动记账检查页 (LedgerKeepAliveScreen)
 * 提供通知读取权限、电池优化白名单、国产厂商后台锁检查与一键模拟记账测试。
 */
@Composable
fun LedgerKeepAliveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LedgerTheme.colors

    var isNotificationAccessGranted by remember { mutableStateOf(checkNotificationAccess(context)) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptIgnored(context)) }
    var simulateStatusMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            LedgerTopAppBar(
                title = "自动记账与保活检查",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = colors.textPrimary
                        )
                    }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isAllReady = isNotificationAccessGranted && isBatteryOptIgnored
            StatusOverviewBanner(isAllReady = isAllReady)

            CheckCard(
                title = "通知读取权限 (NotificationListener)",
                desc = "用于实时捕获微信、支付宝、银行 App 发出的支付成功通知并自动提取金额。",
                isPassed = isNotificationAccessGranted,
                actionText = if (isNotificationAccessGranted) "已授权" else "去开启",
                onAction = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )

            CheckCard(
                title = "忽略电池优化 (Doze 白名单)",
                desc = "避免系统在息屏或待机时强制杀死后台记账监听服务，确保即时捕获账单。",
                isPassed = isBatteryOptIgnored,
                actionText = if (isBatteryOptIgnored) "已加入白名单" else "去加入",
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

            SimulationTestCard(
                simulateStatusMessage = simulateStatusMessage,
                onSimulate = {
                    scope.launch {
                        simulateStatusMessage = "正在模拟..."
                        val id = LedgerNotificationListenerService.simulatePayment(
                            title = "微信支付",
                            text = "向商家付款 28.50 元",
                            pkg = "com.tencent.mm"
                        )
                        simulateStatusMessage = if (id != null) {
                            "已成功记入一笔 28.50 元餐饮支出！"
                        } else {
                            "模拟失败，请检查服务配置。"
                        }
                        isNotificationAccessGranted = checkNotificationAccess(context)
                        isBatteryOptIgnored = checkBatteryOptIgnored(context)
                    }
                }
            )

            OemGuideCard()
        }
    }
}

private fun checkNotificationAccess(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}

private fun checkBatteryOptIgnored(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    } else true
}

@Composable
private fun StatusOverviewBanner(isAllReady: Boolean) {
    val colors = LedgerTheme.colors
    val bgColor = if (isAllReady) colors.income.copy(alpha = 0.08f) else colors.expense.copy(alpha = 0.08f)
    val borderColor = if (isAllReady) colors.income.copy(alpha = 0.25f) else colors.borderSubtle
    val icon = if (isAllReady) Icons.Outlined.Check else Icons.Outlined.Warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(ArchiveLines.hairline, borderColor, RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isAllReady) colors.income else colors.accent,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = if (isAllReady) "自动记账环境已就绪" else "尚需开启关键后台权限",
                style = LedgerTheme.typography.subtitle,
                fontFamily = LedgerTheme.typography.serif,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                text = if (isAllReady) "服务正在监听支付通知，消费后将自动录入账本。" else "请完成下列权限授予，以便在后台精准记录每一笔支出。",
                style = LedgerTheme.typography.caption,
                fontFamily = LedgerTheme.typography.serif,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun CheckCard(
    title: String,
    desc: String,
    isPassed: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    val colors = LedgerTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = LedgerTheme.typography.body,
                    fontFamily = LedgerTheme.typography.serif,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isPassed) Icons.Outlined.Check else Icons.Outlined.Close,
                    contentDescription = null,
                    tint = if (isPassed) colors.income else colors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = desc,
                style = LedgerTheme.typography.caption,
                fontFamily = LedgerTheme.typography.serif,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            LedgerTextButton(
                text = actionText,
                enabled = !isPassed,
                onClick = onAction
            )
        }
    }
}

@Composable
private fun SimulationTestCard(
    simulateStatusMessage: String?,
    onSimulate: () -> Unit
) {
    val colors = LedgerTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "功能联调：模拟微信记账",
                style = LedgerTheme.typography.subtitle,
                fontFamily = LedgerTheme.typography.serif,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "无需真实掏出手机付款，点击下方按钮即可模拟一条「微信支付 28.50 元」的通知事件并注入账本。",
                style = LedgerTheme.typography.caption,
                fontFamily = LedgerTheme.typography.serif,
                color = colors.textSecondary
            )
            if (simulateStatusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = simulateStatusMessage,
                    style = LedgerTheme.typography.caption,
                    fontFamily = LedgerTheme.typography.serif,
                    color = colors.accent,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LedgerTextButton(
                text = "模拟记一笔 (28.50 元)",
                onClick = onSimulate
            )
        }
    }
}

@Composable
private fun OemGuideCard() {
    val colors = LedgerTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surface)
            .border(ArchiveLines.hairline, colors.borderSubtle, RoundedCornerShape(4.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = "常见机型后台防杀指南",
                style = LedgerTheme.typography.subtitle,
                fontFamily = LedgerTheme.typography.serif,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OemItem(
                name = "小米 / 澎湃 OS (Xiaomi / HyperOS)",
                steps = "长按多任务卡片锁定 + 设置 -> 应用设置 -> 自启动 -> 允许自启动 + 省电策略设为「无限制」"
            )
            LedgerDivider(modifier = Modifier.padding(vertical = 8.dp))
            OemItem(
                name = "华为 / 荣耀 (EMUI / HarmonyOS / MagicOS)",
                steps = "手机管家 -> 应用启动管理 -> 找到本应用 -> 关闭「自动管理」-> 开启「允许自启动、允许关联启动、允许后台活动」"
            )
            LedgerDivider(modifier = Modifier.padding(vertical = 8.dp))
            OemItem(
                name = "OPPO / 一加 (ColorOS)",
                steps = "多任务界面右上角三点 -> 锁定 + 设置 -> 电池 -> 应用耗电管理 -> 允许唤醒前台与后台运行"
            )
            LedgerDivider(modifier = Modifier.padding(vertical = 8.dp))
            OemItem(
                name = "vivo / iQOO (OriginOS)",
                steps = "多任务下滑加锁 + 设置 -> 电池 -> 后台耗电管理 -> 允许高耗电后台运行"
            )
        }
    }
}

@Composable
private fun OemItem(name: String, steps: String) {
    val colors = LedgerTheme.colors
    Column {
        Text(
            text = name,
            style = LedgerTheme.typography.body,
            fontFamily = LedgerTheme.typography.serif,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = steps,
            style = LedgerTheme.typography.caption,
            fontFamily = LedgerTheme.typography.serif,
            color = colors.textSecondary,
            lineHeight = 18.sp
        )
    }
}
