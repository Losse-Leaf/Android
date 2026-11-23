package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.ui.components.FolioLeavesOrnament

/**
 * 时间流空白状态 (Stream Empty State)
 * - 首次为空：散页插画 + 衬线题词 + 朱砂「落笔」胶囊
 * - 筛选为空：一句说明 + 描边胶囊「清除筛选」
 */
@Composable
internal fun StreamEmptyState(
    isFiltering: Boolean,
    onWriteClick: () -> Unit,
    onResetFilter: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isFiltering) {
                FolioLeavesOrnament()
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "这一页还空着",
                    style = ArchiveTextStyles.pageTitle.copy(fontSize = 24.sp, lineHeight = 32.sp),
                    color = colors.inkPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "等风来，也等你落笔。记下此刻的一瞬思绪、光影，或一件值得收藏的日常。",
                    style = ArchiveTextStyles.essay,
                    color = colors.inkSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 52.dp)
                        .clip(ArchiveShapes.full)
                        .background(colors.accentWarm)
                        .clickable(role = Role.Button, onClick = onWriteClick)
                        .padding(horizontal = 26.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = FolioGlyphs.Pen,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "写一页随笔",
                        style = ArchiveTextStyles.button,
                        color = colors.onAccent
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(ArchiveShapes.full)
                        .background(colors.archiveSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FolioGlyphs.Filter,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "没有符合条件的记录",
                    style = ArchiveTextStyles.essayTitle,
                    color = colors.inkPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "换一个时间或分类试试",
                    style = ArchiveTextStyles.body,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 46.dp)
                        .clip(ArchiveShapes.full)
                        .border(ArchiveLines.hairline, colors.borderDivider, ArchiveShapes.full)
                        .clickable(role = Role.Button, onClick = onResetFilter)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "清除筛选",
                        style = ArchiveTextStyles.label,
                        color = colors.inkPrimary
                    )
                }
            }
        }
    }
}

/**
 * 时间流骨架屏：与真实散页同构的纸页卡片，内部为极慢呼吸的凹槽条。
 */
@Composable
internal fun StreamLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonBreathing")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = ArchiveMotion.EaseStandard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(ArchiveShapes.xs)
                    .background(colors.archiveSubtle.copy(alpha = breath))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 12.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.archiveSubtle.copy(alpha = breath))
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.borderSubtle)
            )
        }
        LeafSkeleton(breath = breath, titleFraction = 0.62f, lines = 2, withImage = false)
        LeafSkeleton(breath = breath, titleFraction = 0.80f, lines = 1, withImage = true)
        LeafSkeleton(breath = breath, titleFraction = 0.48f, lines = 3, withImage = false)
    }
}

@Composable
private fun LeafSkeleton(
    breath: Float,
    titleFraction: Float,
    lines: Int,
    withImage: Boolean
) {
    val colors = LooseLeafTheme.colors
    val bar = colors.archiveSubtle.copy(alpha = breath)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.leaf)
            .background(colors.archiveCard)
            .border(ArchiveLines.hairline, colors.borderCard, ArchiveShapes.leaf)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 10.dp)
                    .clip(ArchiveShapes.full)
                    .background(bar)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(width = 30.dp, height = 10.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.accentWarm.copy(alpha = 0.14f * breath))
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (withImage) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(ArchiveShapes.sm)
                    .background(bar)
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(titleFraction)
                .height(16.dp)
                .clip(ArchiveShapes.full)
                .background(bar)
        )
        for (i in 0 until lines) {
            Spacer(modifier = Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i == lines - 1) 0.56f else 0.94f)
                    .height(10.dp)
                    .clip(ArchiveShapes.full)
                    .background(bar.copy(alpha = bar.alpha * 0.8f))
            )
        }
    }
}

/**
 * 时间流异常页：胭脂警示图形 + 衬线标题 + 说明 + 反相墨色「重新连接」胶囊。
 */
@Composable
internal fun StreamErrorCard(
    errorMessage: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(ArchiveShapes.full)
                    .background(colors.negativeTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FolioGlyphs.Alert,
                    contentDescription = null,
                    tint = colors.alertRed,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "这一页暂时翻不开",
                style = ArchiveTextStyles.essayTitle,
                color = colors.inkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage.ifBlank { "中枢未能送达这一页的字迹，请检查网络后重试" },
                style = ArchiveTextStyles.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(26.dp))
            Row(
                modifier = Modifier
                    .defaultMinSize(minHeight = 50.dp)
                    .clip(ArchiveShapes.full)
                    .background(if (isRetrying) colors.archiveSubtle else colors.inverseSurface)
                    .clickable(enabled = !isRetrying, role = Role.Button, onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.inkSecondary,
                        trackColor = colors.borderSubtle
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "正在重新连接",
                        style = ArchiveTextStyles.label,
                        color = colors.inkSecondary
                    )
                } else {
                    Icon(
                        imageVector = FolioGlyphs.Sync,
                        contentDescription = null,
                        tint = colors.inverseInk,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "重新连接",
                        style = ArchiveTextStyles.label,
                        color = colors.inverseInk
                    )
                }
            }
        }
    }
}
