package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource

/**
 * 极速记账键盘 (Ledger Keypad)
 * 4x3 拟物微隆起大键位，单键高度 ≥ 60dp
 */
@Composable
fun TactileKeypad(
    onDigitClick: (String) -> Unit,
    onDotClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 62.dp
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (item in row) {
                    TactileKeyButton(
                        label = item,
                        height = keyHeight,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (item) {
                                "." -> onDotClick()
                                "⌫" -> onBackspaceClick()
                                else -> onDigitClick(item)
                            }
                        },
                        onLongClick = if (item == "⌫") {
                            {
                                onClearAll()
                            }
                        } else null
                    )
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
    onLongClick: (() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    val shape = RoundedCornerShape(3.dp)
    // MOB-P2-46：用 LocalView 触发系统触感常量（与 Compose 的 HapticFeedbackType 区分开）
    val view = LocalView.current
    // 零权限触觉反馈：LocalHapticFeedback 走系统 HapticFeedbackConstants，
    // 不需要在 Manifest 声明 VIBRATE（AOPT-05a）
    val haptics = LocalHapticFeedback.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(colors.archiveSubtle, shape)
            .border(0.5.dp, colors.borderDivider, shape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colors.accentWarm.copy(alpha = 0.2f)),
                // MOB-P1-61：按键要有按钮角色与可点击名称，读屏才念得出「数字 7 / 退格」
                role = Role.Button,
                onClick = {
                    // MOB-P1-61：读屏要能听出「这是数字键 / 退格键」
                    // MOB-P2-46：数字键改用系统「键盘敲击」触感 ——
                    // 原文案键用的 TextHandleMove 在线性马达上有 15ms 阻塞感，连续输入会累积延迟；
                    // KEYBOARD_TAP 更短促，且与退格/盖章的触感能区分开。
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                },
                onLongClick = onLongClick?.let { action ->
                    {
                        // MOB-P2-46：长按清空用「重」触感，与单键区分
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        action()
                    }
                }
            )
    ) {
        if (label == "⌫") {
            Icon(
                painter = painterResource(R.drawable.ic_backspace),
                contentDescription = stringResource(R.string.tactile_keypad_contentDescription_01),
                tint = colors.inkPrimary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = label,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontWeight = FontWeight.Medium,
                fontSize = 26.sp,
                color = colors.inkPrimary
            )
        }
    }
}
