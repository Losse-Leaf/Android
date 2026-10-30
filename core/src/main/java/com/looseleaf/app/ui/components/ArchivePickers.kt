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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.utils.CityDictionary
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 散页底线式属地城市选择控件 (ArchiveCityPickerField)
 * 规则：顶部衬线标签、无外边框、底线发丝线、右侧箭头、占位符"选择城市"
 */
@Composable
fun ArchiveCityPickerField(
    selectedCityCode: String?,
    selectedCityName: String?,
    onCitySelected: (code: String, name: String) -> Unit,
    label: String? = null,
    placeholder: String = "选择城市",
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }

    val hasSelection = !selectedCityName.isNullOrBlank() || !selectedCityCode.isNullOrBlank()
    val displayText = if (hasSelection) {
        val name = selectedCityName ?: ""
        val code = selectedCityCode ?: ""
        if (code.isNotBlank()) "$name ($code)" else name
    } else {
        placeholder
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    searchKeyword = ""
                    showDialog = true
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayText,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 14.sp,
                color = if (hasSelection) colors.inkPrimary else colors.inkMuted
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = "选择城市",
                tint = colors.inkSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        // 底部单发丝线
        ArchiveDivider()

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 11.5.sp,
                color = colors.inkSecondary
            )
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArchiveShapes.radiusSmall)
                    .background(colors.archivePaper)
                    .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.radiusSmall)
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选择属地城市",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkPrimary
                        )
                        ArchiveTextButton(
                            text = "关闭",
                            textColor = colors.inkSecondary,
                            onClick = { showDialog = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ArchiveField(
                        value = searchKeyword,
                        onValueChange = { searchKeyword = it },
                        placeholder = "搜索城市名称或拼音…",
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val filteredCities = remember(searchKeyword) {
                        CityDictionary.searchCities(searchKeyword)
                    }

                    if (filteredCities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "未检索到匹配城市",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                color = colors.inkMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            items(filteredCities, key = { it.code }) { city ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCitySelected(city.code, city.name)
                                            showDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = city.name,
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 14.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = city.code,
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                ArchiveDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 散页纸墨风日期选择器 (ArchiveDatePickerSheet)
 * 采用温润纸色居中弹窗形态（无投影，4dp 微圆角，极细发丝沉墨边），
 * 彻底避免在底部抽屉表单中唤起时出现「底抽叠底抽」的层级冲突。
 * - 顶部显示 "2026 年 10 月 ▾"，点击平滑切换为 4×3 月份网格
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
    val today = remember { LocalDate.now() }

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
                    .clip(ArchiveShapes.radiusSmall)
                    .background(colors.archivePaper)
                    .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.radiusSmall)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
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
                            text = if (isMonthPickerMode) "${currentYearMonth.year} 年" else "${currentYearMonth.year} 年 ${currentYearMonth.monthValue} 月 ▾",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
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
                                            color = if (isSelected) colors.accentWarm else colors.inkPrimary
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
                                                        isToday -> Modifier.border(ArchiveLines.hairline, colors.inkSecondary, CircleShape)
                                                        else -> Modifier
                                                    }
                                                )
                                                .clickable {
                                                    tempDate = date
                                                }
                                        ) {
                                            Text(
                                                text = dayNum.toString(),
                                                fontFamily = ArchiveFontFamilies.MonoScale,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = when {
                                                    isSelected -> colors.archivePaper
                                                    isToday -> colors.accentWarm
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

                    ArchiveTextButton(
                        text = "确定",
                        textColor = colors.accentWarm,
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
                    .clip(ArchiveShapes.radiusSmall)
                    .background(colors.archivePaper)
                    .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.radiusSmall)
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
 * 散页底线式日期与时间拆分输入行 (ArchiveDateTimeSplitRow)
 * 规则：顶部衬线标签、两个无框底线式子字段（日期 / 时间），点击分别唤起 ArchiveDatePickerSheet 与 ArchiveTimePickerSheet
 */
@Composable
fun ArchiveDateTimeSplitRow(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateStr = remember(selectedDateTime) {
        String.format(Locale.US, "%04d-%02d-%02d", selectedDateTime.year, selectedDateTime.monthValue, selectedDateTime.dayOfMonth)
    }
    val timeStr = remember(selectedDateTime) {
        String.format(Locale.US, "%02d:%02d", selectedDateTime.hour, selectedDateTime.minute)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日期字段
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .clickable { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateStr,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 14.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "▾",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                }
                ArchiveDivider()
            }

            // 时间字段
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showTimePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 14.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "▾",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkSecondary
                    )
                }
                ArchiveDivider()
            }
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
 * 散页底线式单日期选择字段 (ArchiveDatePickerField)
 */
@Composable
fun ArchiveDatePickerField(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    label: String? = null,
    placeholder: String = "选择日期",
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSheet = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedDate?.let { String.format(Locale.US, "%04d-%02d-%02d", it.year, it.monthValue, it.dayOfMonth) } ?: placeholder,
                fontFamily = if (selectedDate != null) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                fontSize = 14.sp,
                color = if (selectedDate != null) colors.inkPrimary else colors.inkMuted
            )
            Text(
                text = "▾",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary
            )
        }
        ArchiveDivider()
    }

    if (showSheet) {
        ArchiveDatePickerSheet(
            selectedDate = selectedDate ?: LocalDate.now(),
            onDateSelected = onDateSelected,
            onDismissRequest = { showSheet = false }
        )
    }
}

/**
 * 散页底线式单时间选择字段 (ArchiveTimePickerField)
 */
@Composable
fun ArchiveTimePickerField(
    selectedTime: LocalTime?,
    onTimeSelected: (LocalTime) -> Unit,
    label: String? = null,
    placeholder: String = "选择时间",
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showSheet by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSheet = true }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedTime?.let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) } ?: placeholder,
                fontFamily = if (selectedTime != null) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                fontSize = 14.sp,
                color = if (selectedTime != null) colors.inkPrimary else colors.inkMuted
            )
            Text(
                text = "▾",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkSecondary
            )
        }
        ArchiveDivider()
    }

    if (showSheet) {
        ArchiveTimePickerSheet(
            selectedTime = selectedTime ?: LocalTime.now(),
            onTimeSelected = onTimeSelected,
            onDismissRequest = { showSheet = false }
        )
    }
}
