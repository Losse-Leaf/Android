package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一纸墨条目骨架 (ArchiveItemRow)
 * 严格遵循规范：
 * - 第一行：标题（衬线 SemiBold，单行省略）+ 行尾次要信息
 * - 第二行：描述（次要色，最多 2 行）
 * - 第三行：元信息（次要色小字，数字用等宽）
 * - 条目之间 1dp 淡线，左缘对齐内容起点；条目上下内边距 16dp
 * - 无卡片、无描边、无填充；按压态为略亮一档的纸色 (archiveCard)
 */
@Composable
fun ArchiveItemRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingInfo: @Composable (() -> Unit)? = null,
    description: @Composable (() -> Unit)? = null,
    metadata: @Composable (() -> Unit)? = null,
    extraContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgColor = if (isPressed) colors.archiveCard else Color.Transparent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // 第一行：标题 + 行尾次要信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (trailingInfo != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    trailingInfo()
                }
            }

            // 第二行：描述（次要色，最多 2 行）
            if (description != null) {
                Spacer(modifier = Modifier.height(6.dp))
                description()
            }

            // 第三行：元信息（次要色小字，数字用等宽）
            if (metadata != null) {
                Spacer(modifier = Modifier.height(6.dp))
                metadata()
            }

            // 额外内容（如造物的 GitHub 链接）
            if (extraContent != null) {
                Spacer(modifier = Modifier.height(8.dp))
                extraContent()
            }
        }

        // 条目之间 1dp 淡线：左缘对齐内容起点 (start = 16.dp)，右缘对齐右边距 (end = 16.dp)，不顶到屏幕边
        if (showDivider) {
            ArchiveDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
        }
    }
}

@Composable
fun ArchiveItemDescription(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2
) {
    val colors = LooseLeafTheme.colors
    Text(
        text = text,
        fontFamily = ArchiveFontFamilies.EditorialSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = colors.inkSecondary,
        modifier = modifier
    )
}

@Composable
fun ArchiveItemMetadata(
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Text(
        text = text,
        fontFamily = ArchiveFontFamilies.MonoScale,
        fontSize = 12.sp,
        color = colors.inkMuted,
        modifier = modifier
    )
}
