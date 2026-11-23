package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.looseleaf.core.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.looseleaf.app.theme.ArchiveTextStyles
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 散页纸墨风日期选择器 (ArchiveDatePickerSheet)
 * 采用温润纸色居中弹窗形态（无投影，4dp 微圆角，极细发丝沉墨边），
 * 彻底避免在底部抽屉表单中唤起时出现「底抽叠底抽」的层级冲突。
 * - 顶部显示「2026 年 10 月」与下拉箭头，点击平滑切换为 4×3 月份网格
 * - 纸色月历网格，周一为起始，固定中文星期（"一" ~ "日"）
 * - 选中日使用赤陶实心小圆与反白字，今天使用次要色细圈
 * - 底栏：左侧取消、右侧确定文字按钮
 */
@Composable
fun ArchiveDatePickerSheet(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    var tempDate by remember(selectedDate) { mutableStateOf(selectedDate) }
    var currentYearMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    var isMonthPickerMode by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now(FormatUtils.BeijingZone) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .shadow(
                        elevation = com.looseleaf.app.theme.ArchiveShadows.dialogElevation,
                        shape = ArchiveShapes.lg,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(ArchiveShapes.lg)
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                // 顶栏：月份切换头与左右切月
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isMonthPickerMode) {
                                currentYearMonth = currentYearMonth.minusYears(1)
                            } else {
                                currentYearMonth = currentYearMonth.minusMonths(1)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_left),
                            contentDescription = "上一页",
                            tint = colors.inkSecondary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(ArchiveShapes.radiusSmall)
                            .clickable { isMonthPickerMode = !isMonthPickerMode }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isMonthPickerMode) "${currentYearMonth.year} 年" else "${currentYearMonth.year} 年 ${currentYearMonth.monthValue} 月",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isMonthPickerMode) FolioGlyphs.ChevronUp else FolioGlyphs.ChevronDown,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (isMonthPickerMode) {
                                currentYearMonth = currentYearMonth.plusYears(1)
                            } else {
                                currentYearMonth = currentYearMonth.plusMonths(1)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = "下一页",
                            tint = colors.inkSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isMonthPickerMode) {
                    // 4x3 月份网格（沿用账本月份选择样式）
                    val months = (1..12).toList()
                    for (row in months.chunked(4)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (m in row) {
                                val isSelected = currentYearMonth.monthValue == m
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(ArchiveShapes.radiusSmall)
                                        .clickable {
                                            currentYearMonth = YearMonth.of(currentYearMonth.year, m)
                                            tempDate = currentYearMonth.atDay(tempDate.dayOfMonth.coerceAtMost(currentYearMonth.lengthOfMonth()))
                                            isMonthPickerMode = false
                                        }
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${m}月",
                                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp,
                                            color = if (isSelected) colors.accentSmallText else colors.inkPrimary
                                        )
                                        if (isSelected) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(16.dp)
                                                    .height(2.dp)
                                                    .background(colors.accentWarm)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 星期表头（固定简体中文）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                            Text(
                                text = w,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 12.sp,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 日期月历网格
                    val firstDay = currentYearMonth.atDay(1)
                    val dayOffset = (firstDay.dayOfWeek.value - 1) % 7
                    val totalDays = currentYearMonth.lengthOfMonth()
                    val totalCells = ((dayOffset + totalDays + 6) / 7) * 7

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (week in 0 until totalCells / 7) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (dayCol in 0..6) {
                                    val cellIndex = week * 7 + dayCol
                                    val dayNum = cellIndex - dayOffset + 1
                                    if (dayNum in 1..totalDays) {
                                        val date = currentYearMonth.atDay(dayNum)
                                        val isSelected = date == tempDate
                                        val isToday = date == today

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    when {
                                                        isSelected -> Modifier.background(colors.accentWarm)
                                                        isToday -> Modifier.background(colors.accentTint)
                                                        else -> Modifier
                                                    }
                                                )
                                                .clickable {
                                                    tempDate = date
                                                }
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontFamily = ArchiveFontFamilies.Numeric,
                                                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                                fontSize = 14.sp,
                                                color = when {
                                                    isSelected -> colors.onAccent
                                                    isToday -> colors.accentSmallText
                                                    else -> colors.inkPrimary
                                                }
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                ArchiveDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // 底栏操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArchiveTextButton(
                        text = "取消",
                        textColor = colors.inkSecondary,
                        onClick = onDismissRequest
                    )

                    ArchiveSheetActionButton(
                        text = "确定",
                        onClick = {
                            onDateSelected(tempDate)
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 散页纸墨风时间选择器 (ArchiveTimePickerSheet)
 * 采用居中纸墨卡片弹窗形态（无投影，4dp 微圆角，极细发丝沉墨边），
 * 彻底消除在底部抽屉中唤起时「双层底抽堆叠」的交互割裂。
 * - 24 小时制双列滚轮（时 00~23、分 00~59），居中吸附对齐
 * - 等宽数字，选中项放大加粗，上下透出一行次要色刻度
 * - 仅在中间选中区呈现上下两道极细发丝刻度线，杜绝横贯全屏的粗黑边框
 * - 顶栏提供"现在"快捷对齐当前系统时刻
 * - 底栏：左取消、右确定文字按钮，无时钟表盘
 */
@Composable
fun ArchiveTimePickerSheet(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val scope = rememberCoroutineScope()

    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = selectedTime.hour)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = selectedTime.minute)

    val hourFling = rememberSnapFlingBehavior(lazyListState = hourState)
    val minuteFling = rememberSnapFlingBehavior(lazyListState = minuteState)

    // 精确计算最接近中心视口刻度线的项（避免 contentPadding 偏移导致项计算错位）
    val centerHour by remember {
        derivedStateOf {
            val layoutInfo = hourState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }?.index?.coerceIn(0, 23) ?: selectedTime.hour
        }
    }

    val centerMinute by remember {
        derivedStateOf {
            val layoutInfo = minuteState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }?.index?.coerceIn(0, 59) ?: selectedTime.minute
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 340.dp)
                    .shadow(
                        elevation = com.looseleaf.app.theme.ArchiveShadows.dialogElevation,
                        shape = ArchiveShapes.lg,
                        spotColor = colors.frameShadowSpot,
                        ambientColor = colors.frameShadowAmbient
                    )
                    .clip(ArchiveShapes.lg)
                    .background(colors.surfaceRaised)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // 顶栏：标题与"现在"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择时间",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary
                    )

                    ArchiveTextButton(
                        text = "现在",
                        textColor = colors.accentWarm,
                        onClick = {
                            val now = LocalTime.now()
                            scope.launch {
                                hourState.animateScrollToItem(now.hour)
                                minuteState.animateScrollToItem(now.minute)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 双列滚轮选择器（时 / 分）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 中部吸附高亮槽 (44dp)：仅在时分数字中央区域呈现上下两道极细发丝线，无外框
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .height(44.dp)
                    ) {
                        HorizontalDivider(thickness = ArchiveLines.hairline, color = colors.borderSubtle)
                        Spacer(modifier = Modifier.weight(1f))
                        HorizontalDivider(thickness = ArchiveLines.hairline, color = colors.borderSubtle)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 小时列
                        LazyColumn(
                            state = hourState,
                            flingBehavior = hourFling,
                            contentPadding = PaddingValues(vertical = 44.dp),
                            modifier = Modifier
                                .width(80.dp)
                                .height(132.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(24) { h ->
                                val isSelected = h == centerHour
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clickable {
                                            scope.launch { hourState.animateScrollToItem(h) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%02d", h),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = if (isSelected) 22.sp else 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.inkPrimary else colors.inkSecondary.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }

                        Text(
                            text = ":",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        // 分钟列
                        LazyColumn(
                            state = minuteState,
                            flingBehavior = minuteFling,
                            contentPadding = PaddingValues(vertical = 44.dp),
                            modifier = Modifier
                                .width(80.dp)
                                .height(132.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(60) { m ->
                                val isSelected = m == centerMinute
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clickable {
                                            scope.launch { minuteState.animateScrollToItem(m) }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%02d", m),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = if (isSelected) 22.sp else 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.inkPrimary else colors.inkSecondary.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                ArchiveDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // 底栏操作
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArchiveTextButton(
                        text = "取消",
                        textColor = colors.inkSecondary,
                        onClick = onDismissRequest
                    )

                    ArchiveTextButton(
                        text = "确定",
                        textColor = colors.accentWarm,
                        onClick = {
                            onTimeSelected(LocalTime.of(centerHour, centerMinute))
                            onDismissRequest()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 日期 + 时间拆分行 (ArchiveDateTimeSplitRow)
 * 上方可选标签；下方两枚凹槽输入槽（日期 / 时间），点击分别唤起日期、时间选择器。
 */
@Composable
fun ArchiveDateTimeSplitRow(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateStr = remember(selectedDateTime) {
        String.format(Locale.US, "%04d-%02d-%02d", selectedDateTime.year, selectedDateTime.monthValue, selectedDateTime.dayOfMonth)
    }
    val timeStr = remember(selectedDateTime) {
        String.format(Locale.US, "%02d:%02d", selectedDateTime.hour, selectedDateTime.minute)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PickerLabel(label)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PickerSlot(
                value = dateStr,
                isPlaceholder = false,
                numeric = true,
                trailingGlyph = FolioGlyphs.ChevronDown,
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1.35f)
            )
            PickerSlot(
                value = timeStr,
                isPlaceholder = false,
                numeric = true,
                trailingGlyph = FolioGlyphs.Clock,
                onClick = { showTimePicker = true },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showDatePicker) {
        ArchiveDatePickerSheet(
            selectedDate = selectedDateTime.toLocalDate(),
            onDateSelected = { newDate ->
                onDateTimeSelected(selectedDateTime.withYear(newDate.year).withMonth(newDate.monthValue).withDayOfMonth(newDate.dayOfMonth))
            },
            onDismissRequest = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        ArchiveTimePickerSheet(
            selectedTime = selectedDateTime.toLocalTime(),
            onTimeSelected = { newTime ->
                onDateTimeSelected(selectedDateTime.withHour(newTime.hour).withMinute(newTime.minute).withSecond(0))
            },
            onDismissRequest = { showTimePicker = false }
        )
    }
}

/**
 * 单日期选择字段 (ArchiveDatePickerField)
 * 凹槽输入槽；有值且提供 onClear 时右侧出现圆形清除按钮。
 */
@Composable
fun ArchiveDatePickerField(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    label: String? = null,
    placeholder: String = "选择日期",
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        PickerLabel(label)
        PickerSlot(
            value = selectedDate?.let { String.format(Locale.US, "%04d-%02d-%02d", it.year, it.monthValue, it.dayOfMonth) } ?: placeholder,
            isPlaceholder = selectedDate == null,
            numeric = selectedDate != null,
            trailingGlyph = FolioGlyphs.ChevronDown,
            onClick = { showSheet = true },
            extraTrailing = if (selectedDate != null && onClear != null) {
                {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.inkMuted.copy(alpha = 0.16f))
                            .clickable(role = Role.Button, onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FolioGlyphs.Close,
                            contentDescription = "清除",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            } else null
        )
    }

    if (showSheet) {
        ArchiveDatePickerSheet(
            selectedDate = selectedDate ?: LocalDate.now(FormatUtils.BeijingZone),
            onDateSelected = onDateSelected,
            onDismissRequest = { showSheet = false }
        )
    }
}

/**
 * 通用单选字段 (ArchiveSelectField)
 * 凹槽输入槽 + 下拉箭头，点击唤起统一 ArchiveDialog 选项列表；选中项为朱砂淡底行 + 对勾。
 */
@Composable
fun <T> ArchiveSelectField(
    selectedValue: T,
    options: List<Pair<T, String>>,
    onValueSelected: (T) -> Unit,
    label: String? = null,
    placeholder: String = "请选择",
    dialogTitle: String = label ?: "请选择",
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second
    val hasValue = selectedValue != null && !selectedLabel.isNullOrBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        PickerLabel(label)
        PickerSlot(
            value = selectedLabel ?: placeholder,
            isPlaceholder = !hasValue,
            numeric = false,
            trailingGlyph = FolioGlyphs.ChevronDown,
            onClick = { showDialog = true }
        )
    }

    if (showDialog) {
        ArchiveDialog(onDismissRequest = { showDialog = false }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dialogTitle,
                    style = ArchiveTextStyles.sheetTitle,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f)
                )
                ArchiveTextButton(
                    text = "关闭",
                    textColor = colors.inkSecondary,
                    onClick = { showDialog = false }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(options) { (optVal, optLabel) ->
                    val isSelected = optVal == selectedValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArchiveShapes.md)
                            .background(if (isSelected) colors.accentTint else Color.Transparent)
                            .clickable {
                                onValueSelected(optVal)
                                showDialog = false
                            }
                            .padding(vertical = 13.dp, horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = optLabel,
                            style = ArchiveTextStyles.body,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.accentSmallText else colors.inkPrimary
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = FolioGlyphs.Check,
                                contentDescription = "已选",
                                tint = colors.accentSmallText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerLabel(label: String?) {
    if (label.isNullOrBlank()) return
    Text(
        text = label,
        style = ArchiveTextStyles.meta,
        fontWeight = FontWeight.Medium,
        color = LooseLeafTheme.colors.inkSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

/**
 * 选择器触发槽：与 ArchiveField 同一套凹槽外观（熟宣底、14dp 圆角、50dp 高）。
 */
@Composable
private fun PickerSlot(
    value: String,
    isPlaceholder: Boolean,
    numeric: Boolean,
    trailingGlyph: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraTrailing: (@Composable () -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clip(ArchiveShapes.field)
            .background(colors.archiveSubtle)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = if (numeric) ArchiveTextStyles.amount.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium) else ArchiveTextStyles.body.copy(fontSize = 15.sp),
            color = if (isPlaceholder) colors.inkMuted else colors.inkPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (extraTrailing != null) {
            extraTrailing()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = trailingGlyph,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * 纸色月份选择弹层 (ArchiveMonthPickerSheet)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveMonthPickerSheet(
    selectedYearMonth: YearMonth,
    onYearMonthSelected: (YearMonth) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    var currentYear by remember(selectedYearMonth) { mutableStateOf(selectedYearMonth.year) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        scrimColor = colors.scrim,
        shape = ArchiveShapes.sheetTop,
        tonalElevation = 0.dp,
        dragHandle = { ArchiveSheetGrip(modifier = Modifier.padding(bottom = 8.dp)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { currentYear-- }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = "上一年",
                        tint = colors.inkPrimary
                    )
                }
                Text(
                    text = "${currentYear} 年",
                    style = ArchiveTextStyles.pageTitle.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = colors.inkPrimary
                )
                IconButton(onClick = { currentYear++ }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = "下一年",
                        tint = colors.inkPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val months = (1..12).toList()
            for (row in months.chunked(4)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (m in row) {
                        val isSelected = currentYear == selectedYearMonth.year && m == selectedYearMonth.monthValue
                        val nowYm = YearMonth.now(FormatUtils.BeijingZone)
                        val isCurrent = currentYear == nowYm.year && m == nowYm.monthValue
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(ArchiveShapes.md)
                                .background(
                                    when {
                                        isSelected -> colors.inverseSurface
                                        isCurrent -> colors.accentTint
                                        else -> colors.archiveSubtle
                                    }
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onYearMonthSelected(YearMonth.of(currentYear, m))
                                    onDismissRequest()
                                }
                        ) {
                            Text(
                                text = "${m}月",
                                style = ArchiveTextStyles.label,
                                fontWeight = if (isSelected || isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected -> colors.inverseInk
                                    isCurrent -> colors.accentSmallText
                                    else -> colors.inkPrimary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
