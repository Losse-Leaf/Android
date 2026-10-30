package com.looseleaf.app.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 全页统一 1dp 淡暖灰实线 (ArchiveLines.hairline)
 */
@Composable
fun ArchiveDivider(
    modifier: Modifier = Modifier,
    color: Color? = null,
    thickness: Dp = ArchiveLines.hairline
) {
    val dividerColor = color ?: LooseLeafTheme.colors.borderSubtle
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = dividerColor
    )
}
