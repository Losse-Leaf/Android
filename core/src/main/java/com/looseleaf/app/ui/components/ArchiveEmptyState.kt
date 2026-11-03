package com.looseleaf.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一空状态：上移至内容区上 1/3 + 线稿纸签元素 + 实心赤陶 CTA 按钮
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
            .padding(top = 56.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        when {
            imageVector != null -> {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = colors.inkMuted.copy(alpha = 0.55f),
                    modifier = Modifier.size(36.dp)
                )
            }
            icon != null -> {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = colors.inkMuted.copy(alpha = 0.55f),
                    modifier = Modifier.size(36.dp)
                )
            }
            else -> {
                ArchivePaperSlipOrnament()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.bodyLarge,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = ArchiveTypeScale.caption,
                color = colors.inkMuted,
                textAlign = TextAlign.Center
            )
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 40.dp)
                    .clip(ArchiveShapes.small)
                    .background(colors.accentWarm)
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onActionClick()
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionText,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = ArchiveTypeScale.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.archivePaper
                )
            }
        }
    }
}

/**
 * 折角纸签线稿插画：克制的 1.2dp 墨线折页 + 一枚赤陶小书签印记
 */
@Composable
private fun ArchivePaperSlipOrnament() {
    val colors = LooseLeafTheme.colors
    val lineColor = colors.inkMuted.copy(alpha = 0.42f)
    val accentColor = colors.accentWarm.copy(alpha = 0.72f)

    Canvas(modifier = Modifier.size(width = 36.dp, height = 44.dp)) {
        val stroke = 1.25.dp.toPx()
        val fold = 9.dp.toPx()
        val w = size.width
        val h = size.height

        val outline = Path().apply {
            moveTo(0f, 0f)
            lineTo(w - fold, 0f)
            lineTo(w, fold)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = outline,
            color = lineColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val dogEar = Path().apply {
            moveTo(w - fold, 0f)
            lineTo(w - fold, fold)
            lineTo(w, fold)
        }
        drawPath(
            path = dogEar,
            color = lineColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 顶部左侧赤陶小书签贴条
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(6.dp.toPx(), 0f),
            size = Size(5.dp.toPx(), 9.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
        )

        // 纸面两道极淡横格线
        drawLine(
            color = lineColor.copy(alpha = 0.28f),
            start = Offset(7.dp.toPx(), h * 0.52f),
            end = Offset(w - 7.dp.toPx(), h * 0.52f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = lineColor.copy(alpha = 0.28f),
            start = Offset(7.dp.toPx(), h * 0.70f),
            end = Offset(w - 13.dp.toPx(), h * 0.70f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
