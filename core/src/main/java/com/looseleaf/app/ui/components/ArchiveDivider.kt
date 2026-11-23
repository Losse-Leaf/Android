package com.looseleaf.app.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 发丝分隔线 (ArchiveDivider)：默认取最淡的一档分隔色，只在确需分组时出现。
 */
@Composable
fun ArchiveDivider(
    modifier: Modifier = Modifier,
    color: Color? = null,
    thickness: Dp = ArchiveLines.hairline
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color ?: LooseLeafTheme.colors.borderSubtle
    )
}
