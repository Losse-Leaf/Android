package com.looseleaf.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.model.GearItem
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.utils.FormatUtils
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 统一装备展示行组件（账本「装备」与展台「随行装备」共用）
 * - 去掉冗余分组名，优先保留规格与简短日期，杜绝购入日期被截断
 * - 右侧指标使用等宽对齐（tnum），支持价格或「已陪伴 N 天」
 */
@Composable
fun ArchiveGearRow(
    gear: GearItem,
    trailingText: String? = null,
    trailingColor: Color? = null,
    showShortDateInSubtitle: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val today = remember { LocalDate.now(FormatUtils.BeijingZone) }

    val subtitle = remember(gear.spec, gear.purchaseDate, gear.category, showShortDateInSubtitle, today) {
        val shortDate = if (showShortDateInSubtitle) {
            formatGearShortDate(gear.purchaseDate, today)
        } else {
            null
        }
        val parts = listOfNotNull(
            gear.spec.trim().takeIf { it.isNotBlank() },
            shortDate
        )
        if (parts.isNotEmpty()) {
            parts.joinToString(" · ")
        } else {
            gear.category.takeIf { it.isNotBlank() }.orEmpty()
        }
    }

    val rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 48.dp)
        .clip(ArchiveShapes.small)
        .then(
            if (onClick != null) {
                Modifier.clickable(role = Role.Button, onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(vertical = 10.dp)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = gear.name,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = ArchiveTypeScale.body,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = ArchiveTypeScale.caption,
                    color = colors.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                style = TextStyle(
                    fontFamily = ArchiveFontFamilies.MonoScale,
                    fontSize = ArchiveTypeScale.body,
                    fontFeatureSettings = "tnum"
                ),
                color = trailingColor ?: colors.inkPrimary,
                maxLines = 1
            )
        }
    }
}

fun formatGearShortDate(rawDate: String?, today: LocalDate = LocalDate.now(FormatUtils.BeijingZone)): String? {
    val clean = rawDate?.trim()?.take(10)?.takeIf { it.isNotBlank() } ?: return null
    val parsed = runCatching { LocalDate.parse(clean) }.getOrNull() ?: return clean
    return if (parsed.year == today.year) {
        String.format("%02d/%02d", parsed.monthValue, parsed.dayOfMonth)
    } else {
        String.format("%04d/%02d/%02d", parsed.year, parsed.monthValue, parsed.dayOfMonth)
    }
}

fun formatGearCompanionDays(rawDate: String?, today: LocalDate = LocalDate.now(FormatUtils.BeijingZone)): String? {
    val clean = rawDate?.trim()?.take(10)?.takeIf { it.isNotBlank() } ?: return null
    val parsed = runCatching { LocalDate.parse(clean) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(parsed, today).coerceAtLeast(1L)
    return "已陪伴 $days 天"
}
