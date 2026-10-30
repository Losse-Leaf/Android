package com.looseleaf.app.ui.stream

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 散页活页装订孔装饰 (Binder Holes Strip)
 *
 * 位于手账列表左边缘，模拟物理活页手账被金属活页环固定的冲孔区。
 * - 孔径 9dp (半径 4.5dp，严格落于 8–10dp 黄金手账孔径区间)
 * - 冲孔间距固定 pitch = 52dp
 * - 程序化绘制内阴影凹陷与底部微透光高光，营造真实纸张厚度与冲压深度
 * - 视差系数 1:1 跟随列表滚动
 * - 竖线与孔彻底分离：右侧单条极淡微线，绝不与圆孔重叠
 */
@Composable
internal fun BinderHoles(
    scrollY: Float,
    modifier: Modifier = Modifier,
    width: Dp = 18.dp,
    pitchDp: Dp = 52.dp,
    holeRadiusDp: Dp = 4.5.dp
) {
    val colors = LooseLeafTheme.colors
    val isDark = colors.isDark

    Canvas(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .clipToBounds()
    ) {
        val pitchPx = pitchDp.toPx()
        val radiusPx = holeRadiusDp.toPx()
        // 孔洞中心置于打孔带偏左区域 (距左侧 8dp)，与右侧分隔竖线保持充分安全距离
        val centerX = 8.dp.toPx()

        // 计算当前可视范围内的孔洞垂直位置，配合滚动偏移（1:1 平滑滚动）
        val mod = ((scrollY % pitchPx) + pitchPx) % pitchPx
        var currentY = mod - pitchPx

        while (currentY < size.height + pitchPx) {
            val center = Offset(centerX, currentY)

            // 1. 底部纸边缘透光微白边（立体裁切感）
            val highlightColor = if (isDark) {
                colors.borderSubtle.copy(alpha = 0.22f)
            } else {
                colors.archiveCard.copy(alpha = 0.90f)
            }
            drawCircle(
                color = highlightColor,
                radius = radiusPx + 0.8f,
                center = center + Offset(0f, 1.0f)
            )

            // 2. 活页孔内部底色（显露手账本夹底衬深度）
            val holeBgColor = if (isDark) {
                colors.archivePaper.copy(alpha = 0.75f)
            } else {
                colors.inkPrimary.copy(alpha = 0.12f)
            }
            drawCircle(
                color = holeBgColor,
                radius = radiusPx,
                center = center
            )

            // 3. 上方与左侧内凹月牙阴影（冲压深度感）
            val shadowColor = if (isDark) {
                colors.inkPrimary.copy(alpha = 0.35f)
            } else {
                colors.inkPrimary.copy(alpha = 0.22f)
            }
            drawCircle(
                color = shadowColor,
                radius = radiusPx - 0.5f,
                center = center - Offset(0.6f, 0.8f)
            )

            // 4. 孔心深层极细内核
            val coreColor = if (isDark) {
                colors.archivePaper
            } else {
                colors.inkPrimary.copy(alpha = 0.16f)
            }
            drawCircle(
                color = coreColor,
                radius = radiusPx * 0.65f,
                center = center
            )

            currentY += pitchPx
        }

        // 5. 与孔洞完全分离的极淡单线（压痕单线，不重叠圆孔）
        val perfX = size.width - 0.5.dp.toPx()
        val perfColor = if (isDark) {
            colors.borderSubtle.copy(alpha = 0.20f)
        } else {
            colors.inkPrimary.copy(alpha = 0.08f)
        }
        drawLine(
            color = perfColor,
            start = Offset(perfX, 0f),
            end = Offset(perfX, size.height),
            strokeWidth = 0.6.dp.toPx()
        )
    }
}
