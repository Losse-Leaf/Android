package com.looseleaf.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 左滑露出操作行 (ArchiveSwipeRevealRow)
 *
 * - 向左滑动露出右侧胭脂色「删除」圆角方块（图形 + 文字），松手按半程阈值停靠或回弹；
 * - 必须主动点击露出的按钮才会删除，杜绝误删；
 * - 露出状态下点击前景任意处或右滑即收起；
 * - 露出按钮随滑动距离渐显、轻微放大，给手指一点「被拉出来」的反馈。
 */
@Composable
fun ArchiveSwipeRevealRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 84.dp,
    deleteLabel: String = "删除",
    backgroundColor: Color = LooseLeafTheme.colors.archivePaper,
    content: @Composable () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val density = LocalDensity.current
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ArchiveShapes.md)
    ) {
        // 背景层：右侧删除操作
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(backgroundColor),
            contentAlignment = Alignment.CenterEnd
        ) {
            val revealFraction = (-offsetX.value / actionWidthPx).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .graphicsLayer {
                        alpha = revealFraction
                        val s = 0.85f + 0.15f * revealFraction
                        scaleX = s
                        scaleY = s
                    }
                    .clip(ArchiveShapes.md)
                    .background(colors.alertRed)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = deleteLabel
                    ) {
                        coroutineScope.launch {
                            offsetX.snapTo(0f)
                            onDelete()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = FolioGlyphs.Close,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = deleteLabel,
                        style = ArchiveTextStyles.meta,
                        fontSize = 11.sp,
                        color = colors.onAccent
                    )
                }
            }
        }

        // 前景层：主内容，水平拖拽 + 阻尼停靠
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(backgroundColor)
                .pointerInput(actionWidthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < -actionWidthPx / 2f) {
                                    offsetX.animateTo(-actionWidthPx, ArchiveMotion.glide())
                                } else {
                                    offsetX.animateTo(0f, ArchiveMotion.glide())
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { offsetX.animateTo(0f, ArchiveMotion.glide()) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val target = (offsetX.value + dragAmount).coerceIn(-actionWidthPx, 0f)
                                offsetX.snapTo(target)
                            }
                        }
                    )
                }
        ) {
            content()

            // 露出状态时拦截前景点击：点任意处只收起
            if (offsetX.value < -10f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, ArchiveMotion.glide())
                            }
                        }
                )
            }
        }
    }
}
