package com.looseleaf.app.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距令牌：4dp 基础栅格，页面版心 20dp。
 * 节奏原则：同组内 8 / 12，组与组 24，大区块 40。
 */
object ArchiveSpacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp

    /** 一级页面左右版心 */
    val gutter: Dp = 20.dp
    /** 大区块间距 */
    val section: Dp = 40.dp
    /** 卡片内边距 */
    val cardPadding: Dp = 18.dp
    /** 卡片之间的间距 */
    val cardGap: Dp = 12.dp
    /** 底部悬浮导航占位（胶囊 56 + 上下留白） */
    val dockClearance: Dp = 96.dp
    /** 可读内容最大宽度（平板 / 横屏） */
    val readableWidth: Dp = 680.dp
}

/**
 * 线宽令牌
 */
object ArchiveLines {
    val hairline: Dp = 1.dp
    /** 聚焦 / 选中描边 */
    val emphasis: Dp = 1.5.dp
}

/**
 * 圆角令牌：小控件收、纸页放，越大的面越圆。
 */
object ArchiveShapes {
    val xs: RoundedCornerShape = RoundedCornerShape(6.dp)
    val sm: RoundedCornerShape = RoundedCornerShape(10.dp)
    val md: RoundedCornerShape = RoundedCornerShape(16.dp)
    val lg: RoundedCornerShape = RoundedCornerShape(24.dp)
    val xl: RoundedCornerShape = RoundedCornerShape(30.dp)
    val full: RoundedCornerShape = RoundedCornerShape(50)

    /** 散页卡片 */
    val leaf: RoundedCornerShape = RoundedCornerShape(20.dp)
    /** 输入槽 */
    val field: RoundedCornerShape = RoundedCornerShape(14.dp)
    /** 底部抽屉顶部圆角 */
    val sheetTop: RoundedCornerShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)

    // 兼容别名
    val small: RoundedCornerShape = sm
    val medium: RoundedCornerShape = md
    val radiusSmall: RoundedCornerShape = sm
    val radiusMedium: RoundedCornerShape = md
    val radiusFull: RoundedCornerShape = full
    val paperCut: RoundedCornerShape = leaf
    val tagEar: RoundedCornerShape = xs
    val pill: RoundedCornerShape = full
    val dialogLayer: RoundedCornerShape = lg
    val washiTape: RoundedCornerShape = xs
    val bookmark: RoundedCornerShape = sm
}

/**
 * 阴影层级令牌：Folio 以色阶分层为主，阴影只用于真正「浮起来」的东西。
 */
object ArchiveShadows {
    val stickerElevation: Dp = 0.dp
    val paperSheetElevation: Dp = 0.dp
    /** 卡片按需的极轻投影 */
    val leafElevation: Dp = 2.dp
    val floatingLayerElevation: Dp = 12.dp
    val pressedElevation: Dp = 0.dp
    /** 底部导航胶囊 */
    val dockElevation: Dp = 20.dp
    /** 对话框 */
    val dialogElevation: Dp = 24.dp
}

/**
 * 动效令牌：短、轻、带一点阻尼，不弹跳。
 */
object ArchiveMotion {
    const val DurationFast: Int = 160
    const val DurationBase: Int = 280
    const val DurationSlow: Int = 420

    /** 强调缓出：进入、展开 */
    val EaseOutEmphasized: CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    /** 标准缓动：状态切换 */
    val EaseStandard: CubicBezierEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    /** 缓入：退出 */
    val EaseIn: CubicBezierEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    /** 兼容别名 */
    val EaseInOutBook: CubicBezierEasing = EaseStandard

    /** 滑块、指示器等位移：柔和阻尼，不过冲 */
    fun <T> glide(): SpringSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 420f
    )

    /** 按压缩放：按下即响应（约 120ms 到位），松手无过冲 */
    fun <T> press(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 700f
    )

    /** 内容进出场（列表项淡入、计数滚动）的标准时长 */
    const val DurationEnter: Int = 240
}
