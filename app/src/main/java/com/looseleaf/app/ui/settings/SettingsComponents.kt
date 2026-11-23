package com.looseleaf.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveDivider
import com.looseleaf.app.utils.FormatUtils

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
 * 受信任设备卡片：设备方印 + 名称 + 「本机」标签；下行为标识与活跃时间；右侧两个文字动作。
 */
@Composable
internal fun TrustedDeviceRow(
    deviceName: String,
    deviceId: String,
    platform: String,
    isLocal: Boolean,
    lastActiveAt: String? = null,
    onRename: () -> Unit,
    onRevokeOrUnpair: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val platformIcon = painterResource(R.drawable.ic_smartphone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(ArchiveShapes.md)
                    .background(if (isLocal) colors.positiveTint else colors.archiveSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = platformIcon,
                    contentDescription = null,
                    tint = if (isLocal) colors.accentMoss else colors.inkMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deviceName,
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
                            .background(if (isLocal) colors.positiveTint else colors.archiveSubtle)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isLocal) "本机" else platform.uppercase(),
                            style = ArchiveTextStyles.meta,
                            fontWeight = FontWeight.Medium,
                            color = if (isLocal) colors.accentMoss else colors.inkSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                val metaString = if (!isLocal && !lastActiveAt.isNullOrBlank()) {
                    "$deviceId · 活跃于 ${FormatUtils.formatDisplayDate(lastActiveAt).takeLast(5)}"
                } else {
                    "标识 $deviceId"
                }
                Text(
                    text = metaString,
                    style = ArchiveTextStyles.meta,
                    color = colors.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeviceActionChip(
                text = stringResource(R.string.profile_components_text_01),
                onClick = onRename,
                isDestructive = false
            )
            DeviceActionChip(
                text = if (isLocal) "解除配对" else "踢除",
                onClick = onRevokeOrUnpair,
                isDestructive = true
            )
        }
    }
}

@Composable
private fun RowScope.DeviceActionChip(text: String, onClick: () -> Unit, isDestructive: Boolean) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 40.dp)
            .clip(ArchiveShapes.full)
            .border(
                ArchiveLines.hairline,
                if (isDestructive) colors.alertRed.copy(alpha = 0.4f) else colors.borderDivider,
                ArchiveShapes.full
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = ArchiveTextStyles.label,
            color = if (isDestructive) colors.alertRed else colors.inkPrimary
        )
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
