package com.looseleaf.app.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 散页手账间距阶梯 (Archive Spacing Ladder)
 * 严格遵从 4 / 8 / 12 / 16 / 24 / 32 / 48dp 规范
 */
object ArchiveSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}

/**
 * 散页统一线型 (Archive Lines)
 * 全页只允许一种线 —— 1dp 极淡暖灰实线。删除所有虚线
 */
object ArchiveLines {
    val hairline: Dp = 1.dp
}

/**
 * 散页手账圆角体系 (Archive Shapes)
 * 严格收敛为两档：4dp (内容微圆角) 与 全圆 (Circle / Pill)
 */
object ArchiveShapes {
    /** 微圆角 4dp (配图、按压微高亮矩形) */
    val radiusSmall: RoundedCornerShape = RoundedCornerShape(4.dp)

    /** 全圆 (FAB、分类色点、状态邮戳) */
    val radiusFull: RoundedCornerShape = RoundedCornerShape(50)

    /** 散页纸张切角：严格统一至 4dp */
    val paperCut: RoundedCornerShape = radiusSmall

    /** 索引标签页耳朵：统一至 4dp */
    val tagEar: RoundedCornerShape = radiusSmall

    /** 全圆角胶囊与印章 */
    val pill: RoundedCornerShape = radiusFull

    /** 弹层、底抽纸笺与对话框 16dp */
    val dialogLayer: RoundedCornerShape = RoundedCornerShape(16.dp)

    /** 和纸胶带：统一至 4dp */
    val washiTape: RoundedCornerShape = radiusSmall

    /** 底栏指示器 */
    val bookmark: RoundedCornerShape = radiusSmall
}

/**
 * 散页手账阴影层级 (Archive Shadows)
 * 贴纸 / 纸页 / 浮层三档，绝非冷黑，配合暖棕灰投影视效
 */
object ArchiveShadows {
    val stickerElevation: Dp = 0.5.dp
    val paperSheetElevation: Dp = 1.5.dp
    val floatingLayerElevation: Dp = 6.dp
    val pressedElevation: Dp = 0.5.dp
}

/**
 * 散页手账动效节奏 (Archive Motion)
 * 全站动效严格 ≤ 300ms，统一经典纸张翻动缓动曲线
 */
object ArchiveMotion {
    const val DurationFast: Int = 120   // 微交互（按压下沉、标签凸起）
    const val DurationBase: Int = 200   // 状态过渡（分类翻页、折叠封面）
    const val DurationSlow: Int = 300   // 展开抽屉、卡片落纸

    val EaseInOutBook: CubicBezierEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
    val SpringGentle: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 400f)
}

/**
 * 装饰参数集中管理配置 (Archive Decor Config)
 * 一处调参，支持低配机降级与朴素模式一键对比
 */
data class ArchiveDecorConfig(
    val maxCardTiltDeg: Float = 0f,         // 移除卡片旋转
    val maxCardOffsetDp: Dp = 0.dp,         // 移除卡片错位
    val washiTiltDeg: Float = 0f,           // 移除和纸倾角
    val postmarkTiltDeg: Float = 0f,        // 移除邮戳倾角
    val grainAlphaLight: Float = 0f,        // 浅色宣纸噪点透明度归零
    val grainAlphaDark: Float = 0f,         // 暗房牛皮纸噪点透明度归零
    val enableDecorations: Boolean = false  // 装饰质感默认关闭（单一纸面）
)

val LocalArchiveDecorConfig = compositionLocalOf { ArchiveDecorConfig() }
