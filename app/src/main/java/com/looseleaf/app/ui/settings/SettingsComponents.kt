package com.looseleaf.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveDivider
import com.looseleaf.app.ui.components.FolioGlyphs

/**
 * 键值信息行：左标签（次要色）、右数值（等宽数字）。
 */
@Composable
internal fun ProfileInfoRow(label: String, value: String) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = ArchiveTextStyles.body, color = colors.inkSecondary)
        Text(
            text = value,
            style = ArchiveTextStyles.meta,
            fontWeight = FontWeight.Medium,
            color = colors.inkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 当前登录账号摘要行：账号徽标 + 昵称 + 「已登录」标识 + 邮箱/服务器信息。
 */
@Composable
internal fun AccountIdentityRow(
    displayName: String,
    subtitle: String
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(ArchiveShapes.md)
                .background(colors.positiveTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FolioGlyphs.Person,
                contentDescription = null,
                tint = colors.accentMoss,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = ArchiveTextStyles.rowTitle,
                    color = colors.inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(ArchiveShapes.full)
                        .background(colors.positiveTint)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "已登录",
                        style = ArchiveTextStyles.meta,
                        fontWeight = FontWeight.Medium,
                        color = colors.accentMoss,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = ArchiveTextStyles.meta,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 设置分组：上方加宽字距的小标，下方一张纸页卡片承载若干行，行间以内缩发丝线分隔。
 */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = ArchiveTextStyles.overline,
            color = colors.inkMuted,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ArchiveShapes.leaf)
                .background(colors.archiveCard)
                .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.leaf),
            content = content
        )
    }
}

/** 分组卡片内的行间分隔 */
@Composable
internal fun SettingsRowDivider() {
    ArchiveDivider(modifier = Modifier.padding(start = 18.dp))
}
