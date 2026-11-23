package com.looseleaf.feature.ledger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.ui.components.ArchiveDivider
import com.looseleaf.app.ui.components.ArchiveHeaderIconButton
import com.looseleaf.app.ui.components.ArchivePageHeader
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.feature.ledger.theme.LedgerTheme

/**
 * 自动记账与后台保活检查页 (LedgerKeepAliveScreen)
 *
 * - 顶部状态卡：两项就绪时为松绿「已就绪」，否则为朱砂「还差 N 步」；
 * - 两张检查卡：通知读取、电池优化白名单，右侧为状态印记，未通过时出现朱砂胶囊行动按钮；
 * - 机型指南：一张纸页卡片，四个厂商分段说明。
 * 回到前台时自动重新检测，用户从系统设置返回后状态立即刷新。
 */
@Composable
fun LedgerKeepAliveScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 该页是在账本内 return 出来的全屏子页，不在路由栈里，必须自己拦返回键
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val colors = LedgerTheme.colors

    var isNotificationAccessGranted by remember { mutableStateOf(checkNotificationAccess(context)) }
    var isBatteryOptIgnored by remember { mutableStateOf(checkBatteryOptIgnored(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isNotificationAccessGranted = checkNotificationAccess(context)
        isBatteryOptIgnored = checkBatteryOptIgnored(context)
    }

    val scrollState = rememberScrollState()

    Scaffold(
        // 外层已让过系统栏，内层清零避免再吃一条导航栏高度
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ArchivePageHeader(
                title = "自动记账",
                subtitle = "通知监听与后台保活",
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
            val pending = listOf(isNotificationAccessGranted, isBatteryOptIgnored).count { !it }
            StatusOverviewBanner(pendingSteps = pending)

            CheckCard(
                index = 1,
                title = "通知读取权限",
                desc = "实时捕获微信、支付宝、银行 App 的支付成功通知，并自动提取金额。",
                isPassed = isNotificationAccessGranted,
                actionText = "去开启",
                onAction = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
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

            Spacer(modifier = Modifier.height(8.dp))
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
private fun StatusOverviewBanner(pendingSteps: Int) {
    val colors = LedgerTheme.colors
    val isAllReady = pendingSteps == 0
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
                    text = pendingSteps.toString(),
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
                text = if (isAllReady) "自动记账已就绪" else "还差 $pendingSteps 步",
                style = ArchiveTextStyles.essayTitle,
                color = colors.textPrimary
            )
            Text(
                text = if (isAllReady) "正在监听支付通知，消费后会自动录入账本。" else "完成下面的授权，后台才能稳定记下每一笔。",
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
    onAction: () -> Unit
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
            if (isPassed) {
                Text(
                    text = "已开启",
                    style = ArchiveTextStyles.meta,
                    fontWeight = FontWeight.Medium,
                    color = colors.income
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
        if (!isPassed) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .padding(start = 38.dp)
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
