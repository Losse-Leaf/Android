package com.looseleaf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 岁月留白刻度 (Temporal Gap Separators)
 * 严格遵循 docs/ecosystem.md §4.1 约定：
 * 当相邻记录时间跨度超过 14 天时，插入留白与刻度线，还原实体手账的岁月沉淀
 */
@Composable
fun TemporalGapSeparator(
    days: Long,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val dividerColor = colors.borderDivider

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.weight(1f)) {
            val y = size.height / 2f
            drawLine(
                color = dividerColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        Text(
            text = " 历经 $days 天的沉默与沉淀 ",
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = colors.inkMuted,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Canvas(modifier = Modifier.weight(1f)) {
            val y = size.height / 2f
            drawLine(
                color = dividerColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
    }
}
