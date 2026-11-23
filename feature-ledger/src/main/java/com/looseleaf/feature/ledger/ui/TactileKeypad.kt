package com.looseleaf.feature.ledger.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.feature.ledger.theme.LedgerTheme
import kotlinx.coroutines.delay

/**
 * 触感数字键盘 (TactileKeypad)
 *
 * Folio 版本：像一块嵌在纸面里的计算尺。
 * - 四列：左三列数字（1-9、小数点、0、清空），右列为 退格 / 加号 / 跨两行的确认键；
 * - 数字键：凹槽色圆角方块，22sp 等宽数字；功能键：更深一档凹槽 + 线性图形；
 * - 按下：键面缩到 94% 并浮出墨色薄层，松手阻尼回弹，配合键盘触感；
 * - 确认键：朱砂实心，禁用时退为凹槽色 + 禁用墨色字；
 * - 数字键与退格键长按连发（系统长按阈值后每 80ms 一次）。
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
    val spacing = 8.dp
    val colors = LedgerTheme.colors
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // 左侧三列：数字区
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
                            isAction = item == "C",
                            accessibilityLabel = when (item) {
                                "." -> "小数点"
                                "C" -> "清空"
                                else -> null
                            },
                            onClick = {
                                when (item) {
                                    "." -> onDotClick()
                                    "C" -> onClearAll()
                                    else -> onDigitClick(item)
                                }
                            },
                            onRepeat = if (item == "." || item == "C") null else ({ onDigitClick(item) })
                        )
                    }
                }
            }
        }

        // 右侧第四列：退格 / + / 确认
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            TactileKeyButton(
                label = "",
                glyph = FolioGlyphs.Backspace,
                accessibilityLabel = "退格",
                height = keyHeight,
                modifier = Modifier.fillMaxWidth(),
                isAction = true,
                onClick = onBackspaceClick,
                onRepeat = onBackspaceClick
            )

            if (showPlus) {
                TactileKeyButton(
                    label = "",
                    glyph = FolioGlyphs.Plus,
                    accessibilityLabel = "加",
                    height = keyHeight,
                    modifier = Modifier.fillMaxWidth(),
                    isAction = true,
                    onClick = onPlusClick
                )
            } else {
                TactileKeyButton(
                    label = "C",
                    accessibilityLabel = "清空",
                    height = keyHeight,
                    modifier = Modifier.fillMaxWidth(),
                    isAction = true,
                    onClick = onClearAll
                )
            }

            // 确认主键（跨两行）
            val confirmHeight = keyHeight * 2 + spacing
            val enabled = isConfirmEnabled && onConfirm != null
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val confirmBg by animateColorAsState(
                targetValue = when {
                    !isConfirmEnabled -> colors.surfaceElevated
                    isPressed -> colors.archive.accentPress
                    else -> colors.accent
                },
                animationSpec = tween(ArchiveMotion.DurationFast),
                label = "confirmKeyBg"
            )
            val confirmScale by animateFloatAsState(
                targetValue = if (isPressed && enabled) 0.95f else 1f,
                animationSpec = ArchiveMotion.press(),
                label = "confirmKeyScale"
            )
            val confirmTextColor = if (isConfirmEnabled) colors.onAccent else colors.textMuted

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(confirmHeight)
                    .graphicsLayer {
                        scaleX = confirmScale
                        scaleY = confirmScale
                    }
                    .clip(ArchiveShapes.md)
                    .background(confirmBg)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onConfirm?.invoke()
                        }
                    )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = FolioGlyphs.Check,
                        contentDescription = null,
                        tint = confirmTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    if (confirmText.length <= 4) {
                        Text(
                            text = confirmText,
                            style = ArchiveTextStyles.label,
                            fontWeight = FontWeight.SemiBold,
                            color = confirmTextColor,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            text = confirmText,
                            style = ArchiveTextStyles.meta,
                            fontWeight = FontWeight.SemiBold,
                            color = confirmTextColor,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                            maxLines = 2
                        )
                    }
                }
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
    glyph: ImageVector? = null,
    accessibilityLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null
) {
    val colors = LedgerTheme.colors
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedVisual by interactionSource.collectIsPressedAsState()

    val baseColor = if (isAction) colors.surfaceElevated else colors.surfaceElevated.copy(alpha = 0.55f)
    val pressedColor = if (isAction) colors.border else colors.surfaceElevated
    val bgColor by animateColorAsState(
        targetValue = if (isPressedVisual) pressedColor else baseColor,
        animationSpec = tween(ArchiveMotion.DurationFast),
        label = "keyBg"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressedVisual) 0.94f else 1f,
        animationSpec = ArchiveMotion.press(),
        label = "keyScale"
    )

    val longClickAction = onLongClick
    val repeatAction = onRepeat
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    var isPressed by remember { mutableStateOf(false) }

    // 数字 / 退格键：按住超过系统长按阈值后按 80ms 间隔连发；抬手即停（协程随 key 变化取消）。
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
                try {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
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

    // 连发键用一个占位 onLongClick 消费长按，避免抬手时 combinedClickable 再补一次 onClick。
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
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ArchiveShapes.md)
            .background(bgColor)
            .then(
                if (accessibilityLabel != null) {
                    Modifier.semantics { contentDescription = accessibilityLabel }
                } else Modifier
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = combinedLongClick
            )
    ) {
        if (glyph != null) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = ArchiveFontFamilies.Numeric,
                    fontWeight = if (label == "C") FontWeight.Medium else FontWeight.Normal,
                    fontSize = if (label == "C") 17.sp else 23.sp,
                    fontFeatureSettings = "tnum, lnum"
                ),
                color = if (label == "C") colors.accent else colors.textPrimary
            )
        }
    }
}
