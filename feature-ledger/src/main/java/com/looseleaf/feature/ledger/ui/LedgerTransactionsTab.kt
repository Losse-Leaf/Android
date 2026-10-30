package com.looseleaf.feature.ledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.core.R
import com.looseleaf.feature.ledger.data.CategoryTotalRow
import com.looseleaf.feature.ledger.data.MonthlyTotalsRow
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
import com.looseleaf.feature.ledger.theme.LedgerFormat
import com.looseleaf.feature.ledger.theme.LedgerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * 账单明细选项卡
 *
 * 遵循自然大地色盘与时间流规范：
 * 1. 月份切换："2026 年 10 月 ▾"，点击调起月份选择，内容区支持横滑切月；
 * 2. 待确认入口（预留）：>0 时显示一行"待确认 N 条 ›"带 6dp 赤陶小点；
 * 3. 本月概览：纯纸色平铺（无卡片无边框），大号等宽本月支出，下一行小字收入与结余，6dp 占比条与图例；
 * 4. 账目列表：按日分组，分组头"10月27日 · 昨天"与"支 ¥50.00"（无重复¥符号）；
 * 5. 条目：6dp 分类色点，第一行商户/备注，第二行时间与渠道，右侧等宽金额；
 * 6. 左滑删除（去掉垃圾桶图标），支持撤销提示；点击编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionsLedgerTab(
    transactions: List<Transaction>,
    monthlyTotals: MonthlyTotalsRow = MonthlyTotalsRow(),
    monthlyCategoryTotals: List<CategoryTotalRow> = emptyList(),
    isPrivacyMasked: Boolean,
    installmentSummary: com.looseleaf.app.model.InstallmentSummary? = null,
    onOpenUpcomingInstallments: () -> Unit = {},
    selectedYearMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpenMonthPicker: () -> Unit,
    onEdit: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onNavigateToKeypad: () -> Unit = {},
    onNavigateToPending: () -> Unit = {},
    listState: LazyListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() },
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors

    // 口径统一与去重计算：分期还款仅手续费计入月支出 (RC-140)
    val expenseTransactions = remember(transactions) { transactions.filter { it.type != "income" } }
    val incomeTransactions = remember(transactions) { transactions.filter { it.type == "income" } }
    val totalExpense = remember(transactions, monthlyTotals) {
        if (transactions.isNotEmpty()) {
            expenseTransactions.sumOf {
                if (it.source == "installment_repay") it.feeAmount else it.amount
            }
        } else {
            monthlyTotals.totalExpense
        }
    }
    val totalIncome = remember(transactions, monthlyTotals) {
        if (transactions.isNotEmpty()) incomeTransactions.sumOf { it.amount } else monthlyTotals.totalIncome
    }
    val monthBalance = totalIncome - totalExpense

    // 分类支出统计
    val categoryTotals = remember(expenseTransactions, monthlyCategoryTotals) {
        if (expenseTransactions.isNotEmpty()) {
            expenseTransactions
                .groupBy { if (it.source == "installment_repay") "利息手续费" else it.category }
                .map { (cat, list) ->
                    val sum = list.sumOf { if (it.source == "installment_repay") it.feeAmount else it.amount }
                    cat to sum
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
        } else {
            monthlyCategoryTotals.map { it.category to it.total }
        }
    }

    // 预留待确认条数计算
    val pendingCount = remember(transactions) {
        transactions.count { it.syncStatus == "PENDING_CREATE" }
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val monthSwipeModifier = Modifier.pointerInput(selectedYearMonth) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (dragOffsetX > 140f) {
                    onMonthChange(selectedYearMonth.minusMonths(1))
                } else if (dragOffsetX < -140f) {
                    onMonthChange(selectedYearMonth.plusMonths(1))
                }
                dragOffsetX = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                dragOffsetX += dragAmount
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
        ) {
            // 1. 待确认入口（预留）：> 0 时展示，= 0 完全不占位
            if (pendingCount > 0) {
                item(key = "pending_confirm_entry") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onNavigateToPending() }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "待确认 $pendingCount 条 ›",
                            style = LedgerTheme.typography.caption,
                            fontFamily = LedgerTheme.typography.serif,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 3. 本月概览（无卡片、无边框，靠间距与一条淡线分隔）
            item(key = "monthly_summary_overview") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(monthSwipeModifier)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "本月支出",
                        style = LedgerTheme.typography.caption,
                        fontFamily = LedgerTheme.typography.serif,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isPrivacyMasked) "¥ ••••" else LedgerFormat.formatMoney(totalExpense),
                        style = LedgerTheme.typography.amountLarge,
                        color = colors.expense
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // 收入与结余一行
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "收入 ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(totalIncome)}",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = colors.income
                        )
                        Text(
                            text = "·",
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                        val balanceColor = colors.textPrimary // 结余为负时也用正文色加负号，不使用红色
                        Text(
                            text = "结余 ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(monthBalance)}",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 12.sp,
                            color = balanceColor
                        )
                    }

                    // 6dp 高分类占比条 + 前 3 个分类图例
                    if (totalExpense > 0 && categoryTotals.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        ) {
                            categoryTotals.forEach { (cat, amt) ->
                                val weight = (amt.toFloat() / totalExpense.toFloat()).coerceAtLeast(0.01f)
                                Box(
                                    modifier = Modifier
                                        .weight(weight)
                                        .fillMaxHeight()
                                        .background(LedgerCategoryColors.colorForCategory(cat, colors.isDark))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 图例：前 3 个分类，其余合并为"其他"
                        val top3 = categoryTotals.take(3)
                        val otherSum = categoryTotals.drop(3).sumOf { it.second }
                        val legendItems = top3 + if (otherSum > 0) listOf("其他" to otherSum) else emptyList()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            legendItems.forEach { (cat, amt) ->
                                val pct = (amt * 100.0 / totalExpense).roundToLong()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(LedgerCategoryColors.colorForCategory(cat, colors.isDark))
                                    )
                                    Text(
                                        text = "$cat $pct%",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 分期概览条目行：仅当存在进行中计划且本月有期次、或有逾期时显示 (RC-140)
                    if (installmentSummary != null && (installmentSummary.thisMonthPaid > 0 || installmentSummary.thisMonthScheduled > 0 || installmentSummary.overdueCount > 0)) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val today = LocalDate.now(FormatUtils.BeijingZone)
                        val nearestDays = runCatching {
                            installmentSummary.nearestDueDate?.let {
                                java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(it))
                            }
                        }.getOrNull()
                        val hasUrgent = installmentSummary.overdueCount > 0 || (nearestDays != null && nearestDays in 0..3)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onOpenUpcomingInstallments() }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hasUrgent) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFC46243)) // 赤陶小点
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "分期",
                                    style = LedgerTheme.typography.body,
                                    fontFamily = LedgerTheme.typography.serif,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "本月已还 ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(installmentSummary.thisMonthPaid)}",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = " · ",
                                    color = colors.textMuted,
                                    fontSize = 12.sp
                                )
                                if (installmentSummary.overdueCount > 0) {
                                    Text(
                                        text = "待还 ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(installmentSummary.thisMonthScheduled + installmentSummary.overdueAmount)} (含逾期)",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = Color(0xFFB33A2B), // 砖红文字
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "待还 ${if (isPrivacyMasked) "••••" else LedgerFormat.formatMoney(installmentSummary.thisMonthScheduled)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }

                            Text(
                                text = "›",
                                fontSize = 16.sp,
                                color = colors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    LedgerDivider()
                }
            }

            // 4. 账目列表：按日分组
            if (transactions.isEmpty()) {
                item(key = "empty_transactions_state") {
                    LedgerEmptyState(
                        title = "这个月还没有账目",
                        actionText = "记一笔",
                        onActionClick = onNavigateToKeypad,
                        modifier = Modifier
                            .padding(vertical = 40.dp)
                            .then(monthSwipeModifier)
                    )
                }
            } else {
                val groupedTransactions = transactions
                    .groupBy { it.occurredAt.take(10) }
                    .toList()
                    .sortedByDescending { it.first }

                groupedTransactions.forEach { (dateString, dayTxs) ->
                    // 日期分组头
                    item(key = "hdr_$dateString") {
                        val parsedDate = runCatching { LocalDate.parse(dateString) }.getOrNull()
                        val today = LocalDate.now(FormatUtils.BeijingZone)
                        val dateLabel = parsedDate?.let { d ->
                            val diff = java.time.temporal.ChronoUnit.DAYS.between(d, today)
                            val rel = when (diff) {
                                0L -> "今天"
                                1L -> "昨天"
                                2L -> "前天"
                                else -> when (d.dayOfWeek) {
                                    DayOfWeek.MONDAY -> "周一"
                                    DayOfWeek.TUESDAY -> "周二"
                                    DayOfWeek.WEDNESDAY -> "周三"
                                    DayOfWeek.THURSDAY -> "周四"
                                    DayOfWeek.FRIDAY -> "周五"
                                    DayOfWeek.SATURDAY -> "周六"
                                    DayOfWeek.SUNDAY -> "周日"
                                    else -> ""
                                }
                            }
                            "${d.monthValue}月${d.dayOfMonth}日 · $rel"
                        } ?: dateString

                        // 日结统计口径调整：排除分期还款本金；全为分期本金则显示"分期 ¥X" (RC-140)
                        val dayRegularExpense = dayTxs.filter { it.type != "income" && it.source != "installment_repay" }.sumOf { it.amount } +
                            dayTxs.filter { it.source == "installment_repay" }.sumOf { it.feeAmount }
                        val dayInstallmentPrincipal = dayTxs.filter { it.source == "installment_repay" }.sumOf { it.principalAmount }
                        val dayIncome = dayTxs.filter { it.type == "income" }.sumOf { it.amount }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = dateLabel,
                                style = LedgerTheme.typography.caption,
                                fontFamily = LedgerTheme.typography.serif,
                                color = colors.textSecondary
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (dayRegularExpense > 0) {
                                    Text(
                                        text = "支 ${LedgerFormat.formatMoney(dayRegularExpense)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                } else if (dayInstallmentPrincipal > 0) {
                                    Text(
                                        text = "分期 ${LedgerFormat.formatMoney(dayInstallmentPrincipal)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                if (dayIncome > 0) {
                                    Text(
                                        text = "收 ${LedgerFormat.formatMoney(dayIncome)}",
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.income
                                    )
                                }
                            }
                        }
                    }

                    // 日期下的条目列表（左滑删除，条目间 1dp 淡线，左缩进 22dp，内边距 12dp）
                    items(
                        items = dayTxs,
                        key = { "tx_${it.id}_${it.syncStatus}" }
                    ) { tx ->
                        LedgerSwipeRevealRow(
                            onDelete = { onDelete(tx) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.background)
                            ) {
                                TransactionRowItem(
                                    tx = tx,
                                    isPrivacyMasked = isPrivacyMasked,
                                    onClick = { onEdit(tx) }
                                )
                                LedgerDivider(modifier = Modifier.padding(start = 22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 账目单行条目
 * - 左侧 6dp 分类色点
 * - 第一行：商户或备注，若无则显示分类名
 * - 第二行：时间 · 账户/渠道（次要色小字，不重复分类名）
 * - 右侧：金额（等宽、右对齐，千分位，保留两位小数）
 * - 上下内边距 12dp
 */
@Composable
private fun TransactionRowItem(
    tx: Transaction,
    isPrivacyMasked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors
    val isIncome = tx.type == "income"
    val isInstallmentRepay = tx.source == "installment_repay"
    val sign = if (isIncome) "+" else "-"
    val displayAmount = if (isPrivacyMasked) "¥ ••••" else "$sign${LedgerFormat.formatMoney(tx.amount)}"
    val colorDot = if (isInstallmentRepay) LedgerCategoryColors.colorForCategory("利息手续费", colors.isDark) else LedgerCategoryColors.colorForCategory(tx.category, colors.isDark)
    val displayTime = remember(tx.occurredAt) { FormatUtils.formatDisplayTime(tx.occurredAt) }

    // 第一行标题：商户或备注，若都没有则显示分类名
    val titleText = remember(tx) {
        val note = tx.note?.trim()
        val title = tx.title.trim()
        when {
            title.isNotBlank() && title != tx.category -> title
            !note.isNullOrBlank() -> note
            else -> tx.category
        }
    }

    // 第二行副文案：分期还款显示 手续费 · 账户名；普通账目显示 时间 · 账户/渠道
    val subtitleText = remember(tx, displayTime) {
        if (isInstallmentRepay) {
            val feePart = if (tx.feeAmount > 0) "手续费 ${LedgerFormat.formatMoney(tx.feeAmount)}" else null
            val accountPart = tx.paymentMethod?.takeIf { it.isNotBlank() }
            listOfNotNull(feePart, accountPart).joinToString(" · ")
        } else {
            val channel = tx.paymentMethod?.takeIf { it.isNotBlank() } ?: "现金"
            if (displayTime.isNotBlank()) "$displayTime · $channel" else channel
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // 6dp 分类色点
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colorDot)
            )
            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = titleText,
                    style = LedgerTheme.typography.body,
                    fontFamily = LedgerTheme.typography.serif,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText,
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 右侧金额：等宽、右对齐
        Text(
            text = displayAmount,
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = TextAlign.End,
            color = if (isIncome) colors.income else colors.expense
        )
    }
}
