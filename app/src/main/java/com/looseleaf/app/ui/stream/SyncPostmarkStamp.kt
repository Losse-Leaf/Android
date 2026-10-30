package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LocalArchiveDecorConfig
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 封面区手账状态邮戳 (Cover Header Circular Postmark Stamp)
 *
 * 极简圆形小印章 (20–24dp)：
 * 1. 无待处理事项且未在同步：静默隐藏，不占用视觉空间
 * 2. 存在待放行或失败事项：呈现一枚 22dp 赤陶色细描边圆形小印章（半透明、-8° 倾角），
 *    内部仅为数字印记，点击呼出底部处置抽屉 (BottomSheet)
 * 3. 正在同步中且无错误：展示同尺寸淡墨慢旋图符
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
    val decorConfig = LocalArchiveDecorConfig.current

    val totalPending = failedCount + pendingCount
    if (totalPending <= 0 && !isSyncing) {
        // 无待处理时不显示
        return
    }

    if (totalPending > 0) {
        val stampColor = colors.accentWarm
        val targetClick = if (failedCount > 0) onFailedClick else onPendingClick

        Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(stampColor.copy(alpha = 0.12f))
                .border(1.dp, stampColor.copy(alpha = 0.65f), CircleShape)
                .clickable(onClick = targetClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (totalPending > 99) "99+" else totalPending.toString(),
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Bold,
                fontSize = if (totalPending > 9) 10.sp else 11.sp,
                color = stampColor,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )
        }
    } else if (isSyncing) {
        // 同步中：淡棕灰色圆形邮戳，慢速旋转
        val infiniteTransition = rememberInfiniteTransition(label = "syncSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinAngle"
        )

        Box(
            modifier = modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.archiveSubtle)
                .border(0.6.dp, colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "同步中",
                tint = colors.inkMuted,
                modifier = Modifier
                    .size(13.dp)
                    .rotate(angle)
            )
        }
    }
}
