package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 纯文字子页/分段切换：纯文字标签，选中项加粗 + 2dp 赤陶下划线，无框无底色
 * 支持可传入数量 counts（展示为小圆角徽标，数量为 0 时隐藏，与标题文字明确区分）
 */
@Composable
fun ArchiveSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    counts: List<Int>? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val count = counts?.getOrNull(index) ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                // 热区修复（第 1 批）：此前 clickable 只包住文字列，可视高约 32dp，
                // 手指点偏一点就落空。外层 heightIn(44dp) + 垂直居中，保证热区 ≥44dp，
                // 同时不改变文字与下划线的相对位置（内容整体居中）。
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Tab) {
                        if (!isSelected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onTabSelected(index)
                    }
                    .padding(vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = ArchiveTypeScale.bodyLarge,
                        color = if (isSelected) colors.inkPrimary else colors.inkSecondary,
                        maxLines = 1
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .defaultMinSize(minWidth = 18.dp)
                                .clip(ArchiveShapes.full)
                                .background(
                                    // 底与数字同族（accentSmallText），否则选中徽标会出现
                                    // 「深字压浅赤陶底」两种赤陶并排
                                    if (isSelected) colors.accentSmallText.copy(alpha = 0.14f)
                                    else colors.archiveSubtle
                                )
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = ArchiveTypeScale.micro,
                                lineHeight = ArchiveTypeScale.micro,
                                color = if (isSelected) colors.accentSmallText else colors.inkMuted,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    fontFeatureSettings = "tnum"
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(colors.accentWarm)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

/**
 * 散页类型文字切换（选中加粗 + 2dp 赤陶下划线，无底色）
 */
@Composable
fun ArchiveTypeToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                role = Role.Tab,
                onClick = {
                    if (!isSelected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = ArchiveTypeScale.body,
            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(colors.accentWarm)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
