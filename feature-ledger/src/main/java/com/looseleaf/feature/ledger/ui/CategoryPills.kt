package com.looseleaf.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.SpectrumColors
import com.looseleaf.feature.ledger.data.DefaultExpenseCategories
import com.looseleaf.feature.ledger.theme.LedgerTheme

private val PillShape = RoundedCornerShape(50)

/**
 * 账本分类胶囊横滑栏 (Category Pills Bar)
 * 采用现代极简金融设计，支持横向平滑滚动与当前选中项对齐。
 */
@Composable
fun CategoryPills(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<String> = DefaultExpenseCategories
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedCategory, categories) {
        val index = categories.indexOf(selectedCategory)
        if (index >= 0) {
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
            val spectrumColor = SpectrumColors.colorForCategory(cat, LedgerTheme.colors.isDark)
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
    val colors = LedgerTheme.colors
    val shape = PillShape

    val containerBg = if (isSelected) {
        colors.accent.copy(alpha = 0.12f)
    } else {
        colors.surfaceElevated
    }

    val borderColor = if (isSelected) {
        colors.accent
    } else {
        colors.borderSubtle
    }

    val textColor = if (isSelected) {
        colors.accent
    } else {
        colors.textSecondary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(shape)
            .background(containerBg, shape)
            .border(width = if (isSelected) 1.dp else 0.5.dp, color = borderColor, shape = shape)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics { stateDescription = if (isSelected) "已选中" else "未选中" }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(spectrumColor)
        )
        Text(
            text = category,
            style = LedgerTheme.typography.caption,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
