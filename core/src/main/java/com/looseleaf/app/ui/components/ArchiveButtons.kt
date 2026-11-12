package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一文字按钮 (ArchiveTextButton)
 * - 4dp 圆角、48dp 最小触控热区、衬线字体
 * - 普通操作用赤陶色，破坏性操作用低饱和砖红 (alertRed)，禁用时次要色
 */
@Composable
fun ArchiveTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    textColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val finalTextColor = textColor ?: when {
        !enabled -> colors.inkDisabled
        isDestructive -> colors.alertRed
        else -> colors.accentWarm
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(ArchiveShapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = if (enabled && textColor == null) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = ArchiveTypeScale.body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = finalTextColor
        )
    }
}

/**
 * 散页抽屉顶栏主动作按钮 (ArchiveSheetActionButton)
 * - 解决纯文字主操作辨识度过弱问题：启用时使用实心赤陶色（或砖红破坏色）+ 反白粗体，禁用时使用淡纸底灰字
 * - 保持外围 ≥48dp 触控热区
 */
@Composable
fun ArchiveSheetActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val bgColor = when {
        !enabled -> colors.archiveSubtle
        isDestructive -> colors.alertRed
        else -> colors.accentWarm
    }
    val contentColor = if (enabled) colors.archivePaper else colors.inkDisabled

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(ArchiveShapes.small)
                .background(bgColor)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Bold,
                fontSize = ArchiveTypeScale.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        }
    }
}

/**
 * 散页统一主按钮 (ArchivePrimaryButton)
 * - 实心赤陶底色、4dp 圆角、48dp 高度、纸白衬线文字 + 轻触感
 * - 禁用态：**不再是「赤陶 38% alpha + 纸白字」**（那是 ~2:1 对比，按钮像糊了一块脏色），
 *   改为「纸面底 + inkDisabled 字」—— 一眼看出"按不了"，且禁用时页面上不再出现赤陶。
 * - 禁用态仍需可见轮廓：`archiveSubtle` 与页面底 `archivePaper` 只差 1.04:1，
 *   只换填色等于"按钮凭空消失"。故补一条 1dp `lineDisabled`（墨 32%）描边 ——
 *   禁用控件的可辨识度靠**轮廓**而不是填色。它比输入框禁用下划线（lineEdge 60%，
 *   约 9.6% 墨）重一档：按钮是整块可点区域，需要更强的形状提示；
 *   若改成 borderDivider 那一档填色，48dp 高的灰板会被读成"已按下的深色按钮"，
 *   与启用态的赤陶实心语义打架。
 */
@Composable
fun ArchivePrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(ArchiveShapes.small)
            .background(if (enabled) colors.accentWarm else colors.archiveSubtle)
            .then(
                if (enabled) Modifier
                else Modifier.border(1.dp, colors.lineDisabled, ArchiveShapes.small)
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = ArchiveTypeScale.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) colors.archivePaper else colors.inkDisabled
        )
    }
}
