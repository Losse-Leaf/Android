package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.FolioGlyphs

/**
 * 刊头同步状态签 (Sync Postmark)
 *
 * 1. 无待处理且未在同步：完全隐藏，不占位；
 * 2. 有失败项：胭脂淡底胶囊 + 警示图形 + 数字，点开处置抽屉；
 * 3. 仅有待放行项：朱砂淡底胶囊 + 圆点 + 数字，点击重新排队；
 * 4. 正在同步：凹槽圆片里一枚慢旋的同步图形。
 */
@Composable
internal fun SyncPostmarkStamp(
    isSyncing: Boolean,
    pendingCount: Int,
    failedCount: Int,
    onFailedClick: () -> Unit,
    onPendingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    val total = failedCount + pendingCount
    if (total <= 0 && !isSyncing) return

    if (total > 0) {
        val hasFailed = failedCount > 0
        val tint = if (hasFailed) colors.alertRed else colors.accentSmallText
        val container = if (hasFailed) colors.negativeTint else colors.accentTint
        val label = if (total > 99) "99+" else total.toString()
        Row(
            modifier = modifier
                .defaultMinSize(minHeight = 28.dp)
                .clip(ArchiveShapes.full)
                .background(container)
                .clickable(
                    role = Role.Button,
                    onClick = if (hasFailed) onFailedClick else onPendingClick
                )
                .semantics {
                    contentDescription = if (hasFailed) "$failedCount 项同步失败，点击处理" else "$pendingCount 项待同步，点击重新排队"
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasFailed) {
                Icon(
                    imageVector = FolioGlyphs.Alert,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(ArchiveShapes.full)
                        .background(tint)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = ArchiveTextStyles.meta,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1
            )
        }
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinAngle"
        )
        Box(
            modifier = modifier
                .size(28.dp)
                .clip(ArchiveShapes.full)
                .background(colors.archiveSubtle)
                .semantics { contentDescription = "同步中" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FolioGlyphs.Sync,
                contentDescription = null,
                tint = colors.inkSecondary,
                modifier = Modifier
                    .size(15.dp)
                    .rotate(angle)
            )
        }
    }
}

