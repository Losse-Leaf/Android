package com.looseleaf.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.FolioGlyphs
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.MotionPrefs
import java.time.LocalDate

/**
 * 生物识别门禁遮罩层
 *
 * 一页合上的封面：
 * - 中央是一枚朱砂方印「散」，外圈一道缓慢呼吸的发丝圆环（系统关闭动画时静止）；
 * - 印下方是衬线书名与今日日期，再下是一句无衬线说明；
 * - 底部一枚反相墨色胶囊按钮发起验证。
 */
@Composable
internal fun BiometricLockOverlay(
    onRequestUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val context = LocalContext.current
    val animate = remember { MotionPrefs.animationsEnabled(context) }

    val transition = rememberInfiniteTransition(label = "lockBreath")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val ringPhase = if (animate) breath else 0.5f

    val dateLine = remember {
        val today = LocalDate.now(FormatUtils.BeijingZone)
        val weekday = when (today.dayOfWeek.value) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            else -> "周日"
        }
        "${today.year} · ${today.monthValue} 月 ${today.dayOfMonth} 日 · $weekday"
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // 方印 + 呼吸圆环
            Box(
                modifier = Modifier
                    .size(168.dp)
                    .semantics { contentDescription = "已加锁" },
                contentAlignment = Alignment.Center
            ) {
                val ringColor = colors.inkPrimary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val base = size.minDimension / 2f
                    drawCircle(
                        color = ringColor.copy(alpha = 0.06f + 0.06f * ringPhase),
                        radius = base * (0.86f + 0.10f * ringPhase),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = ringColor.copy(alpha = 0.10f),
                        radius = base * 0.72f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = ArchiveShapes.lg,
                            spotColor = colors.accentWarm.copy(alpha = 0.5f),
                            ambientColor = colors.frameShadowAmbient
                        )
                        .clip(ArchiveShapes.lg)
                        .background(colors.accentWarm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "散",
                        fontFamily = ArchiveFontFamilies.Display,
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp,
                        color = colors.onAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.main_activity_text_01),
                style = ArchiveTextStyles.sheetTitle.copy(fontSize = 22.sp, lineHeight = 30.sp),
                color = colors.inkPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateLine,
                style = ArchiveTextStyles.overline,
                color = colors.inkMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.main_activity_text_02),
                style = ArchiveTextStyles.body,
                color = colors.inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp)
            )

            Spacer(modifier = Modifier.weight(1.2f))

            UnlockButton(
                text = stringResource(R.string.main_activity_verify_biometrics),
                onClick = onRequestUnlock
            )
        }
    }
}

@Composable
private fun UnlockButton(text: String, onClick: () -> Unit) {
    val colors = LooseLeafTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 360.dp)
            .height(56.dp)
            .graphicsLayer {
                val s = if (isPressed) 0.98f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(ArchiveShapes.full)
            .background(colors.inverseSurface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = FolioGlyphs.Lock,
            contentDescription = null,
            tint = colors.inverseInk,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = ArchiveTextStyles.button,
            color = colors.inverseInk
        )
    }
}
