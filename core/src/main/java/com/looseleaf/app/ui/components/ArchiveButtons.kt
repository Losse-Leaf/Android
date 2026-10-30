package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
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
            .clip(ArchiveShapes.radiusSmall)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = finalTextColor
        )
    }
}

/**
 * 散页统一主按钮 (ArchivePrimaryButton)
 * - 实心赤陶底色、4dp 圆角、48dp 高度、白色衬线文字
 */
@Composable
fun ArchivePrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(ArchiveShapes.radiusSmall)
            .background(if (enabled) colors.accentWarm else colors.accentWarm.copy(alpha = 0.38f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color.White
        )
    }
}
