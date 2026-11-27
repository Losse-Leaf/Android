package com.looseleaf.feature.ledger.ui

import androidx.compose.animation.animateColorAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.Transaction
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.ui.components.ArchiveDivider
import com.looseleaf.app.ui.components.ArchiveEmptyState
import com.looseleaf.app.ui.components.ArchiveSwipeRevealRow
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.feature.ledger.theme.LedgerCategoryColors
import com.looseleaf.feature.ledger.theme.LedgerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

private const val MASK = "••••"

internal data class MonthlyLedgerSummary(
    val totalIncome: Long,
    val totalExpense: Long,
    val balance: Long,
    val categoryTotals: List<Pair<String, Long>>
)

internal fun isIncomeTransaction(tx: Transaction): Boolean =
    tx.type.trim().equals("income", ignoreCase = true)

internal fun isExpenseTransaction(tx: Transaction): Boolean {
    val t = tx.type.trim()
    return t.isEmpty() || t.equals("expense", ignoreCase = true)
}

internal fun computeMonthlyLedgerSummary(transactions: List<Transaction>): MonthlyLedgerSummary {
    val incomeTransactions = transactions.filter { isIncomeTransaction(it) }
    val expenseTransactions = transactions.filter { isExpenseTransaction(it) }
    val totalIncome = incomeTransactions.sumOf { it.amount }
    val totalExpense = expenseTransactions.sumOf {
        if (it.source == "installment_repay") it.feeAmount else it.amount
    }
    val categoryTotals = expenseTransactions
        .groupBy { if (it.source == "installment_repay") "利息手续费" else it.category }
        .map { (cat, list) ->
            val sum = list.sumOf { if (it.source == "installment_repay") it.feeAmount else it.amount }
            cat to sum
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
    return MonthlyLedgerSummary(
        totalIncome = totalIncome,
        totalExpense = totalExpense,
        balance = totalIncome - totalExpense,
        categoryTotals = categoryTotals
    )
}

/**
 * 账单明细选项卡
 *
 * 1. 待确认入口：> 0 时显示一枚朱砂淡底胶囊「待确认 N 条」；
 * 2. 本月总览：一张纸页卡片 —— 小标、衬线英雄数字（本月结余）、收入 / 支出双栏、
 *    带间隙的支出分布占比条与图例；有分期时卡片底部追加一行分期摘要；
 *    整张卡片支持横滑切月；
 * 3. 账目列表：按日分组，组头为「日期 · 相对日」与当日收支；
 * 4. 条目：分类首字方印 + 标题 / 时间与渠道 + 右侧等宽金额；左滑删除，点击编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionsLedgerTab(
    transactions: List<Transaction>,
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
    val summary = remember(transactions) { computeMonthlyLedgerSummary(transactions) }
    val totalExpense = summary.totalExpense
    val totalIncome = summary.totalIncome
    val monthBalance = summary.balance
    val categoryTotals = summary.categoryTotals

    // 预留待确认条数计算
    val pendingCount = remember(transactions) {
        transactions.count { it.syncStatus == "PENDING_CREATE" }
    }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val monthSwipeThresholdPx = with(LocalDensity.current) { 140.dp.toPx() }
    // 视觉位移与判定位移分离：卡片以 0.35 的阻尼跟手，越过阈值时给一次轻触感，松手阻尼回位
    val swipeScope = rememberCoroutineScope()
    val swipeVisual = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    var thresholdArmed by remember { mutableStateOf(false) }
    val monthSwipeModifier = Modifier.pointerInput(selectedYearMonth, monthSwipeThresholdPx) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (dragOffsetX > monthSwipeThresholdPx) {
                    onMonthChange(selectedYearMonth.minusMonths(1))
                } else if (dragOffsetX < -monthSwipeThresholdPx) {
                    onMonthChange(selectedYearMonth.plusMonths(1))
                }
                dragOffsetX = 0f
                thresholdArmed = false
                swipeScope.launch { swipeVisual.animateTo(0f, ArchiveMotion.glide()) }
            },
            onDragCancel = {
                dragOffsetX = 0f
                thresholdArmed = false
                swipeScope.launch { swipeVisual.animateTo(0f, ArchiveMotion.glide()) }
            },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                dragOffsetX += dragAmount
                val beyond = kotlin.math.abs(dragOffsetX) > monthSwipeThresholdPx
                if (beyond != thresholdArmed) {
                    thresholdArmed = beyond
                    if (beyond) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                swipeScope.launch { swipeVisual.snapTo(dragOffsetX * 0.35f) }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 104.dp)
        ) {
            // 1. 待确认入口
            if (pendingCount > 0) {
                item(key = "pending_confirm_entry") {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(ArchiveShapes.full)
                            .background(colors.accentSoft)
                            .clickable(role = Role.Button) { onNavigateToPending() }
                            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
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
                            text = "待确认 $pendingCount 条",
                            style = ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = colors.archive.accentSmallText
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = FolioGlyphs.ChevronRight,
                            contentDescription = null,
                            tint = colors.archive.accentSmallText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. 本月总览卡片
            item(key = "monthly_summary_overview") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(monthSwipeModifier)
                        .graphicsLayer {
                            translationX = swipeVisual.value
                            val progress = (kotlin.math.abs(swipeVisual.value) / (monthSwipeThresholdPx * 0.35f)).coerceIn(0f, 1f)
                            alpha = 1f - 0.25f * progress
                            rotationZ = swipeVisual.value / 400f
                        }
                        .clip(ArchiveShapes.xl)
                        .background(colors.surface)
                        .border(ArchiveLines.hairline, colors.archive.borderCard, ArchiveShapes.xl)
                ) {
                    Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${selectedYearMonth.monthValue} 月结余",
                                style = ArchiveTextStyles.overline,
                                color = colors.textMuted,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${transactions.size} 笔",
                                style = ArchiveTextStyles.meta,
                                color = colors.textMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HeroAmount(
                            cents = monthBalance,
                            masked = isPrivacyMasked
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 收入 / 支出双栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SummaryStat(
                                label = "收入",
                                value = if (isPrivacyMasked) MASK else FormatUtils.formatMoney(totalIncome),
                                valueColor = colors.income,
                                dotColor = colors.income,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryStat(
                                label = "支出",
                                value = if (isPrivacyMasked) MASK else FormatUtils.formatMoney(totalExpense),
                                valueColor = colors.expense,
                                dotColor = colors.expense,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 支出分布占比条 + 图例
                        if (totalExpense > 0 && categoryTotals.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "支出分布",
                                style = ArchiveTextStyles.meta,
                                color = colors.textMuted
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                categoryTotals.forEach { (cat, amt) ->
                                    val weight = (amt.toFloat() / totalExpense.toFloat()).coerceAtLeast(0.015f)
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .clip(ArchiveShapes.full)
                                            .background(LedgerCategoryColors.colorForCategory(cat, colors.isDark))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val top3 = categoryTotals.take(3)
                            val otherSum = categoryTotals.drop(3).sumOf { it.second }
                            val legendItems = top3 + if (otherSum > 0) listOf("其他" to otherSum) else emptyList()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (cat == "其他") colors.textMuted
                                                    else LedgerCategoryColors.colorForCategory(cat, colors.isDark)
                                                )
                                        )
                                        Text(
                                            text = cat,
                                            style = ArchiveTextStyles.meta,
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            text = "$pct%",
                                            style = ArchiveTextStyles.meta,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 分期摘要（卡片底栏）
                    if (installmentSummary != null && (installmentSummary.thisMonthPaid > 0 || installmentSummary.thisMonthScheduled > 0 || installmentSummary.overdueCount > 0)) {
                        val today = LocalDate.now(FormatUtils.BeijingZone)
                        val nearestDays = runCatching {
                            installmentSummary.nearestDueDate?.let {
                                java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.parse(it))
                            }
                        }.getOrNull()
                        val hasUrgent = installmentSummary.overdueCount > 0 || (nearestDays != null && nearestDays in 0..3)
                        val pendingAmount = if (installmentSummary.overdueCount > 0) {
                            installmentSummary.thisMonthScheduled + installmentSummary.overdueAmount
                        } else {
                            installmentSummary.thisMonthScheduled
                        }

                        ArchiveDivider(modifier = Modifier.padding(horizontal = 22.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onOpenUpcomingInstallments() }
                                .padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(ArchiveShapes.sm)
                                    .background(if (hasUrgent) colors.accentSoft else colors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "分",
                                    fontFamily = ArchiveFontFamilies.Display,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (hasUrgent) colors.accent else colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (installmentSummary.overdueCount > 0) "分期 · 含逾期" else "分期",
                                    style = ArchiveTextStyles.label,
                                    color = if (installmentSummary.overdueCount > 0) colors.alertRed else colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "本月已还 ${if (isPrivacyMasked) MASK else FormatUtils.formatMoney(installmentSummary.thisMonthPaid)}  ·  待还 ${if (isPrivacyMasked) MASK else FormatUtils.formatMoney(pendingAmount)}",
                                    style = ArchiveTextStyles.meta,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = FolioGlyphs.ChevronRight,
                                contentDescription = "查看分期",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 3. 账目列表
            if (transactions.isEmpty()) {
                item(key = "empty_transactions_state") {
                    ArchiveEmptyState(
                        title = "这个月还没有账目",
                        subtitle = "左右滑动总览卡片可以翻看其他月份",
                        actionText = "记一笔",
                        onActionClick = onNavigateToKeypad,
                        modifier = monthSwipeModifier
                    )
                }
            } else {
                val groupedTransactions = transactions
                    .groupBy { tx ->
                        FormatUtils.parseOccurredInstantOrNull(tx.occurredAt)
                            ?.atZone(FormatUtils.BeijingZone)
                            ?.toLocalDate()
                            ?.toString()
                            ?: tx.occurredAt.take(10)
                    }
                    .toList()
                    .sortedByDescending { it.first }

                groupedTransactions.forEach { (dateString, dayTxs) ->
                    item(key = "hdr_$dateString") {
                        val parsedDate = runCatching { LocalDate.parse(dateString) }.getOrNull()
                        val today = LocalDate.now(FormatUtils.BeijingZone)
                        val relative = parsedDate?.let { d ->
                            when (java.time.temporal.ChronoUnit.DAYS.between(d, today)) {
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
                        }

                        // 日结口径：排除分期还款本金；全为分期本金则显示「分期 ¥X」(RC-140)
                        val dayRegularExpense = dayTxs.filter { isExpenseTransaction(it) && it.source != "installment_repay" }.sumOf { it.amount } +
                            dayTxs.filter { isExpenseTransaction(it) && it.source == "installment_repay" }.sumOf { it.feeAmount }
                        val dayInstallmentPrincipal = dayTxs.filter { it.source == "installment_repay" }.sumOf { it.principalAmount }
                        val dayIncome = dayTxs.filter { isIncomeTransaction(it) }.sumOf { it.amount }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (parsedDate != null) {
                                Text(
                                    text = "${parsedDate.monthValue}月${parsedDate.dayOfMonth}日",
                                    style = ArchiveTextStyles.label,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary
                                )
                                if (!relative.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = relative,
                                        style = ArchiveTextStyles.meta,
                                        color = if (relative == "今天") colors.archive.accentSmallText else colors.textMuted
                                    )
                                }
                            } else {
                                Text(
                                    text = dateString,
                                    style = ArchiveTextStyles.label,
                                    color = colors.textPrimary
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (dayRegularExpense > 0) {
                                    Text(
                                        text = "支 ${if (isPrivacyMasked) MASK else FormatUtils.formatMoney(dayRegularExpense)}",
                                        style = ArchiveTextStyles.meta,
                                        color = colors.textSecondary
                                    )
                                } else if (dayInstallmentPrincipal > 0) {
                                    Text(
                                        text = "分期 ${if (isPrivacyMasked) MASK else FormatUtils.formatMoney(dayInstallmentPrincipal)}",
                                        style = ArchiveTextStyles.meta,
                                        color = colors.textSecondary
                                    )
                                }
                                if (dayIncome > 0) {
                                    Text(
                                        text = "收 ${if (isPrivacyMasked) MASK else FormatUtils.formatMoney(dayIncome)}",
                                        style = ArchiveTextStyles.meta,
                                        color = colors.income
                                    )
                                }
                            }
                        }
                    }

                    items(
                        items = dayTxs,
                        key = { "tx_${it.id}_${it.syncStatus}" }
                    ) { tx ->
                        ArchiveSwipeRevealRow(
                            onDelete = { onDelete(tx) },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = colors.background
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
                                if (tx != dayTxs.last()) {
                                    ArchiveDivider(modifier = Modifier.padding(start = 56.dp, end = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 英雄数字：数值变化（切月 / 新记一笔）时像计数器一样滚动 —— 变大从下方升起，变小从上方落下。
 */
@Composable
private fun HeroAmount(cents: Long, masked: Boolean) {
    AnimatedContent(
        targetState = HeroValue(cents, masked),
        transitionSpec = {
            val rising = targetState.cents >= initialState.cents
            val enter = slideInVertically(
                animationSpec = tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseOutEmphasized)
            ) { height -> if (rising) height / 2 else -height / 2 } + fadeIn(tween(ArchiveMotion.DurationBase))
            val exit = slideOutVertically(
                animationSpec = tween(ArchiveMotion.DurationFast, easing = ArchiveMotion.EaseIn)
            ) { height -> if (rising) -height / 2 else height / 2 } + fadeOut(tween(ArchiveMotion.DurationFast))
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "heroAmount"
    ) { value ->
        HeroAmountText(cents = value.cents, masked = value.masked)
    }
}

private data class HeroValue(val cents: Long, val masked: Boolean)

/** 英雄数字排版：货币符号缩小、整数部分大号衬线、小数部分降一档。 */
@Composable
private fun HeroAmountText(cents: Long, masked: Boolean) {
    val colors = LedgerTheme.colors
    if (masked) {
        Text(
            text = "¥ $MASK",
            style = ArchiveTextStyles.amountHero,
            color = colors.textPrimary
        )
        return
    }
    val formatted = FormatUtils.formatMoney(cents)
    val body = formatted.trimStart('¥', ' ', '-', '+')
    val negative = cents < 0L
    val dot = body.lastIndexOf('.')
    val integerPart = if (dot >= 0) body.substring(0, dot) else body
    val decimalPart = if (dot >= 0) body.substring(dot) else ""
    val hero = ArchiveTextStyles.amountHero
    val amountColor = when {
        cents > 0L -> colors.income
        cents < 0L -> colors.alertRed
        else -> colors.textPrimary
    }
    val prefixColor = if (negative) colors.alertRed.copy(alpha = 0.78f) else colors.textMuted
    val decimalColor = when {
        cents > 0L -> colors.income.copy(alpha = 0.72f)
        cents < 0L -> colors.alertRed.copy(alpha = 0.72f)
        else -> colors.textSecondary
    }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, color = prefixColor)) {
                append(if (negative) "-¥ " else "¥ ")
            }
            withStyle(SpanStyle(fontSize = hero.fontSize, color = amountColor)) {
                append(integerPart)
            }
            if (decimalPart.isNotEmpty()) {
                withStyle(SpanStyle(fontSize = 24.sp, color = decimalColor)) {
                    append(decimalPart)
                }
            }
        },
        style = hero,
        color = amountColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    valueColor: Color,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors
    Column(
        modifier = modifier
            .clip(ArchiveShapes.md)
            .background(colors.surfaceElevated.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = ArchiveTextStyles.meta,
                color = colors.textMuted
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = ArchiveTextStyles.amount,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 账目单行条目
 * - 左：34dp 分类方印（分类色淡底 + 分类首字，衬线）
 * - 中：标题（商户 / 备注 / 分类名）与「时间 · 渠道」
 * - 右：等宽金额，收入为松绿带「+」
 */
@Composable
private fun TransactionRowItem(
    tx: Transaction,
    isPrivacyMasked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LedgerTheme.colors
    val isIncome = isIncomeTransaction(tx)
    val isInstallmentRepay = tx.source == "installment_repay"
    val sign = if (isIncome) "+" else "-"
    val displayAmount = if (isPrivacyMasked) "¥ $MASK" else "$sign${FormatUtils.formatMoney(tx.amount)}"
    val categoryName = if (isInstallmentRepay) "利息手续费" else tx.category
    val categoryColor = LedgerCategoryColors.colorForCategory(categoryName, colors.isDark)
    val displayTime = remember(tx.occurredAt) { FormatUtils.formatDisplayTime(tx.occurredAt) }

    val titleText = remember(tx) {
        val note = tx.note?.trim()
        val title = tx.title.trim()
        when {
            title.isNotBlank() && title != tx.category -> title
            !note.isNullOrBlank() -> note
            else -> tx.category
        }
    }

    val subtitleText = remember(tx, displayTime) {
        if (isInstallmentRepay) {
            val feePart = if (tx.feeAmount > 0) "手续费 ${FormatUtils.formatMoney(tx.feeAmount)}" else null
            val accountPart = tx.paymentMethod?.takeIf { it.isNotBlank() }
            listOfNotNull("分期还款", feePart, accountPart).joinToString(" · ")
        } else {
            val channel = tx.paymentMethod?.takeIf { it.isNotBlank() } ?: "现金"
            listOfNotNull(
                displayTime.takeIf { it.isNotBlank() },
                tx.category.takeIf { titleText != tx.category && it.isNotBlank() },
                channel
            ).joinToString(" · ")
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedBg by animateColorAsState(
        targetValue = if (isPressed) colors.archive.pressOverlay else Color.Transparent,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "txPress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.md)
            .background(pressedBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ArchiveShapes.sm)
                .background(categoryColor.copy(alpha = if (colors.isDark) 0.18f else 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = categoryName.take(1).ifBlank { "账" },
                fontFamily = ArchiveFontFamilies.Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = categoryColor
            )
        }
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titleText,
                style = ArchiveTextStyles.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = ArchiveTextStyles.meta,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = displayAmount,
            style = ArchiveTextStyles.amount,
            textAlign = TextAlign.End,
            color = if (isIncome) colors.income else colors.expense
        )
    }
}
