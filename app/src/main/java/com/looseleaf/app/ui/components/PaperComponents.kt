package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 纸页卡片 (PaperCard)：画布上一张更亮的纸，圆角默认取「散页」档。
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val bg = backgroundColor ?: colors.archiveCard
    val border = borderColor ?: colors.borderCard
    val shape = remember(cornerRadius) {
        if (cornerRadius == 20.dp) ArchiveShapes.leaf else RoundedCornerShape(cornerRadius)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg, shape)
            .border(ArchiveLines.hairline, border, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

/**
 * 小徽标 (PaperBadge)：全圆角胶囊，等宽数字友好。
 */
@Composable
fun PaperBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    textColor: Color? = null
) {
    val colors = LooseLeafTheme.colors
    val bg = containerColor ?: colors.archiveSubtle
    val fg = textColor ?: colors.inkSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ArchiveShapes.full)
            .background(bg, ArchiveShapes.full)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = ArchiveTextStyles.meta,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}
