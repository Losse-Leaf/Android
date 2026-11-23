package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 岁月留白刻度 (TemporalGapSeparator)
 * 两组日期相隔 ≥ 7 天时出现：两段向中心收拢的发丝线夹着一行衬线小字，中间一枚菱形节点。
 */
@Composable
fun TemporalGapSeparator(
    days: Long,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.borderSubtle)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(ArchiveShapes.xs)
                .background(colors.inkMuted.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.temporal_gap_separator_text_tpl_01, days).trim(),
            style = ArchiveTextStyles.essay.copy(
                fontSize = ArchiveTextStyles.meta.fontSize,
                lineHeight = ArchiveTextStyles.meta.lineHeight
            ),
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(ArchiveShapes.xs)
                .background(colors.inkMuted.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.borderSubtle)
        )
    }
}
