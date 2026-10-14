package com.looseleaf.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 极速触觉记账键盘 (Tactile Ledger Keypad) - docs/mobile.md §3.1
 * 4x3 拟物微隆起大键位，单键高度 ≥ 60dp，按键触发系统轻微触感震动
 */
@Composable
fun TactileKeypad(
    onDigitClick: (String) -> Unit,
    onDotClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onClearAll: () -> Unit,
    haptics: HapticsEngine,
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
                            haptics.tapKey()
                            when (item) {
                                "." -> onDotClick()
                                "⌫" -> onBackspaceClick()
                                else -> onDigitClick(item)
                            }
                        },
                        onLongClick = if (item == "⌫") {
                            {
                                haptics.tearDelete()
                                onClearAll()
                            }
                        } else null
                    )
                }
            }
        }
    }
}

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

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(colors.archiveSubtle, shape)
            .border(0.5.dp, colors.borderDivider, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colors.accentWarm.copy(alpha = 0.2f)),
                onClick = onClick
            )
    ) {
        if (label == "⌫") {
            Icon(
                imageVector = Icons.Outlined.Backspace,
                contentDescription = "退格",
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
