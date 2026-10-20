package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.local.DefaultExpenseCategories
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.theme.SpectrumColors
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalTime
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.LaunchedEffect

/** MOB-P1-25：分类胶囊形状常量 */
private val PillShape = RoundedCornerShape(16.dp)

/**
 * 分类胶囊横滑栏 (Category Pills Bar) - docs/mobile.md §3.1
 * 严格遵从 docs/web.md §2.3 Earthy Spectrum 光谱色与动态分类字典 (RC-129)
 */
@Composable
fun CategoryPills(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<String> = DefaultExpenseCategories
) {
    val scrollState = rememberScrollState()
    // MOB-P2-74：选中项自动滚入视野 ——
    // 分类多时（10+ 项），当前选中的胶囊可能在屏幕外，
    // 用户不知道「现在选的是哪个」，也无法直接点它取消。
    LaunchedEffect(selectedCategory, categories) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
            // 粗略估算：每个胶囊约 84dp 宽 + 8dp 间距
            scrollState.animateScrollTo((index * 92 - 120).coerceAtLeast(0))
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (cat in categories) {
            val isSelected = cat == selectedCategory
            val spectrumColor = SpectrumColors.colorForCategory(cat, LooseLeafTheme.colors.isDark)
            CategoryPillItem(
                category = cat,
                isSelected = isSelected,
                spectrumColor = spectrumColor,
                onClick = { onCategorySelect(cat) }
            )
        }
    }
}

@Composable
private fun CategoryPillItem(
    category: String,
    isSelected: Boolean,
    spectrumColor: Color,
    onClick: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    // MOB-P1-25：形状常量（原先每次重组都新建，触发绘制失效）
    val shape = PillShape
    // 分类切换的轻触反馈（AOPT-05a）
    val haptics = LocalHapticFeedback.current

    val containerBg = if (isSelected) {
        spectrumColor.copy(alpha = 0.15f)
    } else {
        colors.archiveSubtle
    }

    val borderColor = if (isSelected) {
        spectrumColor
    } else {
        colors.borderDivider
    }

    val textColor = if (isSelected) {
        if (colors.isDark) colors.inkPrimary else spectrumColor
    } else {
        colors.inkSecondary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(containerBg, shape)
            .border(if (isSelected) 1.dp else 0.5.dp, borderColor, shape)
            // MOB-P1-77：选中态语义 + 热区下限（原本只有 ~30dp）
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics { stateDescription = if (isSelected) "已选中" else "" }
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        // 色谱光点
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(spectrumColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = category,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            fontSize = 13.sp,
            color = textColor
        )
    }
}

/**
 * 根据当前北京时间推断推荐的常用分类 (docs/mobile.md §3.1, RC-134)
 */
fun inferRecommendedCategory(): String {
    val hour = LocalTime.now(FormatUtils.BeijingZone).hour
    return when (hour) {
        in 6..10 -> "餐饮"
        in 11..13 -> "餐饮"
        in 17..19 -> "交通"
        in 20..22 -> "娱乐"
        else -> "餐饮"
    }
}
