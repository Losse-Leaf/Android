package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一沉浸式顶栏 (ArchiveTopAppBar)
 */
@Composable
fun ArchiveTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            navigationIcon?.invoke()
            Column {
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = ArchiveTypeScale.h2,
                    letterSpacing = 1.sp,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = ArchiveTypeScale.caption,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            actions()
        }
    }
}

/**
 * 散页统一分区标题栏 (ArchiveSectionHeader)
 * - 标题左对齐（衬线字体 SemiBold，单行省略防挤压）
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
            fontSize = ArchiveTypeScale.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ArchiveTextButton(
                text = actionText,
                onClick = onActionClick
            )
        }
    }
}
