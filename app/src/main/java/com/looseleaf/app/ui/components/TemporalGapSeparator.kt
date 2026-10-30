package com.looseleaf.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 岁月留白刻度 (Temporal Gap Separators)
 * 纯文字居中，次要暖灰，小字号，去掉两侧线条
 * 放置于相邻两组日期之间，当间隔 >= 7 天时提示
 */
@Composable
fun TemporalGapSeparator(
    days: Long,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.temporal_gap_separator_text_tpl_01, days).trim(),
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.5.sp,
            letterSpacing = 0.8.sp,
            color = colors.inkMuted
        )
    }
}
