package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页统一轻量提示数据
 */
data class ArchiveSnackbarData(
    val message: String,
    val actionLabel: String? = null,
    val onActionClick: (() -> Unit)? = null
)

@Composable
fun ArchiveSnackbar(
    data: ArchiveSnackbarData,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    ArchiveSnackbar(
        message = data.message,
        actionLabel = data.actionLabel,
        onActionClick = {
            data.onActionClick?.invoke()
            onDismiss?.invoke()
        },
        modifier = modifier
    )
}

/**
 * 散页统一底栏浮层轻提示 (ArchiveSnackbar)
 * - 呈现于底栏上方，纸色微纸层底、4dp 圆角、1dp 淡暖灰实线边框
 * - 左侧说明文字，右侧赤陶文字操作，无圆点
 */
@Composable
fun ArchiveSnackbar(
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.radiusSmall)
            .background(if (colors.isDark) Color(0xFF22201D) else Color(0xFFECE5D8))
            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.radiusSmall)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 14.sp,
            color = colors.inkPrimary
        )
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Text(
                text = actionLabel,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.accentWarm,
                modifier = Modifier
                    .clip(ArchiveShapes.radiusSmall)
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
