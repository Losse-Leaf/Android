package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.platform.LocalContext
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.utils.CityDictionary

/**
 * 手账质感纸张卡片容器
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val bg = backgroundColor ?: colors.archiveSubtle
    val border = borderColor ?: colors.borderDivider
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, border, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 陶土赭石色高对比印章按钮 (Terracotta Stamp Button)
 * 专为「盖章记下」与核心主干提交操作打造，模拟物理木印章的厚重沉稳感
 */
@Composable
fun TerracottaStampButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(2.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentWarm,
            contentColor = colors.archivePaper,
            disabledContainerColor = colors.archiveSubtle,
            disabledContentColor = colors.inkMuted.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 1.dp,
            pressedElevation = 0.dp
        ),
        border = if (!enabled) ButtonDefaults.outlinedButtonBorder.copy(
            brush = SolidColor(colors.borderDivider)
        ) else null,
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            letterSpacing = 2.sp
        )
    }
}

/**
 * 纸质分类与状态标签 (Paper Badge)
 */
@Composable
fun PaperBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    textColor: Color? = null,
    isPendingSync: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    val bg = containerColor ?: if (isPendingSync) colors.accentWarm.copy(alpha = 0.12f) else colors.archiveSubtle
    val fg = textColor ?: if (isPendingSync) colors.accentWarm else colors.inkSecondary
    val shape = RoundedCornerShape(2.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, colors.borderDivider, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (isPendingSync) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accentWarm)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = fg
        )
    }
}

/**
 * 典雅极细折痕线
 */
@Composable
fun PaperDivider(
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val dividerColor = color ?: LooseLeafTheme.colors.borderDivider
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = dividerColor
    )
}

/**
 * 散页独家自研纸本输入框 (PaperTextField)
 * 彻底替换 Material 粗糙的 OutlinedTextField，还原宣纸微凹、极细墨边与沉稳光标
 * 支持：
 * - isUnderline = true：典雅稿纸下划底线风格（无生硬方框，纯净书写基线）
 * - isUnderline = false：温润宣纸微凹内嵌风格（极细沉墨微边，聚焦金石朱砂）
 */
@Composable
fun PaperTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    isUnderline: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(3.dp)
    val borderColor = if (isFocused) colors.accentWarm else colors.borderDivider
    val borderWidth = if (isFocused) 1.dp else 0.5.dp
    val effectiveTextStyle = (textStyle ?: TextStyle(
        fontFamily = ArchiveFontFamilies.EditorialSerif,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Normal,
        color = colors.inkPrimary
    ))

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = if (isFocused) colors.accentWarm else colors.inkSecondary,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        val boxModifier = if (isUnderline) {
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = if (isFocused) 1.5.dp.toPx() else 0.8.dp.toPx()
                    val y = size.height - stroke / 2
                    drawLine(
                        color = borderColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = stroke
                    )
                }
                .padding(vertical = 8.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.archivePaper, shape)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        }

        Box(
            modifier = boxModifier,
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = effectiveTextStyle.copy(color = colors.inkMuted)
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = singleLine,
                        minLines = minLines,
                        enabled = enabled,
                        textStyle = effectiveTextStyle,
                        cursorBrush = SolidColor(colors.accentWarm),
                        interactionSource = interactionSource,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    trailingIcon()
                }
            }
        }
    }
}

/**
 * 散页独家自研多行文稿纸本编辑器 (PaperTextEditor)
 * 专为长篇手记、随笔与感言打造，温润信笺质感，优美行高与微凹边框
 */
@Composable
fun PaperTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    minHeight: Dp = 100.dp,
    enabled: Boolean = true,
    textStyle: TextStyle? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val shape = RoundedCornerShape(3.dp)
    val borderColor = if (isFocused) colors.accentWarm else colors.borderDivider
    val borderWidth = if (isFocused) 1.dp else 0.5.dp
    val effectiveTextStyle = (textStyle ?: TextStyle(
        fontFamily = ArchiveFontFamilies.BodySans,
        fontSize = 14.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
        color = colors.inkPrimary
    ))

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = if (isFocused) colors.accentWarm else colors.inkSecondary,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(colors.archivePaper, shape)
                .border(borderWidth, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = effectiveTextStyle.copy(color = colors.inkMuted)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                enabled = enabled,
                textStyle = effectiveTextStyle,
                cursorBrush = SolidColor(colors.accentWarm),
                interactionSource = interactionSource,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions
            )
        }
    }
}

/**
 * 散页纸本筛选与形态选择胶囊 (PaperFilterPill)
 * 彻底替换 Material3 默认 FilterChip 的违和薰衣草紫色，呈现沉稳的朱砂与棉纸大地质感
 */
@Composable
fun PaperFilterPill(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle
    val border = if (selected) colors.accentWarm else colors.borderDivider
    val textColor = if (selected) colors.accentWarm else colors.inkSecondary

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(if (selected) 1.dp else 0.5.dp, border, shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = textColor
        )
    }
}

/**
 * 纸质拟物行政区划城市选择器 (PaperCityPickerField)
 * 严格遵循主理人规范：地点禁止用户自由输入，必须从 375 个标准地级行政区划中点选
 */
@Composable
fun PaperCityPickerField(
    selectedCityCode: String?,
    selectedCityName: String?,
    onCitySelected: (code: String, name: String) -> Unit,
    placeholder: String = "选择城市…",
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }

    val hasSelection = !selectedCityName.isNullOrBlank() || !selectedCityCode.isNullOrBlank()
    val displayText = if (hasSelection) {
        val name = selectedCityName ?: ""
        val code = selectedCityCode ?: ""
        val prefix = if (code.length >= 2) code.substring(0, 2) else ""
        val prov = CityDictionary.provincePrefixMap[prefix] ?: ""
        val provText = if (prov.isBlank() || name.contains(prov)) "" else "$prov · "
        if (code.isNotBlank()) "$provText$name ($code)" else name
    } else {
        placeholder
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(colors.archiveSubtle)
            .border(
                width = if (hasSelection) 0.8.dp else 0.5.dp,
                color = if (hasSelection) colors.accentWarm.copy(alpha = 0.4f) else colors.borderDivider,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable {
                searchKeyword = ""
                showDialog = true
            }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = null,
                tint = if (hasSelection) colors.accentWarm else colors.inkMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = displayText,
                fontFamily = if (hasSelection) ArchiveFontFamilies.EditorialSerif else ArchiveFontFamilies.BodySans,
                fontSize = 12.5.sp,
                color = if (hasSelection) colors.inkPrimary else colors.inkMuted,
                modifier = Modifier.weight(1f)
            )
            if (hasSelection) {
                IconButton(
                    onClick = { onCitySelected("", "") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "清除城市",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.archivePaper)
                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选择属地城市",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        )
                        IconButton(
                            onClick = { showDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    PaperTextField(
                        value = searchKeyword,
                        onValueChange = { searchKeyword = it },
                        placeholder = "搜索城市…",
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredCities = remember(searchKeyword) {
                        CityDictionary.searchCities(searchKeyword)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(filteredCities, key = { it.code }) { city ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable {
                                        onCitySelected(city.code, city.name)
                                        showDialog = false
                                    }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = city.name,
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = city.province,
                                        fontFamily = ArchiveFontFamilies.BodySans,
                                        fontSize = 10.5.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                Text(
                                    text = city.code,
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 11.sp,
                                    color = colors.accentWarm
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 散页古典纸墨风格日期选择器弹窗 (PaperDatePickerDialog)
 * 纯原生 Compose 打造，告别丑陋的系统 Dialog，呈现温润宣纸与朱砂纪年质感
 */
@Composable
fun PaperDatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    var tempDate by remember { mutableStateOf(selectedDate) }
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }
    val today = remember { LocalDate.now(FormatUtils.BeijingZone) }

    Dialog(onDismissRequest = onDismiss) {
        PaperCard(
            backgroundColor = colors.archivePaper,
            borderColor = colors.borderSubtle,
            cornerRadius = 8.dp,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 顶栏：标题与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "时空纪年 · 发生日期",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkPrimary
                        )
                        val relativeText = when (tempDate) {
                            today -> "今天"
                            today.minusDays(1) -> "昨天"
                            today.minusDays(2) -> "前天"
                            else -> "周" + when (tempDate.dayOfWeek.value) {
                                1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
                            }
                        }
                        Text(
                            text = "${tempDate.year}年${tempDate.monthValue}月${tempDate.dayOfMonth}日 · $relativeText",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 11.5.sp,
                            color = colors.accentWarm
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 月份翻阅切换条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { currentMonth = currentMonth.minusMonths(1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronLeft,
                            contentDescription = "上一月",
                            tint = colors.inkSecondary
                        )
                    }

                    Text(
                        text = "${currentMonth.year} 年 ${currentMonth.monthValue} 月",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.inkPrimary
                    )

                    IconButton(
                        onClick = { currentMonth = currentMonth.plusMonths(1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "下一月",
                            tint = colors.inkSecondary
                        )
                    }
                }

                // 星期表头
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                        Text(
                            text = w,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            modifier = Modifier.width(32.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                // 典雅古典纸墨月历网格
                val daysInMonth = currentMonth.lengthOfMonth()
                val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value // 1 (Mon) - 7 (Sun)
                val totalCells = ((firstDayOfWeek - 1) + daysInMonth + 6) / 7 * 7

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (week in 0 until (totalCells / 7)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 1..7) {
                                val cellIndex = week * 7 + col
                                val dayNum = cellIndex - (firstDayOfWeek - 1)
                                if (dayNum in 1..daysInMonth) {
                                    val cellDate = currentMonth.atDay(dayNum)
                                    val isSelected = cellDate == tempDate
                                    val isCellToday = cellDate == today

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                when {
                                                    isSelected -> colors.accentWarm
                                                    isCellToday -> colors.accentWarm.copy(alpha = 0.12f)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                width = if (isCellToday && !isSelected) 0.8.dp else 0.dp,
                                                color = if (isCellToday && !isSelected) colors.accentWarm else Color.Transparent,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable { tempDate = cellDate },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontFamily = ArchiveFontFamilies.MonoScale,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isCellToday) FontWeight.SemiBold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> colors.archivePaper
                                                isCellToday -> colors.accentWarm
                                                else -> colors.inkPrimary
                                            }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

                // 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = colors.inkSecondary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDateSelected(tempDate)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
                            contentColor = colors.archivePaper
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("确定日期", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 散页古典纸墨风格时间微调弹窗 (PaperTimePickerDialog)
 * 默认当前时刻，支持精确到分的高对比度时分微调与快捷时段芯片
 */
@Composable
fun PaperTimePickerDialog(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    var tempHour by remember { mutableStateOf(selectedTime.hour) }
    var tempMinute by remember { mutableStateOf(selectedTime.minute) }

    Dialog(onDismissRequest = onDismiss) {
        PaperCard(
            backgroundColor = colors.archivePaper,
            borderColor = colors.borderSubtle,
            cornerRadius = 8.dp,
            contentPadding = PaddingValues(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 顶栏：标题与关闭
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "发生时刻 · 调整时分",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

                // 快捷时段纸签 (严格对齐 Web PaperDatePicker: 晨 09:00, 午 12:30, 暮 18:00, 夜 22:00)
                val nowTime = remember { LocalTime.now(FormatUtils.BeijingZone) }
                val timePresets = listOf(
                    "此刻" to nowTime,
                    "晨 09:00" to LocalTime.of(9, 0),
                    "午 12:30" to LocalTime.of(12, 30),
                    "暮 18:00" to LocalTime.of(18, 0),
                    "夜 22:00" to LocalTime.of(22, 0)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    timePresets.forEach { (label, presetTime) ->
                        val isPresetActive = tempHour == presetTime.hour && tempMinute == presetTime.minute
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isPresetActive) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle)
                                .border(
                                    0.5.dp,
                                    if (isPresetActive) colors.accentWarm else colors.borderDivider,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    tempHour = presetTime.hour
                                    tempMinute = presetTime.minute
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 11.5.sp,
                                fontWeight = if (isPresetActive) FontWeight.Medium else FontWeight.Normal,
                                color = if (isPresetActive) colors.accentWarm else colors.inkSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 大号纸墨时钟展示与微调步进器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.archiveSubtle)
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 时位步进
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { tempHour = (tempHour + 1) % 24 },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "+1小时", tint = colors.accentWarm, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", tempHour),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkPrimary
                            )
                            IconButton(
                                onClick = { tempHour = (tempHour - 1 + 24) % 24 },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Remove, contentDescription = "-1小时", tint = colors.accentWarm, modifier = Modifier.size(16.dp))
                            }
                            Text("时 (00-23)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 9.5.sp, color = colors.inkMuted)
                        }

                        Text(
                            text = ":",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        // 分位步进
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            IconButton(
                                onClick = { tempMinute = (tempMinute + 1) % 60 },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "+1分钟", tint = colors.accentWarm, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = String.format(Locale.US, "%02d", tempMinute),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkPrimary
                            )
                            IconButton(
                                onClick = { tempMinute = (tempMinute - 1 + 60) % 60 },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Outlined.Remove, contentDescription = "-1分钟", tint = colors.accentWarm, modifier = Modifier.size(16.dp))
                            }
                            Text("分 (00-59)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 9.5.sp, color = colors.inkMuted)
                        }
                    }
                }

                HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

                // 底部操作栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = colors.inkSecondary, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onTimeSelected(LocalTime.of(tempHour, tempMinute, 0))
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
                            contentColor = colors.archivePaper
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("确定时刻", fontFamily = ArchiveFontFamilies.EditorialSerif, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * 散页独立日期槽位 (PaperDateField)
 * 彻底从混杂卡片中解耦，点击唤起专属古典纸墨日历选择页
 */
@Composable
fun PaperDateField(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now(FormatUtils.BeijingZone) }
    val isToday = selectedDate == today

    val displayDate = remember(selectedDate) {
        String.format(Locale.US, "%d.%02d.%02d", selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.archivePaper)
            .border(
                width = if (!isToday) 0.8.dp else 0.5.dp,
                color = if (!isToday) colors.accentWarm.copy(alpha = 0.5f) else colors.borderDivider,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { showDialog = true }
            .padding(horizontal = 10.dp, vertical = 8.5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = "选择日期",
                tint = if (!isToday) colors.accentWarm else colors.inkMuted,
                modifier = Modifier.size(13.dp)
            )
            if (!label.isNullOrBlank()) {
                Text(
                    text = "$label：",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }
            Text(
                text = displayDate,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 12.sp,
                fontWeight = if (!isToday) FontWeight.Medium else FontWeight.Normal,
                color = if (!isToday) colors.accentWarm else colors.inkPrimary
            )
        }
    }

    if (showDialog) {
        PaperDatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = onDateSelected,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 散页独立时刻槽位 (PaperTimeField)
 * 默认当前时刻，支持自定义精确到分，点击唤起纸墨时分微调器
 */
@Composable
fun PaperTimeField(
    selectedTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }

    val displayTime = remember(selectedTime) {
        String.format(Locale.US, "%02d:%02d", selectedTime.hour, selectedTime.minute)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.archivePaper)
            .border(
                width = 0.5.dp,
                color = colors.borderDivider,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { showDialog = true }
            .padding(horizontal = 10.dp, vertical = 8.5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = "选择时刻",
                tint = colors.accentWarm,
                modifier = Modifier.size(13.dp)
            )
            if (!label.isNullOrBlank()) {
                Text(
                    text = "$label：",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }
            Text(
                text = displayTime,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.inkPrimary
            )
        }
    }

    if (showDialog) {
        PaperTimePickerDialog(
            selectedTime = selectedTime,
            onTimeSelected = onTimeSelected,
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 散页拆解式日期与时间联合行 (PaperDateTimeSplitRow)
 * 左侧优雅古典日历，右侧精确时刻微调，彻底告别粗笨的复合选择器
 */
@Composable
fun PaperDateTimeSplitRow(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaperDateField(
            selectedDate = selectedDateTime.toLocalDate(),
            onDateSelected = { newDate ->
                onDateTimeSelected(
                    selectedDateTime
                        .withYear(newDate.year)
                        .withMonth(newDate.monthValue)
                        .withDayOfMonth(newDate.dayOfMonth)
                )
            },
            modifier = Modifier.weight(1.3f)
        )

        PaperTimeField(
            selectedTime = selectedDateTime.toLocalTime(),
            onTimeSelected = { newTime ->
                onDateTimeSelected(
                    selectedDateTime
                        .withHour(newTime.hour)
                        .withMinute(newTime.minute)
                        .withSecond(0)
                )
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 散页古典纸墨风时间选择器 (PaperDateTimePickerField)
 * 兼容原有调用入口，内部完全采用拆解式 PaperDateTimeSplitRow 呈现
 */
@Composable
fun PaperDateTimePickerField(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    PaperDateTimeSplitRow(
        selectedDateTime = selectedDateTime,
        onDateTimeSelected = onDateTimeSelected,
        modifier = modifier
    )
}

/**
 * 散页具体街巷地点输入槽位 (PaperLocationField)
 * 纯粹纸墨风格可选输入框，严格对齐 Web 端具体地点注记规范
 */
@Composable
fun PaperLocationField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "具体地点",
    placeholder: String = "街巷或地标注记 (选填)"
) {
    PaperTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * 散页纸墨风格下拉选择器 (PaperSelectField)
 * 纯原生 Compose 打造，替代丑陋的系统 Spinner，严格对齐 Web 端 PaperSelect
 */
@Composable
fun <T> PaperSelectField(
    selectedValue: T?,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "请选择…"
) {
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedValue }?.second

    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 11.5.sp,
                color = colors.inkMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(colors.archiveSubtle)
                .border(
                    width = if (selectedLabel != null) 0.8.dp else 0.5.dp,
                    color = if (selectedLabel != null) colors.accentWarm.copy(alpha = 0.4f) else colors.borderDivider,
                    shape = RoundedCornerShape(4.dp)
                )
                .clickable { showDialog = true }
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLabel ?: placeholder,
                    fontFamily = if (selectedLabel != null) ArchiveFontFamilies.EditorialSerif else ArchiveFontFamilies.BodySans,
                    fontSize = 12.5.sp,
                    color = if (selectedLabel != null) colors.inkPrimary else colors.inkMuted,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.archivePaper)
                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label ?: "请选择",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        )
                        IconButton(
                            onClick = { showDialog = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = colors.inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.5f))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        items(options) { (value, optLabel) ->
                            val isSelected = value == selectedValue
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) colors.accentWarm.copy(alpha = 0.12f) else colors.archivePaper)
                                    .clickable {
                                        onSelected(value)
                                        showDialog = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = optLabel,
                                    fontFamily = if (isSelected) ArchiveFontFamilies.EditorialSerif else ArchiveFontFamilies.BodySans,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isSelected) colors.accentWarm else colors.inkPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = colors.accentWarm,
                                        modifier = Modifier.size(15.dp)
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
