package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 纯文字子页/分段切换：纯文字标签，选中项加粗 + 2dp 赤陶下划线，无框无底色
 * 支持可传入数量 counts（展示为紧随标签后的小号等宽数字，数量为 0 时隐藏）
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

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selectedIndex
            val count = counts?.getOrNull(index) ?: 0
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(role = Role.Tab) { onTabSelected(index) }
                    .padding(vertical = 4.dp)
                    .width(IntrinsicSize.Max)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp,
                        color = if (isSelected) colors.inkPrimary else colors.inkSecondary,
                        maxLines = 1
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = count.toString(),
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = colors.inkMuted,
                            maxLines = 1
                        )
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
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
