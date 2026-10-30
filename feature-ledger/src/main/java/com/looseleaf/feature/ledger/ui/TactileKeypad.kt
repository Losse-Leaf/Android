package com.looseleaf.feature.ledger.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.feature.ledger.theme.LedgerTheme

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
                            }
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
            // 第 1 行：退格
            TactileKeyButton(
                label = "⌫",
                height = keyHeight,
                modifier = Modifier.fillMaxWidth(),
                isAction = true,
                onClick = onBackspaceClick,
                onLongClick = onClearAll
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
            val confirmTextColor = if (isConfirmEnabled) Color.White else colors.textMuted

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
    onLongClick: (() -> Unit)? = null
) {
    val colors = LedgerTheme.colors
    val view = LocalView.current
    val shape = RoundedCornerShape(4.dp)

    // 去框：纯净平贴纸色底，背景根据动作类型低调微调
    val bgColor = if (isAction) colors.surfaceElevated else colors.surfaceElevated.copy(alpha = 0.5f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bgColor, shape)
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        action()
                    }
                }
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
