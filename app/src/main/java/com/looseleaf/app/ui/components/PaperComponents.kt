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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.platform.LocalContext
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDateTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
    placeholder: String = "选择属地城市…",
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
                        placeholder = "输入城市名、省份或 6 位代码…",
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
 * 散页古典纸墨风时间选择器 (PaperDateTimePickerField)
 * 彻底对齐 Web/macOS 端的时间回溯补记能力，支持自然时间快捷芯片与精确时空选择
 */
@Composable
fun PaperDateTimePickerField(
    selectedDateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val context = LocalContext.current
    val colors = LooseLeafTheme.colors
    var showDialog by remember { mutableStateOf(false) }

    val isToday = remember(selectedDateTime) {
        FormatUtils.isTodayInBeijing(selectedDateTime)
    }
    val displayStr = remember(selectedDateTime) {
        FormatUtils.formatLocalDateTimeDisplay(selectedDateTime)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.archivePaper)
            .border(
                0.5.dp,
                if (!isToday) colors.accentWarm.copy(alpha = 0.6f) else colors.borderDivider,
                RoundedCornerShape(4.dp)
            )
            .clickable { showDialog = true }
            .padding(horizontal = 9.dp, vertical = 6.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.DateRange,
            contentDescription = "选择发生时刻",
            tint = if (!isToday) colors.accentWarm else colors.inkMuted,
            modifier = Modifier.size(14.dp)
        )
        if (!label.isNullOrBlank()) {
            Text(
                text = "$label：",
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 11.5.sp,
                color = colors.inkMuted
            )
        }
        Text(
            text = displayStr,
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (!isToday) colors.accentWarm else colors.inkPrimary
        )
        if (!isToday) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accentWarm.copy(alpha = 0.12f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "补记",
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 9.5.sp,
                    color = colors.accentWarm
                )
            }
        }
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            PaperCard(
                backgroundColor = colors.archivePaper,
                borderColor = colors.borderSubtle,
                cornerRadius = 6.dp,
                contentPadding = PaddingValues(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "回溯补记 · 发生时间",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = "当前选择：$displayStr",
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
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

                    HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

                    Text(
                        text = "快捷时空锚点",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )

                    // 快捷时空芯片网格
                    val now = remember { LocalDateTime.now(FormatUtils.BeijingZone) }
                    val shortcuts = listOf(
                        "此时此刻" to now,
                        "今日 09:00" to now.withHour(9).withMinute(0).withSecond(0),
                        "今日 14:00" to now.withHour(14).withMinute(0).withSecond(0),
                        "今日 20:00" to now.withHour(20).withMinute(0).withSecond(0),
                        "昨日此时" to now.minusDays(1),
                        "3 天前" to now.minusDays(3)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        shortcuts.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { (title, dt) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.archiveSubtle)
                                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                                            .clickable {
                                                onDateTimeSelected(dt)
                                                showDialog = false
                                            }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = title,
                                            fontFamily = ArchiveFontFamilies.BodySans,
                                            fontSize = 11.sp,
                                            color = colors.inkPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.borderDivider, thickness = 0.5.dp)

                    // 自选精确日期与时刻按钮
                    Button(
                        onClick = {
                            showDialog = false
                            val year = selectedDateTime.year
                            val month = selectedDateTime.monthValue - 1
                            val day = selectedDateTime.dayOfMonth
                            val hour = selectedDateTime.hour
                            val minute = selectedDateTime.minute

                            val datePicker = android.app.DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val timePicker = android.app.TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            val newDt = LocalDateTime.of(y, m + 1, d, h, min, 0)
                                            onDateTimeSelected(newDt)
                                        },
                                        hour,
                                        minute,
                                        true
                                    )
                                    timePicker.show()
                                },
                                year,
                                month,
                                day
                            )
                            datePicker.show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.paperTertiary,
                            contentColor = colors.inkPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "自选精确日期与时刻…",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

