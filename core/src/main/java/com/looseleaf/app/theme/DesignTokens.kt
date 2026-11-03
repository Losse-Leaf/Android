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
 * 严格收敛为三档：small 4dp（输入框 / 标签 / 按钮）、medium 12dp（卡片 / 抽屉 / 弹窗）、full（可选胶囊 / 圆点）
 */
object ArchiveShapes {
    /** 小圆角 4dp（输入框、标签、紧凑按钮、配图） */
    val small: RoundedCornerShape = RoundedCornerShape(4.dp)

    /** 中圆角 12dp（弹层卡片、汇总卡片） */
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** 底部抽屉顶边圆角 12dp */
    val sheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

    /** 全圆角（可选胶囊、FAB、分类色点、状态徽标） */
    val full: RoundedCornerShape = RoundedCornerShape(50)

    /** 兼容语义别名 */
    val radiusSmall: RoundedCornerShape = small
    val radiusMedium: RoundedCornerShape = medium
    val radiusFull: RoundedCornerShape = full
    val paperCut: RoundedCornerShape = small
    val tagEar: RoundedCornerShape = small
    val pill: RoundedCornerShape = full
    val dialogLayer: RoundedCornerShape = medium
    val washiTape: RoundedCornerShape = small
    val bookmark: RoundedCornerShape = small
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
