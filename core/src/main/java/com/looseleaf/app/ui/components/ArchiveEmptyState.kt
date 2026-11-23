package com.looseleaf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 空状态 (ArchiveEmptyState)
 * - 版面重心落在内容区上三分之一
 * - 默认插画为「三张错落的散页 + 一枚朱砂小印」几何线稿；传入图标时改为凹槽圆底图标
 * - 衬线标题 + 无衬线说明 + 朱砂胶囊行动按钮
 */
@Composable
fun ArchiveEmptyState(
    title: String,
    subtitle: String? = null,
    icon: Painter? = null,
    imageVector: ImageVector? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        when {
            imageVector != null || icon != null -> {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(ArchiveShapes.full)
                        .background(colors.archiveSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageVector != null) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (icon != null) {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            else -> FolioLeavesOrnament()
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = title,
            style = ArchiveTextStyles.essayTitle,
            color = colors.inkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp)
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = ArchiveTextStyles.body,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(26.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.accentWarm)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onActionClick()
                        }
                    )
                    .padding(horizontal = 26.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionText,
                    style = ArchiveTextStyles.button,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onAccent
                )
            }
        }
    }
}

/**
 * 散页插画：三张以不同角度叠放的纸，最上一张带两行字迹与一枚朱砂小印。
 */
@Composable
fun FolioLeavesOrnament(modifier: Modifier = Modifier) {
    val colors = LooseLeafTheme.colors
    val back = colors.archiveSubtle
    val mid = colors.borderDivider
    val front = colors.archiveCard
    val line = colors.inkMuted.copy(alpha = 0.55f)
    val seal = colors.accentWarm

    Canvas(modifier = modifier.size(width = 96.dp, height = 84.dp)) {
        val w = 54.dp.toPx()
        val h = 66.dp.toPx()
        val r = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        val origin = Offset((size.width - w) / 2f, (size.height - h) / 2f)
        val stroke = 1.2.dp.toPx()

        rotate(degrees = -14f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawRoundRect(color = back, topLeft = origin, size = Size(w, h), cornerRadius = r)
        }
        rotate(degrees = 8f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawRoundRect(color = mid, topLeft = origin, size = Size(w, h), cornerRadius = r, style = Stroke(stroke))
        }
        drawRoundRect(color = front, topLeft = origin, size = Size(w, h), cornerRadius = r)
        drawRoundRect(color = line.copy(alpha = 0.35f), topLeft = origin, size = Size(w, h), cornerRadius = r, style = Stroke(stroke))

        val pad = 10.dp.toPx()
        drawLine(
            color = line,
            start = Offset(origin.x + pad, origin.y + h * 0.34f),
            end = Offset(origin.x + w - pad, origin.y + h * 0.34f),
            strokeWidth = stroke
        )
        drawLine(
            color = line.copy(alpha = 0.4f),
            start = Offset(origin.x + pad, origin.y + h * 0.50f),
            end = Offset(origin.x + w - pad * 1.8f, origin.y + h * 0.50f),
            strokeWidth = stroke
        )
        val sealSize = 11.dp.toPx()
        drawRoundRect(
            color = seal,
            topLeft = Offset(origin.x + w - pad - sealSize, origin.y + h - pad - sealSize),
            size = Size(sealSize, sealSize),
            cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
        )
    }
}
