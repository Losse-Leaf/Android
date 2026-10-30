package com.looseleaf.app.ui.stream

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.R
import com.looseleaf.app.theme.*

/**
 * 时间流空白状态 (Stream Empty State)
 * 纯白素纸笺，手写体题词 + 钢笔图标引导落笔
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
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = ArchiveShapes.paperCut,
            color = colors.archivePaper,
            border = BorderStroke(1.dp, colors.borderSubtle),
            shadowElevation = ArchiveShadows.paperSheetElevation,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!isFiltering) {
                    // 顶部小装饰：两枚微弱装订打孔暗示
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.borderSubtle.copy(alpha = 0.5f))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(colors.borderSubtle.copy(alpha = 0.5f))
                        )
                    }

                    // 衬线体标题
                    Text(
                        text = "这一页还空着，等风来也等你落笔",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 18.sp,
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "记下此刻的一瞬思绪、光影或值得收藏的日常",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(26.dp))

                    Button(
                        onClick = onWriteClick,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
                            contentColor = colors.archivePaper
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = ArchiveShadows.paperSheetElevation
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fountain_pen),
                            contentDescription = null,
                            tint = colors.archivePaper,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "写一页随笔",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // 筛选结果为空：显示一句话"没有符合条件的记录"和文字按钮"清除筛选"
                    Text(
                        text = "没有符合条件的记录",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = onResetFilter,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "清除筛选",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 14.sp,
                            color = colors.accentWarm
                        )
                    }
                }
            }
        }
    }
}

/**
 * 时间流铅笔草稿骨架屏 (Pencil Handwriting Skeleton)
 * 绝无耀眼高光扫描 Shimmer，以铅笔笔痕与呼吸微透光模拟待墨纸张
 */
@Composable
internal fun StreamLoadingSkeleton(
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    // 铅笔笔触呼吸微透光 (0.13f ~ 0.22f)，极度克制静谧
    val infiniteTransition = rememberInfiniteTransition(label = "skeletonBreathing")
    val pencilAlpha by infiniteTransition.animateFloat(
        initialValue = 0.13f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = ArchiveMotion.EaseInOutBook),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pencilAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 日期撕裂线骨架
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 24.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.inkPrimary.copy(alpha = pencilAlpha))
            )
            Box(
                modifier = Modifier
                    .size(width = 54.dp, height = 14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.inkMuted.copy(alpha = pencilAlpha * 0.8f))
            )
            // 撕线虚线骨架
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
            ) {
                val step = 10.dp.toPx()
                val dash = 5.dp.toPx()
                var x = 0f
                val y = size.height / 2f
                while (x < size.width) {
                    drawLine(
                        color = colors.inkMuted.copy(alpha = pencilAlpha * 0.6f),
                        start = Offset(x, y),
                        end = Offset(minOf(x + dash, size.width), y),
                        strokeWidth = 1.dp.toPx()
                    )
                    x += step
                }
            }
        }

        // 散页卡片骨架 1
        PencilCardSkeleton(pencilAlpha = pencilAlpha, titleWidthFraction = 0.65f, contentLines = 2)

        // 散页卡片骨架 2
        PencilCardSkeleton(pencilAlpha = pencilAlpha, titleWidthFraction = 0.82f, contentLines = 3)

        // 散页卡片骨架 3
        PencilCardSkeleton(pencilAlpha = pencilAlpha, titleWidthFraction = 0.50f, contentLines = 1)
    }
}

@Composable
private fun PencilCardSkeleton(
    pencilAlpha: Float,
    titleWidthFraction: Float,
    contentLines: Int
) {
    val colors = LooseLeafTheme.colors

    Surface(
        shape = ArchiveShapes.paperCut,
        color = colors.archivePaper,
        border = BorderStroke(0.6.dp, colors.borderSubtle),
        shadowElevation = ArchiveShadows.paperSheetElevation,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 顶栏：时间铅笔痕 + 分类耳标微痕
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.inkMuted.copy(alpha = pencilAlpha * 0.75f))
                )
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 15.dp)
                        .clip(ArchiveShapes.tagEar)
                        .background(colors.accentWarm.copy(alpha = pencilAlpha * 0.5f))
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 标题草稿条
            Box(
                modifier = Modifier
                    .fillMaxWidth(titleWidthFraction)
                    .height(15.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.inkPrimary.copy(alpha = pencilAlpha))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 正文草稿线条
            for (i in 0 until contentLines) {
                val lineFraction = if (i == contentLines - 1) 0.58f else 0.94f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(lineFraction)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.inkMuted.copy(alpha = pencilAlpha * 0.65f))
                )
                if (i < contentLines - 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * 时间流异常折角卡片 (Dog-Eared Error Card)
 * 右上角真实折纸效果，古典宋体「手稿翻阅受阻」+ 重新翻阅按钮
 */
@Composable
internal fun StreamErrorCard(
    errorMessage: String,
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val dogEarSize = 22.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 380.dp)
        ) {
            Surface(
                shape = ArchiveShapes.paperCut,
                color = colors.archivePaper,
                border = BorderStroke(1.dp, colors.borderSubtle),
                shadowElevation = ArchiveShadows.paperSheetElevation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 报头：手账折损提示
                    Text(
                        text = "手稿翻阅受阻",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkPrimary,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = errorMessage.ifBlank { "中枢未能送达这一页的字迹，请检查网络或重新翻阅" },
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 12.sp,
                        color = colors.inkSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = onRetry,
                        enabled = !isRetrying,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentWarm,
                            contentColor = colors.archivePaper,
                            disabledContainerColor = colors.accentWarm.copy(alpha = 0.6f),
                            disabledContentColor = colors.archivePaper.copy(alpha = 0.7f)
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp,
                                color = colors.archivePaper
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在翻阅…",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_fountain_pen),
                                contentDescription = null,
                                tint = colors.archivePaper,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "重新翻阅",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 右上角折角三角（Dog-Ear Fold）
            Canvas(
                modifier = Modifier
                    .size(dogEarSize)
                    .align(Alignment.TopEnd)
            ) {
                val w = size.width
                val h = size.height

                // 翻折三角底阴影
                val shadowPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, h)
                    lineTo(w, 0f)
                    close()
                }
                drawPath(shadowPath, color = colors.cardShadowSpot.copy(alpha = 0.28f))

                // 翻折纸角三角瓣
                val flapPath = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(flapPath, color = colors.archiveSubtle)

                // 折痕线
                drawLine(
                    color = colors.borderSubtle,
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
