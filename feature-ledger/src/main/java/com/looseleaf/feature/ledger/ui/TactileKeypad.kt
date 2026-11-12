package com.looseleaf.feature.ledger.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.delay

/**
 * 共享等宽数字键盘 (TactileKeypad)
 * 遵循「散页」第四轮规范：
 * - 去框（无按键外轮廓实线，纯净纸色贴合）
 * - 四列布局：左三列为数字（1-9、0、小数点、C 清空），右列为 退格 / + / 确认
 * - 4dp 微圆角、等宽数字与古典衬线确认按钮
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TactileKeypad(
    onDigitClick: (String) -> Unit,
    onDotClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onClearAll: () -> Unit,
    onPlusClick: () -> Unit = {},
    confirmText: String = "确认",
    isConfirmEnabled: Boolean = true,
    onConfirm: (() -> Unit)? = null,
    showPlus: Boolean = true,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 46.dp
) {
    val spacing = 6.dp
    val colors = LedgerTheme.colors
    val view = LocalView.current
    val shape = RoundedCornerShape(4.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // 左侧三列：数字区域
        Column(
            modifier = Modifier.weight(3f),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val digitRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf(".", "0", "C")
            )
            for (row in digitRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (item in row) {
                        TactileKeyButton(
                            label = item,
                            height = keyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when (item) {
                                    "." -> onDotClick()
                                    "C" -> onClearAll()
                                    else -> onDigitClick(item)
                                }
                            },
                            // 数字键长按连发同一数字；小数点与 C 不参与连发
                            onRepeat = if (item == "." || item == "C") null else ({ onDigitClick(item) })
                        )
                    }
                }
            }
        }

        // 右侧第四列：操作区域（退格 / + / 确认）
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // 第 1 行：退格（长按连续退位，清空交给左区「C」键）
            TactileKeyButton(
                label = "⌫",
                height = keyHeight,
                modifier = Modifier.fillMaxWidth(),
                isAction = true,
                onClick = onBackspaceClick,
                onRepeat = onBackspaceClick
            )

            // 第 2 行：+（或清空占位）
            if (showPlus) {
                TactileKeyButton(
                    label = "+",
                    height = keyHeight,
                    modifier = Modifier.fillMaxWidth(),
                    isAction = true,
                    onClick = onPlusClick
                )
            } else {
                TactileKeyButton(
                    label = "C",
                    height = keyHeight,
                    modifier = Modifier.fillMaxWidth(),
                    isAction = true,
                    onClick = onClearAll
                )
            }

            // 第 3 & 4 行：确认主按钮（高度跨两行）
            val confirmHeight = keyHeight * 2 + spacing
            val confirmBg = if (isConfirmEnabled) colors.accent else colors.surfaceElevated
            val confirmTextColor = if (isConfirmEnabled) colors.background else colors.textMuted

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(confirmHeight)
                    .clip(shape)
                    .background(confirmBg, shape)
                    .combinedClickable(
                        enabled = isConfirmEnabled && onConfirm != null,
                        role = Role.Button,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfirm?.invoke()
                        }
                    )
            ) {
                Text(
                    text = confirmText,
                    fontFamily = LedgerTheme.typography.serif,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = confirmTextColor
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TactileKeyButton(
    label: String,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAction: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null
) {
    val colors = LedgerTheme.colors
    val view = LocalView.current
    val shape = RoundedCornerShape(4.dp)

    // 去框：纯净平贴纸色底，背景根据动作类型低调微调
    val bgColor = if (isAction) colors.surfaceElevated else colors.surfaceElevated.copy(alpha = 0.5f)

    val longClickAction = onLongClick
    val repeatAction = onRepeat
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    var isPressed by remember { mutableStateOf(false) }

    // 数字 / 退格键：按住超过系统长按阈值后按 80ms 间隔连发。
    // 抬手时 isPressed 置 false，LaunchedEffect 的 key 变化会取消协程，连发随之停止
    // （全程 delay 挂起，不阻塞主线程；协程随组合 / key 变化自动取消，无泄漏）。
    LaunchedEffect(isPressed) {
        if (isPressed && repeatAction != null) {
            delay(longPressTimeout)
            while (true) {
                repeatAction()
                delay(80L)
            }
        }
    }

    // 仅连发键监听按下 / 抬起；不 consume 事件，避免与 combinedClickable 的手势冲突。
    val pressModifier = if (repeatAction != null) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // 手势被父级抢走 / 组合销毁时也要复位，否则连发协程停不下来
                try {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    // awaitAllPointersUp() 是 Compose 内部 API，这里手动等待所有指针抬起
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                } finally {
                    isPressed = false
                }
            }
        }
    } else {
        Modifier
    }

    // 连发键用一个占位的 onLongClick 消费长按手势，避免抬手时 combinedClickable 再补一次 onClick；
    // 连发本身由上面的 LaunchedEffect 完成，这里只保留一次长按震动。
    val combinedLongClick: (() -> Unit)? = if (longClickAction != null || repeatAction != null) {
        val action = longClickAction ?: {}
        {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            action()
        }
    } else {
        null
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .then(pressModifier)
            .clip(shape)
            .background(bgColor, shape)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = combinedLongClick
            )
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = if (label == "⌫") 18.sp else 20.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary
        )
    }
}
