package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页时间流统一筛选栏：年份下拉选择 + 类型分类胶囊单行聚合
 * 避免原先双层滑动栏对手机垂直空间的严重压占
 */
@Composable
internal fun StreamFilterBar(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    availableYears: List<Int> = emptyList(),
    selectedYear: Int? = null,
    onYearSelected: ((Int?) -> Unit)? = null
) {
    val filters = listOf(
        "all" to "全部",
        "daily" to "日常",
        "travel" to "游历",
        "project" to "造物",
        "wish" to "心愿"
    )
    val colors = LooseLeafTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 年份快捷下拉胶囊 (若提供年份集)
        if (availableYears.isNotEmpty() && onYearSelected != null) {
            var showYearMenu by remember { mutableStateOf(false) }
            val hasYearSelected = selectedYear != null

            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (hasYearSelected) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle
                        )
                        .border(
                            0.5.dp,
                            if (hasYearSelected) colors.accentWarm else colors.borderDivider,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { showYearMenu = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (selectedYear != null) "${selectedYear}年" else "全部年份",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        fontWeight = if (hasYearSelected) FontWeight.Medium else FontWeight.Normal,
                        color = if (hasYearSelected) colors.accentWarm else colors.inkSecondary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = if (hasYearSelected) colors.accentWarm else colors.inkMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = showYearMenu,
                    onDismissRequest = { showYearMenu = false },
                    modifier = Modifier.background(colors.archivePaper)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "全部年份",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                color = if (selectedYear == null) colors.accentWarm else colors.inkPrimary
                            )
                        },
                        onClick = {
                            onYearSelected(null)
                            showYearMenu = false
                        }
                    )
                    for (year in availableYears) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${year}年",
                                    fontFamily = ArchiveFontFamilies.MonoScale,
                                    fontSize = 13.sp,
                                    color = if (selectedYear == year) colors.accentWarm else colors.inkPrimary
                                )
                            },
                            onClick = {
                                onYearSelected(year)
                                showYearMenu = false
                            }
                        )
                    }
                }
            }

            // 分隔竖线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(colors.borderDivider.copy(alpha = 0.6f))
            )
        }

        // 分类标签胶囊
        for ((key, label) in filters) {
            val isSelected = key == selectedFilter
            val shape = RoundedCornerShape(14.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(shape)
                    .background(if (isSelected) colors.inkPrimary else colors.archiveSubtle, shape)
                    .border(0.5.dp, if (isSelected) colors.inkPrimary else colors.borderDivider, shape)
                    .clickable { onFilterSelected(key) }
                    .padding(horizontal = 11.dp, vertical = 5.dp)
            ) {
                Text(
                    text = label,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.sp,
                    color = if (isSelected) colors.archivePaper else colors.inkSecondary
                )
            }
        }
    }
}

/** 兼容旧调用的空实现（已并入 StreamFilterBar） */
@Composable
internal fun YearFilterBar(
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit
) {
    // 已整合至 StreamFilterBar，无需独立占行渲染
}
