package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 12.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val bg = backgroundColor ?: colors.archiveSubtle
    val border = borderColor ?: colors.borderDivider
    val shape = remember(cornerRadius) {
        if (cornerRadius == 12.dp) ArchiveShapes.medium else RoundedCornerShape(cornerRadius)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, shape)
            .border(ArchiveLines.hairline, border, shape)
            .padding(contentPadding)
    ) {
        content()
    }
}

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
            .clip(ArchiveShapes.small)
            .background(bg, ArchiveShapes.small)
            .border(ArchiveLines.hairline, colors.borderSubtle, ArchiveShapes.small)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = ArchiveTypeScale.micro,
            letterSpacing = 0.5.sp,
            color = fg
        )
    }
}
