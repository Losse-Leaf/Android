package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.theme.SpectrumColors
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import java.time.YearMonth
import kotlin.math.roundToLong
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import com.looseleaf.app.ui.components.PaperSkeleton
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import com.looseleaf.app.data.sync.SyncManager
import androidx.compose.ui.res.painterResource
/**
 * 账单明细选项卡：月份切换、月度指标看板、分类光谱比例条、流水明细卡片
 */
@Composable
internal fun TransactionsLedgerTab(
    transactions: List<Transaction>,
    isPrivacyMasked: Boolean,
    /** MOB-P2-44：同步中时用纸墨骨架代替「暂无记账明细」 */
    isRefreshing: Boolean = false,
    selectedYearMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    // 过滤属于选中年月的明细
    val monthTransactions = remember(transactions, selectedYearMonth) {
        val targetPrefix = String.format(java.util.Locale.US, "%04d-%02d", selectedYearMonth.year, selectedYearMonth.monthValue)
        val targetDotPrefix = String.format(java.util.Locale.US, "%04d.%02d", selectedYearMonth.year, selectedYearMonth.monthValue)
        transactions.filter { tx ->
            tx.occurredAt.startsWith(targetPrefix) || tx.occurredAt.startsWith(targetDotPrefix)
        }
    }

    val totalExpense = remember(monthTransactions) {
        monthTransactions.filter { it.type == "expense" }.sumOf { it.amount }
    }
    val totalIncome = remember(monthTransactions) {
        monthTransactions.filter { it.type == "income" }.sumOf { it.amount }
    }
    val monthBalance = totalIncome - totalExpense

    // 分类支出占比
    val categoryBreakdown = remember(monthTransactions) {
        monthTransactions.filter { it.type == "expense" }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp)
    ) {
        // 月份选择控制行
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { onMonthChange(selectedYearMonth.minusMonths(1)) }) {
                    Icon(painter = painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.ledger_transactions_tab_contentDescription_01), tint = colors.inkPrimary)
                }

                Text(
                    text = stringResource(R.string.ledger_transactions_tab_text_tpl_01, selectedYearMonth.year, selectedYearMonth.monthValue),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    color = colors.inkPrimary
                )

                IconButton(onClick = { onMonthChange(selectedYearMonth.plusMonths(1)) }) {
                    Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.ledger_transactions_tab_contentDescription_02), tint = colors.inkPrimary)
                }
            }
        }

        // 月度汇总统计看板卡片
        item {
            PaperCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("本月总支出", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(totalExpense),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = colors.accentWarm
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("本月总收入", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(totalIncome),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = colors.accentMoss
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("月收支结余", fontSize = 11.sp, color = colors.inkMuted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPrivacyMasked) "****" else FormatUtils.centsToYuanString(monthBalance),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = if (monthBalance >= 0) colors.inkPrimary else colors.accentWarm
                            )
                        }
                    }

                    // 分类支出光谱条 (Spectrum Bar)
                    if (totalExpense > 0 && categoryBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        PaperDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        // 多彩比例横条
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        ) {
                            categoryBreakdown.forEach { (cat, amt) ->
                                val weight = (amt.toFloat() / totalExpense.toFloat()).coerceAtLeast(0.01f)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(SpectrumColors.colorForCategory(cat, LooseLeafTheme.colors.isDark))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 前 4 大类目图例
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            categoryBreakdown.take(5).forEach { (cat, amt) ->
                                val pct = (amt * 100.0 / totalExpense).roundToLong()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(SpectrumColors.colorForCategory(cat, LooseLeafTheme.colors.isDark))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.ledger_transactions_tab_text_tpl_02, cat, pct),
                                        fontSize = 11.sp,
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        color = colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 记账明细列表
        if (monthTransactions.isEmpty()) {
            item {
                if (isRefreshing) {
                    // MOB-P2-44：同步中给纸墨骨架，而不是直接写「暂无」
                    PaperSkeleton(
                        modifier = Modifier.padding(vertical = 24.dp),
                        lines = 4,
                        lineHeight = 14.dp
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ledger_transactions_tab_text_01),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        } else {
            items(monthTransactions, key = { it.id }) { tx ->
                val isIncome = tx.type == "income"
                val sign = if (isIncome) "+" else "-"
                val displayAmount = if (isPrivacyMasked) "****" else "$sign${FormatUtils.centsToYuanString(tx.amount)}"
                val spectrum = SpectrumColors.colorForCategory(tx.category, LooseLeafTheme.colors.isDark)

                PaperCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(tx) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(spectrum)
                                    )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tx.title,
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.inkPrimary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (tx.isInstallment == true) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(colors.accentWarm.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        val channel = when (tx.installmentChannelCode) {
                                            "huabei" -> "花呗"
                                            "baitiao" -> "白条"
                                            else -> tx.installmentChannelCode?.takeIf { it.isNotBlank() } ?: "分期"
                                        }
                                        val term = if ((tx.installmentMonths ?: 0) > 0) "${tx.installmentMonths}期" else ""
                                        Text(
                                            text = listOfNotNull(channel, term).filter { it.isNotBlank() }.joinToString(" "),
                                            fontSize = 11.sp,
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            color = colors.accentWarm,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                if (tx.syncStatus != SyncManager.STATUS_SYNCED) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentWarm)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = listOfNotNull(
                                    FormatUtils.formatDisplayDateTime(tx.occurredAt),
                                    tx.category,
                                    tx.paymentMethod
                                ).joinToString(" · "),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )

                            if (tx.isInstallment == true) {
                                Spacer(modifier = Modifier.height(3.dp))
                                val totalAmt = tx.installmentTotalAmount?.takeIf { it > 0 } ?: tx.amount
                                val remain = tx.installmentRemainMonths ?: tx.installmentMonths
                                val totalMonths = tx.installmentMonths ?: 12
                                val day = tx.repaymentDay ?: 10
                                // MOB-P1-75：中英混排不再整串塞进等宽字体 ——
                                // MonoScale（FontFamily.Monospace）遇到中文会回退到比例中文字体，
                                // 整行因此失去等宽网格、金额列无法对齐。
                                // 这里把「中文」与「数字」分段：中文用无衬线，数字用等宽并开 tnum。
                                Text(
                                    text = buildAnnotatedString {
                                        append("总额 ")
                                        withStyle(SpanStyle(fontFamily = ArchiveFontFamilies.MonoScale)) {
                                            append(FormatUtils.centsToYuanString(totalAmt))
                                        }
                                        append(" · 剩 ")
                                        withStyle(SpanStyle(fontFamily = ArchiveFontFamilies.MonoScale)) {
                                            append("$remain/$totalMonths")
                                        }
                                        append(" 期 · 每月 ")
                                        withStyle(SpanStyle(fontFamily = ArchiveFontFamilies.MonoScale)) {
                                            append("$day")
                                        }
                                        append(" 日还款")
                                    },
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 11.sp,
                                    color = colors.accentWarm
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayAmount,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = if (isIncome) colors.accentMoss else colors.accentWarm
                            )

                            IconButton(
                                onClick = { onDelete(tx) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.ledger_transactions_tab_contentDescription_03),
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
