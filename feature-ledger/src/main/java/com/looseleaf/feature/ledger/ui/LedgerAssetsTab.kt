package com.looseleaf.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
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
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors

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
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))
                LedgerDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "流动资金",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.liquidBalance),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "固定资产",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.fixedAssets),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "待还信贷负债",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.totalLiabilities),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.expense
                        )
                    }
                }

                if (ov.liquidBalance < 0 && ov.initialLiquidBalance == 0L) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "尚未校准余额，数字可能不准",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary
                        )
                        LedgerTextButton(
                            text = "去校准",
                            onClick = onCalibrateBalanceClick
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                LedgerDivider()
            }
        }

        // 2. 信贷渠道与分期还款负债
        item(key = "credit_channels_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SectionHeader(
                    title = "信贷渠道与分期负债",
                    actionText = "新建分期",
                    onActionClick = onNewInstallmentClick
                )

                if (ov.monthlyLiabilities > 0) {
                    Text(
                        text = "本月待还 ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(ov.monthlyLiabilities)}",
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (installmentPlans.isEmpty()) {
                    Text(
                        text = "暂无未结清信贷负债",
                        style = LedgerTheme.typography.body,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    val sortedPlans = installmentPlans.sortedBy { it.nextDueDate ?: "9999-99-99" }
                    sortedPlans.forEach { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onPlanClick(plan) }
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = plan.title,
                                    style = LedgerTheme.typography.body,
                                    fontFamily = LedgerTheme.typography.serif,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary
                                )
                                if (plan.isOverdue) {
                                    Text(
                                        text = "已逾期 ${plan.overdueDays} 天",
                                        fontFamily = LedgerTheme.typography.serif,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFFB33A2B),
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
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(plan.remainingPrincipal),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.expense
                                )
                                Text(
                                    text = "本期: ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(plan.nextTotal)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // 区块底部的文字按钮"管理渠道"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    LedgerTextButton(
                        text = "管理渠道",
                        onClick = onManageChannelsClick
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                LedgerDivider()
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
                    title = "在役固资与随身装备 (${ov.gearSummary.items.size})",
                    actionText = "添加装备",
                    onActionClick = onAddGearClick
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (ov.gearSummary.items.isEmpty()) {
                    Text(
                        text = "暂无装备记录",
                        style = LedgerTheme.typography.body,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                } else {
                    ov.gearSummary.items.forEach { gear ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSellGearClick(gear) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = gear.name,
                                    style = LedgerTheme.typography.body,
                                    fontFamily = LedgerTheme.typography.serif,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        gear.category,
                                        gear.spec.takeIf { it.isNotBlank() },
                                        gear.purchaseDate
                                    ).joinToString(" · "),
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = if (isPrivacyMasked) "••••" else gear.priceCents?.let { LedgerFormat.formatMoney(it) } ?: "—",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
