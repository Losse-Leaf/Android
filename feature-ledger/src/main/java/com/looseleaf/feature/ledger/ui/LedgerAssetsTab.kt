package com.looseleaf.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.ui.components.ArchiveGearRow
import com.looseleaf.feature.ledger.theme.LedgerFormat
import com.looseleaf.feature.ledger.theme.LedgerTheme

/**
 * 资产大盘选项卡
 *
 * 遵循设计系统规范：
 * - 负数一律正文色加负号，不用苔绿（含流动资金）
 * - 统一使用 SectionHeader
 * - 固资条目去掉"退役/出二手"外露按钮，点击进入详情
 * - 不显示 FAB，底部覆盖底栏
 */
@Composable
internal fun AssetsAndGearTab(
    overview: AssetsOverview?,
    isLoading: Boolean,
    isPrivacyMasked: Boolean,
    installmentPlans: List<InstallmentPlanItemSummary> = emptyList(),
    onCalibrateBalanceClick: () -> Unit,
    onAddGearClick: () -> Unit,
    onSellGearClick: (GearItem) -> Unit,
    onManageChannelsClick: () -> Unit,
    onNewInstallmentClick: () -> Unit = {},
    onPlanClick: (InstallmentPlanItemSummary) -> Unit = {},
    /** 从展台「随行装备」跳进来时要定位的器物（RG-30） */
    focusGearId: String? = null,
    onFocusConsumed: (() -> Unit)? = null,
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors

    // 装备区是本列表最后一个 section（前面是净资产、信贷渠道）；
    // 首帧 totalItemsCount 还是 0，等一帧再滚，滚不到也不影响主流程。
    LaunchedEffect(focusGearId) {
        if (focusGearId == null) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        runCatching {
            val last = listState.layoutInfo.totalItemsCount - 1
            if (last >= 0) listState.animateScrollToItem(last)
        }
        onFocusConsumed?.invoke()
    }

    if (isLoading && overview == null) {
        Box(
            contentAlignment = Alignment.TopStart,
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerSkeleton(modifier = Modifier.fillMaxWidth().height(140.dp))
                LedgerSkeleton(modifier = Modifier.fillMaxWidth().height(100.dp))
                LedgerSkeleton(modifier = Modifier.fillMaxWidth().height(160.dp))
            }
        }
        return
    }

    val ov = overview ?: AssetsOverview()
    var showInstallmentOverviewSheet by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
    ) {
        // 1. 核心净资产透视
        item(key = "net_worth_overview_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SectionHeader(
                    title = "净资产总览",
                    actionText = "校准余额",
                    onActionClick = onCalibrateBalanceClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isPrivacyMasked) "¥ ••••" else LedgerFormat.formatMoney(ov.netWorth),
                    style = TextStyle(
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontWeight = FontWeight.Bold,
                        fontSize = ArchiveTypeScale.display,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (!isPrivacyMasked && ov.netWorth < 0) colors.alertRed else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))
                LedgerDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "流动资金",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.liquidBalance),
                            style = TextStyle(
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ArchiveTypeScale.body,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (!isPrivacyMasked && ov.liquidBalance < 0) colors.alertRed else colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "固定资产",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.fixedAssets),
                            style = TextStyle(
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ArchiveTypeScale.body,
                                fontFeatureSettings = "tnum"
                            ),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "待还负债",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.totalLiabilities),
                            style = TextStyle(
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ArchiveTypeScale.body,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (!isPrivacyMasked && ov.totalLiabilities > 0) colors.alertRed else colors.expense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                LedgerDivider()
            }
        }

        // 2. 分期负债汇总卡片（整块可点击呼出明细底抽）
        item(key = "credit_channels_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SectionHeader(
                    title = "分期负债",
                    actionText = "+ 新建",
                    onActionClick = onNewInstallmentClick
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.medium)
                        .background(colors.surfaceElevated)
                        .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.medium)
                        .clickable(role = Role.Button) { showInstallmentOverviewSheet = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "待还总额",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.totalLiabilities),
                            style = TextStyle(
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ArchiveTypeScale.body,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (!isPrivacyMasked && ov.totalLiabilities > 0) colors.alertRed else colors.expense,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "当月待还",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.monthlyLiabilities),
                            style = TextStyle(
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = ArchiveTypeScale.body,
                                fontFeatureSettings = "tnum"
                            ),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        modifier = Modifier.weight(0.95f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "在还分期",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (installmentPlans.isEmpty()) "暂无欠款" else "${installmentPlans.size} 笔在还",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = ArchiveTypeScale.body,
                            color = if (installmentPlans.isEmpty()) colors.textSecondary else colors.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "›",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = ArchiveTypeScale.title,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // 3. 在役固资与随身装备库
        item(key = "gear_inventory_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SectionHeader(
                    title = "装备 ${ov.gearSummary.items.size}",
                    actionText = "+ 添加",
                    onActionClick = onAddGearClick
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (ov.gearSummary.items.isEmpty()) {
                    Text(
                        text = "暂无装备记录",
                        style = LedgerTheme.typography.body,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    ov.gearSummary.items.forEachIndexed { idx, gear ->
                        if (idx > 0) {
                            LedgerDivider()
                        }
                        // 从展台跳进来时高亮目标器物，用户滚到装备区一眼能看到
                        val isFocused = focusGearId != null && gear.id == focusGearId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFocused) {
                                        Modifier.background(colors.accent.copy(alpha = 0.08f))
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            ArchiveGearRow(
                                gear = gear,
                                trailingText = if (isPrivacyMasked) {
                                    "••••"
                                } else {
                                    gear.priceCents?.let { LedgerFormat.formatMoney(it) } ?: "—"
                                },
                                trailingColor = colors.textPrimary,
                                showShortDateInSubtitle = true,
                                onClick = { onSellGearClick(gear) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInstallmentOverviewSheet) {
        LedgerSheet(
            onDismissRequest = { showInstallmentOverviewSheet = false },
            title = "分期与信贷明细",
            cancelText = "关闭"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 顶部操作栏：新建分期 & 管理渠道
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedgerTextButton(
                        text = "新建分期",
                        onClick = {
                            showInstallmentOverviewSheet = false
                            onNewInstallmentClick()
                        }
                    )
                    LedgerTextButton(
                        text = "管理渠道",
                        onClick = {
                            showInstallmentOverviewSheet = false
                            onManageChannelsClick()
                        }
                    )
                }

                LedgerDivider()

                if (installmentPlans.isEmpty()) {
                    Text(
                        text = "暂无未结清信贷负债",
                        style = LedgerTheme.typography.body,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    val sortedPlans = installmentPlans.sortedBy { it.nextDueDate ?: "9999-99-99" }
                    sortedPlans.forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ArchiveShapes.small)
                                .clickable {
                                    showInstallmentOverviewSheet = false
                                    onPlanClick(plan)
                                }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = plan.title,
                                    style = LedgerTheme.typography.body,
                                    fontFamily = LedgerTheme.typography.serif,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                if (plan.isOverdue) {
                                    Text(
                                        text = "已逾期 ${plan.overdueDays} 天",
                                        fontFamily = LedgerTheme.typography.serif,
                                        fontSize = ArchiveTypeScale.caption,
                                        color = colors.alertRed,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    val nextDue = plan.nextDueDate ?: ""
                                    val nextDueDisplay = if (nextDue.length >= 10) {
                                        "${nextDue.substring(5, 7).toIntOrNull() ?: 0}月${nextDue.substring(8, 10).toIntOrNull() ?: 0}日"
                                    } else {
                                        nextDue
                                    }
                                    Text(
                                        text = "${plan.channelName ?: plan.channelCode} · 第 ${plan.paidPeriods + 1}/${plan.periods} 期 · 下次 $nextDueDisplay",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = ArchiveTypeScale.caption,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(plan.remainingPrincipal),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = ArchiveTypeScale.body,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.expense
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "本期: ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(plan.nextTotal)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = ArchiveTypeScale.caption,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        LedgerDivider()
                    }
                }
            }
        }
    }
}
