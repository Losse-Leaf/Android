package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
@Composable
internal fun StreamFilterBar(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    val filters = listOf(
        "all" to "全部印记",
        "daily" to "日常随笔",
        "travel" to "山川游历",
        "project" to "造物日志",
        "wish" to "心愿印记"
    )
    val colors = LooseLeafTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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

@Composable
internal fun YearFilterBar(
    availableYears: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit
) {
    if (availableYears.isEmpty()) return
    val colors = LooseLeafTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "年份",
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontSize = 11.sp,
            color = colors.inkMuted,
            modifier = Modifier.padding(end = 4.dp)
        )

        // 全部年份
        val isAll = selectedYear == null
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isAll) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle)
                .border(0.5.dp, if (isAll) colors.accentWarm else colors.borderDivider, RoundedCornerShape(10.dp))
                .clickable { onYearSelected(null) }
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "全集",
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 11.sp,
                color = if (isAll) colors.accentWarm else colors.inkSecondary
            )
        }

        for (year in availableYears) {
            val isSelected = selectedYear == year
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.accentWarm.copy(alpha = 0.15f) else colors.archiveSubtle)
                    .border(0.5.dp, if (isSelected) colors.accentWarm else colors.borderDivider, RoundedCornerShape(10.dp))
                    .clickable { onYearSelected(year) }
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${year}年",
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = 11.sp,
                    color = if (isSelected) colors.accentWarm else colors.inkSecondary
                )
            }
        }
    }
}
