package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.LooseLeafTheme
import java.time.LocalDate

/**
 * 散页手账 · 封面区 (Stream Cover Header)
 *
 * 统一化规范：
 * - 左：标题"散页"（衬线，最粗的元素）。其下一行为次要暖灰、单行省略的小字（显示"2026年10月27日 · 周二"，不再显示长句）
 * - 右从左到右：搜索、筛选图标、邮戳（无待处理时隐藏）
 * - 封面与下方内容之间无任何分割线或色带，靠间距分隔
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StreamCoverHeader(
    collapseFraction: Float,
    activeFilterCount: Int,
    onFilterClick: () -> Unit,
    onSearchClick: () -> Unit,
    isSyncing: Boolean,
    pendingCount: Int,
    failedCount: Int,
    onFailedStampClick: () -> Unit,
    onPendingStampClick: () -> Unit,
    onTitleLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val fraction = collapseFraction.coerceIn(0f, 1f)

    // 动态计算高度：展开 80dp -> 折叠 52dp
    val headerHeight = lerp(80.dp, 52.dp, fraction)
    val titleFontSize = lerp(26.sp, 20.sp, fraction)
    val subtitleAlpha = (1f - fraction * 2.5f).coerceIn(0f, 1f)

    val currentDateString = remember {
        val now = LocalDate.now()
        val weekdayStr = when (now.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
        "${now.year}年${now.monthValue}月${now.dayOfMonth}日 · $weekdayStr"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(colors.archivePaper)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // 第一行：标题"散页" 与 右侧功能区（年份选择、邮戳、搜索）居于同一行并对齐
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "散页",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Bold,
                fontSize = titleFontSize,
                letterSpacing = 1.2.sp,
                color = colors.inkPrimary,
                maxLines = 1,
                softWrap = false,
                modifier = if (onTitleLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClick = onTitleLongClick
                    )
                } else Modifier
            )

            // 右：从左到右依次为 搜索、筛选图标、邮戳（无待处理时静默隐藏）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 搜索图标
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "搜索",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 筛选图标（24dp，Outlined；有筛选生效时右上角加 6dp 赤陶色小点，无障碍描述为"筛选，已启用 N 项"）
                IconButton(
                    onClick = onFilterClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_filter_alt),
                            contentDescription = if (activeFilterCount > 0) "筛选，已启用 ${activeFilterCount} 项" else "筛选",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .align(Alignment.TopEnd)
                                    .background(colors.accentWarm, CircleShape)
                            )
                        }
                    }
                }

                // 状态邮戳（无待处理时静默隐藏）
                SyncPostmarkStamp(
                    isSyncing = isSyncing,
                    pendingCount = pendingCount,
                    failedCount = failedCount,
                    onFailedClick = onFailedStampClick,
                    onPendingClick = onPendingStampClick
                )
            }
        }

        // 副标题独占下一行（仅在展开态显示，收起态淡出隐藏）
        if (subtitleAlpha > 0.05f) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = currentDateString,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 12.sp,
                color = colors.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(subtitleAlpha)
            )
        }
    }
}
