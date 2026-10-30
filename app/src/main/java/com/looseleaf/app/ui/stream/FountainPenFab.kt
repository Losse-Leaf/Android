package com.looseleaf.app.ui.stream

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 钢笔尖悬浮操作按钮 (Fountain Pen FAB)
 *
 * 统一化规范：
 * - 保留赤陶色，改为圆形 56dp，只有笔尖图标，无文字、无投影
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FountainPenFab(
    isExpanded: Boolean,
    onClick: () -> Unit,
    onQuickText: () -> Unit,
    onQuickPhoto: () -> Unit,
    onQuickVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val view = LocalView.current
    var showQuickMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd
    ) {
        // 长按弹出的快捷操作气泡（文字 / 照片 / 语音）
        AnimatedVisibility(
            visible = showQuickMenu,
            enter = fadeIn(tween(ArchiveMotion.DurationFast)) + slideInVertically(tween(ArchiveMotion.DurationFast)) { it / 2 },
            exit = fadeOut(tween(ArchiveMotion.DurationFast)) + slideOutVertically(tween(ArchiveMotion.DurationFast)) { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 66.dp)
            ) {
                QuickActionItem(
                    label = "文字随笔",
                    icon = { Icon(painterResource(R.drawable.ic_fountain_pen), contentDescription = null, tint = colors.archivePaper, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showQuickMenu = false
                        onQuickText()
                    }
                )
                QuickActionItem(
                    label = "照片记录",
                    icon = { Icon(painterResource(R.drawable.ic_camera_alt), contentDescription = null, tint = colors.archivePaper, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showQuickMenu = false
                        onQuickPhoto()
                    }
                )
                QuickActionItem(
                    label = "即兴语音",
                    icon = { Icon(painterResource(R.drawable.ic_mic), contentDescription = null, tint = colors.archivePaper, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        showQuickMenu = false
                        onQuickVoice()
                    }
                )
            }
        }

        // 向下滚动时收缩并淡出隐藏；向上滚动或停止后恢复
        AnimatedVisibility(
            visible = isExpanded,
            enter = scaleIn(tween(ArchiveMotion.DurationFast)) + fadeIn(tween(ArchiveMotion.DurationFast)),
            exit = scaleOut(tween(ArchiveMotion.DurationFast)) + fadeOut(tween(ArchiveMotion.DurationFast))
        ) {
            // 圆形 56dp，只有笔尖图标，无文字、无投影、无边框
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.accentWarm)
                    .combinedClickable(
                        onClick = {
                            if (showQuickMenu) {
                                showQuickMenu = false
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            showQuickMenu = !showQuickMenu
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_fountain_pen),
                    contentDescription = "写一页",
                    tint = colors.archivePaper,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionItem(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(ArchiveShapes.pill)
            .background(colors.accentWarm)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        icon()
        Text(
            text = label,
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.archivePaper
        )
    }
}
