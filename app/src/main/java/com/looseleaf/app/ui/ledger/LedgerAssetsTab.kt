package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
/**
 * 资产大盘选项卡：净资产卡片、资金底数校准、信贷负债大盘、在役装备库存
 */
@Composable
internal fun AssetsAndGearTab(
    overview: AssetsOverview?,
    isLoading: Boolean,
    isPrivacyMasked: Boolean,
    onCalibrateBalanceClick: () -> Unit,
    onAddGearClick: () -> Unit,
    onSellGearClick: (GearItem) -> Unit,
    onManageChannelsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (isLoading && overview == null) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
        }
        return
    }

    val ov = overview ?: AssetsOverview()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp)
    ) {
        // 1. 核心净资产透视卡片
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "个人核心资产大盘",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkMuted
                        )
                        TextButton(onClick = onCalibrateBalanceClick) {
                            Text("校准资金底数", fontSize = 11.sp, color = colors.accentWarm)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.netWorth),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 32.sp,
                        color = colors.inkPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    PaperDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("流动可用资金", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.liquidBalance),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.accentMoss
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("在役固定资产", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.fixedAssets),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("待还信贷负债", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(ov.totalLiabilities),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.accentWarm
                            )
                        }
                    }
                }
            }
        }

        // 2. 信贷渠道与分期还款负债
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "信贷渠道与分期负债",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = colors.inkPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            TextButton(
                                onClick = onManageChannelsClick,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("管理渠道", fontSize = 11.5.sp, color = colors.accentWarm)
                            }
                        }
                        if (ov.monthlyLiabilities > 0) {
                            Text(
                                text = "月供 ${FormatUtils.centsToYuanString(ov.monthlyLiabilities)}",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (ov.channelLiabilities.isEmpty() || ov.channelLiabilities.all { it.totalDebt <= 0 }) {
                        Text(
                            text = "暂无未清还信贷分期，资金健康。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        ov.channelLiabilities.filter { it.totalDebt > 0 }.forEach { ch ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = ch.channelName,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "每月 ${ch.repaymentDay} 号还款 (距还款日 ${ch.daysUntilRepayment} 天)",
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FormatUtils.centsToYuanString(ch.totalDebt),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 13.sp,
                                        color = colors.accentWarm
                                    )
                                    Text(
                                        text = "月供 ${FormatUtils.centsToYuanString(ch.monthlyPay)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. 在役固资与随身装备库
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "在役固资装备 (${ov.gearSummary.items.size})",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.inkPrimary
                        )
                        TextButton(onClick = onAddGearClick) {
                            Text("+ 登记新装备", fontSize = 11.sp, color = colors.accentWarm)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (ov.gearSummary.items.isEmpty()) {
                        Text(
                            text = "暂无录入固资装备",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        ov.gearSummary.items.forEach { gear ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onSellGearClick(gear) }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = gear.name,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            gear.category,
                                            gear.spec.takeIf { it.isNotBlank() },
                                            gear.purchaseDate
                                        ).joinToString(" · "),
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = gear.priceCents?.let { FormatUtils.centsToYuanString(it) } ?: "—",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "出清",
                                        fontSize = 10.sp,
                                        color = colors.accentWarm,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.accentWarm.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
