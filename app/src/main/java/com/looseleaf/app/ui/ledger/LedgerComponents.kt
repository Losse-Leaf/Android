package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.*
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
@Composable
internal fun TypeToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(12.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) colors.accentWarm else Color.Transparent, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.sp,
            color = if (isSelected) colors.archivePaper else colors.inkSecondary
        )
    }
}

/**
 * 记账页低频元数据的紧凑纸签胶囊（AOPT-01）：
 * 日期时刻 / 属地 / 分期 收拢成一行，点击展开「记账附加设置」抽屉。
 */
@Composable
internal fun LedgerExtraPill(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) colors.accentWarm.copy(alpha = 0.12f) else colors.archiveSubtle)
            .border(
                width = 0.5.dp,
                color = if (highlighted) colors.accentWarm else colors.borderDivider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontSize = 11.sp,
            color = if (highlighted) colors.accentWarm else colors.inkSecondary
        )
    }
}
