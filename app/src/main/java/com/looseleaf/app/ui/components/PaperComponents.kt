package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 手账质感纸张卡片容器
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val bg = backgroundColor ?: colors.archiveSubtle
    val border = borderColor ?: colors.borderDivider
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, border, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 陶土赭石色高对比印章按钮 (Terracotta Stamp Button)
 * 专为「盖章记下」与核心主干提交操作打造，模拟物理木印章的厚重沉稳感
 */
@Composable
fun TerracottaStampButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(2.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentWarm,
            contentColor = colors.archivePaper,
            disabledContainerColor = colors.inkMuted.copy(alpha = 0.3f),
            disabledContentColor = colors.archivePaper.copy(alpha = 0.7f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            letterSpacing = 2.sp
        )
    }
}

/**
 * 纸质分类与状态标签 (Paper Badge)
 */
@Composable
fun PaperBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    textColor: Color? = null,
    isPendingSync: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    val bg = containerColor ?: if (isPendingSync) colors.accentWarm.copy(alpha = 0.12f) else colors.archiveSubtle
    val fg = textColor ?: if (isPendingSync) colors.accentWarm else colors.inkSecondary
    val shape = RoundedCornerShape(2.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .border(0.5.dp, colors.borderDivider, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        if (isPendingSync) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.accentWarm)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = fg
        )
    }
}

/**
 * 典雅极细折痕线
 */
@Composable
fun PaperDivider(
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val dividerColor = color ?: LooseLeafTheme.colors.borderDivider
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = dividerColor
    )
}
