package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一分区标题栏 (ArchiveSectionHeader)
 * - 标题左对齐（衬线字体 SemiBold）
 * - 右侧可选文字操作按钮
 * - 40dp 最小高度
 */
@Composable
fun ArchiveSectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = colors.inkPrimary
        )
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            ArchiveTextButton(
                text = actionText,
                onClick = onActionClick
            )
        }
    }
}
