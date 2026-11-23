package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 列表条目 (ArchiveItemRow)
 * - 第一行：衬线标题（单行省略）+ 行尾信息
 * - 第二行：描述（次要色，最多 2 行）
 * - 第三行：元信息（等宽数字小字）
 * - 条目之间一条与版心对齐的发丝线；按压时整行浮起一层极淡墨色
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
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) colors.pressOverlay else Color.Transparent,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "itemRowPress"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ArchiveSpacing.gutter, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = ArchiveTextStyles.essayTitle.copy(
                        fontSize = ArchiveTextStyles.rowTitle.fontSize,
                        lineHeight = ArchiveTextStyles.rowTitle.lineHeight
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (trailingInfo != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    trailingInfo()
                }
            }

            if (description != null) {
                Spacer(modifier = Modifier.height(6.dp))
                description()
            }

            if (metadata != null) {
                Spacer(modifier = Modifier.height(8.dp))
                metadata()
            }

            if (extraContent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                extraContent()
            }
        }

        if (showDivider) {
            ArchiveDivider(modifier = Modifier.padding(horizontal = ArchiveSpacing.gutter))
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
        style = ArchiveTextStyles.body,
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
        style = ArchiveTextStyles.meta,
        color = colors.inkMuted,
        modifier = modifier
    )
}
