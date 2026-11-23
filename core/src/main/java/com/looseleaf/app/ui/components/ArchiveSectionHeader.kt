package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 分区标题 (ArchiveSectionHeader)
 * 一枚 12dp 朱砂短线 + 加宽字距的小标，右侧可选文字动作。
 */
@Composable
fun ArchiveSectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(2.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.accentWarm)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = ArchiveTextStyles.overline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colors.inkSecondary
            )
        }
        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ArchiveTextButton(
                text = actionText,
                onClick = onActionClick
            )
        }
    }
}
