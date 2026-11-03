package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
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
        !enabled -> colors.inkMuted
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
    val contentColor = if (enabled) colors.archivePaper else colors.inkMuted.copy(alpha = 0.55f)

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
            .background(if (enabled) colors.accentWarm else colors.accentWarm.copy(alpha = 0.38f))
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
            color = colors.archivePaper
        )
    }
}
