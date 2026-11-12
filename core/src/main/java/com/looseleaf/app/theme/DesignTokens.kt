package com.looseleaf.app.theme

import androidx.compose.animation.core.CubicBezierEasing
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
 * 严格收敛为四档，全仓不再允许出现其它数值：
 * - xs  2dp：热力图格子、进度条、内嵌微块（"几乎不圆"的物理裁切感）
 * - sm  4dp：输入框 / 标签 / 紧凑按钮 / 配图
 * - md 12dp：卡片 / 抽屉 / 弹窗 / 浮层
 * - full     ：胶囊 / 圆点 / FAB / 状态徽标
 */
object ArchiveShapes {
    /** 微圆角 2dp（热力图格子、进度条、内嵌徽标底） */
    val xs: RoundedCornerShape = RoundedCornerShape(2.dp)

    /** 小圆角 4dp（输入框、标签、紧凑按钮、配图） */
    val sm: RoundedCornerShape = RoundedCornerShape(4.dp)

    /** 中圆角 12dp（弹层卡片、汇总卡片） */
    val md: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** 全圆角（可选胶囊、FAB、分类色点、状态徽标） */
    val full: RoundedCornerShape = RoundedCornerShape(50)

    // ——— 语义别名（第 2/3 批清理前保持兼容，数值一律指向上面四档）———

    /** @deprecated 用 [sm]；保留仅为避免本轮批量重命名 30+ 处调用 */
    val small: RoundedCornerShape = sm

    /** @deprecated 用 [md] */
    val medium: RoundedCornerShape = md

    /** 底部抽屉顶边圆角 12dp（只有上两角，属 md 的变体） */
    val sheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

    /** 兼容语义别名 */
    val radiusSmall: RoundedCornerShape = sm
    val radiusMedium: RoundedCornerShape = md
    val radiusFull: RoundedCornerShape = full
    val paperCut: RoundedCornerShape = sm
    val tagEar: RoundedCornerShape = sm
    val pill: RoundedCornerShape = full
    val dialogLayer: RoundedCornerShape = md
    val washiTape: RoundedCornerShape = sm
    val bookmark: RoundedCornerShape = sm
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
 * 全站动效严格 ≤ 300ms，统一经典纸张翻动缓动曲线。
 * 纸是刚性材料，不做回弹（spring）—— 所有过渡必须是 tween + 缓动曲线。
 */
object ArchiveMotion {
    const val DurationFast: Int = 120   // 微交互（按压下沉、标签凸起）
    const val DurationBase: Int = 200   // 状态过渡（分类翻页、折叠封面）
    const val DurationSlow: Int = 300   // 展开抽屉、卡片落纸

    val EaseInOutBook: CubicBezierEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
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
