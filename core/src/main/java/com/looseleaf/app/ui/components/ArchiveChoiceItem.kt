package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一选项条目 (ArchiveChoiceItem)
 * - 6dp 色点（可选）+ 衬线文字
 * - 选中加粗 + 2dp 赤陶下划线
 * - 无边框无底色、48dp 最小点击热区
 */
@Composable
fun ArchiveChoiceItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colorDot: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(ArchiveShapes.small)
            .clickable(
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (colorDot != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colorDot)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = ArchiveTypeScale.body,
                color = if (isSelected) colors.inkPrimary else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(if (isSelected) colors.accentWarm else Color.Transparent)
        )
    }
}

/**
 * 全端统一「可选胶囊」组件 (ArchiveSelectableCapsule)
 * - 统一采用 ArchiveShapes.full 全圆角胶囊形态
 * - 选中态：1dp 赤陶色边 + 12% 赤陶淡染底 + 加粗字
 * - 未选态：1dp 暖灰细边 + 暖纸微层底 + 次要字
 * - 单行省略 + 轻触感反馈
 */
@Composable
fun ArchiveSelectableCapsule(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingDotColor: Color? = null,
    isMonospace: Boolean = false,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current
    val activeColor = accentColor ?: colors.accentWarm

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .clip(ArchiveShapes.full)
            .background(if (selected) activeColor.copy(alpha = 0.12f) else colors.archiveSubtle)
            .border(
                width = ArchiveLines.hairline,
                color = if (selected) activeColor else colors.borderSubtle,
                shape = ArchiveShapes.full
            )
            .clickable(
                role = Role.RadioButton,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(leadingDotColor)
                )
            }
            Text(
                text = text,
                fontFamily = if (isMonospace) ArchiveFontFamilies.MonoScale else ArchiveFontFamilies.EditorialSerif,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = ArchiveTypeScale.body,
                color = if (selected) activeColor else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
