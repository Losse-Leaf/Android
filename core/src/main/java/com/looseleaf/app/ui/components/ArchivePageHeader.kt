package com.looseleaf.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 一级页头 (ArchivePageHeader)
 *
 * - 左：书页声部的大标题（衬线 28sp）；副标题在其下方
 * - 副标题可点（如账本月份）时，渲染为一枚凹槽胶囊 + 向下箭头，明确「这里能切换」
 * - 右：actions，间距 4dp
 * 系统栏内边距由宿主统一负责，这里不处理 insets。
 */
@Composable
fun ArchivePageHeader(
    title: String,
    subtitle: String? = null,
    /** 副标题可点（如账本的月份选择器）；传 null 则退化为纯文本 */
    onSubtitleClick: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ArchiveSpacing.gutter, end = 12.dp, top = 14.dp, bottom = 10.dp),
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
                    style = ArchiveTextStyles.pageTitle,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (onSubtitleClick != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(ArchiveShapes.full)
                                .background(colors.archiveSubtle)
                                .clickable(role = Role.Button, onClick = onSubtitleClick)
                                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                        ) {
                            Text(
                                text = subtitle,
                                style = ArchiveTextStyles.label,
                                color = colors.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = FolioGlyphs.ChevronDown,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Text(
                            text = subtitle,
                            style = ArchiveTextStyles.meta,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
 * 页头圆形图标按钮 (ArchiveHeaderIconButton)
 * 40dp 圆形热区，按下浮现凹槽色圆底；[highlighted] 为真时常驻凹槽底，表示「该功能已启用」。
 */
@Composable
fun ArchiveHeaderIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    tint: Color? = null,
    highlighted: Boolean = false,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 21.dp,
    badge: (@Composable () -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = when {
            isPressed -> colors.archiveSubtle
            highlighted -> colors.archiveSubtle
            else -> Color.Transparent
        },
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "headerIconBg"
    )
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(ArchiveShapes.full)
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val iconTint = tint ?: colors.inkPrimary
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        } else if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(iconSize)
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
            ) {
                badge()
            }
        }
    }
}
