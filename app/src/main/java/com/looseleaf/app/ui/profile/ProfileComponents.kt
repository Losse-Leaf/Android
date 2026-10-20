package com.looseleaf.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.TrustedDevice
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
@Composable
internal fun ProfileInfoRow(label: String, value: String) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontFamily = ArchiveFontFamilies.BodySans, fontSize = 12.sp, color = colors.inkMuted)
        Text(text = value, fontFamily = ArchiveFontFamilies.MonoScale, fontSize = 12.sp, color = colors.inkSecondary)
    }
}

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
    val platformUpper = platform.uppercase()
    val platformIcon = when {
        isLocal -> painterResource(R.drawable.ic_smartphone)
        platformUpper.contains("MAC") || platformUpper.contains("PC") || platformUpper.contains("DESKTOP") -> painterResource(R.drawable.ic_laptop)
        platformUpper.contains("WEB") -> painterResource(R.drawable.ic_language)
        else -> painterResource(R.drawable.ic_devices)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.archiveSubtle.copy(alpha = 0.5f))
            .border(0.5.dp, colors.borderDivider.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：平台图标 + 设备名称（全宽舒展展示，不与操作按钮硬挤！）+ 平台雅致轻胶囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = platformIcon,
                contentDescription = null,
                tint = if (isLocal) colors.accentMoss else colors.inkMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = deviceName,
                fontFamily = ArchiveFontFamilies.BodySans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isLocal) colors.accentMoss.copy(alpha = 0.12f) else colors.archivePaper)
                    .border(0.5.dp, if (isLocal) colors.accentMoss.copy(alpha = 0.3f) else colors.borderDivider, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (isLocal) "本机" else platformUpper,
                    fontSize = 11.sp,
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontWeight = FontWeight.Medium,
                    color = if (isLocal) colors.accentMoss else colors.inkSecondary,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // 第二行：设备全局唯一标识与活跃时间（左侧）······ 操作按钮（右侧靠齐，不挤占第一行！）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val metaString = if (!isLocal && !lastActiveAt.isNullOrBlank()) {
                "$deviceId · 活跃于 ${FormatUtils.formatDate(lastActiveAt).takeLast(5)}"
            } else {
                "标识: $deviceId"
            }
            Text(
                text = metaString,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 11.sp,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.profile_components_text_01),
                    fontSize = 11.5.sp,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    color = colors.inkSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { onRename() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isLocal) "解除" else "踢除",
                    fontSize = 11.5.sp,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    color = colors.accentWarm,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable { onRevokeOrUnpair() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
