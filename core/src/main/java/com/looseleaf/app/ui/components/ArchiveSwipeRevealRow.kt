package com.looseleaf.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 散页统一的左滑露出操作行 (ArchiveSwipeRevealRow)
 *
 * 规范与交互：
 * - 向左滑动条目露出右侧"删除"按钮（砖红低饱和背景与文字，4dp圆角），松手后停留在露出状态。
 * - 用户必须主动点击露出的"删除"按钮才触发实际删除，杜绝滑动误删。
 * - 处于露出状态时，点击前景任意区域或向右滑动会自动回弹收起。
 * - 未滑出状态下，点击条目正常响应 onClick 逻辑。
 */
@Composable
fun ArchiveSwipeRevealRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    actionWidth: Dp = 76.dp,
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
            .clip(ArchiveShapes.radiusSmall)
    ) {
        // 背景层：右侧露出删除操作按钮
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(colors.alertRed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(actionWidth)
                    .fillMaxHeight()
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
                Text(
                    text = deleteLabel,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.alertRed
                )
            }
        }

        // 前景层：展示主内容，支持水平滑动手势与阻尼
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
                                    offsetX.animateTo(-actionWidthPx)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
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

            // 当删除按钮处于露出状态时，遮罩层拦截前景点击，点击前景任意区域只收起删除按钮
            if (offsetX.value < -10f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        }
                )
            }
        }
    }
}
