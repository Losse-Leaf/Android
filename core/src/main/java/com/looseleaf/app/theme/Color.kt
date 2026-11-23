package com.looseleaf.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 散页 · Folio 色彩体系
 *
 * 设计意图：一张安静的书桌。
 * - 「宣」浅色：微暖的生宣底（canvas）上放着更白的散页（surface），凹槽用熟宣色（sunken）。
 * - 「墨」深色：松烟墨底，纸页是略亮一档的墨灰，文字是旧纸色而非纯白。
 * - 全局只有一种彩色强调：朱砂（accent），像每页角落的一枚小印，克制使用。
 * - 正向（收入 / 完成 / 已同步）用松绿；异常与破坏性操作用胭脂（与朱砂拉开色相）；心愿用赭金。
 *
 * 字段名保留了既有调用点使用的旧名（archivePaper / accentWarm / accentMoss …），
 * 同时提供语义别名（canvas / surface / sunken / accent / positive / negative …）供新代码使用。
 */
@Immutable
data class ArchiveColors(
    /** 页面底色（canvas）：书桌 */
    val archivePaper: Color,
    /** 凹陷层：输入槽、分段控件轨道、骨架屏、次级按钮底 */
    val archiveSubtle: Color,
    /** 纸页层：卡片、对话框、浮层 */
    val archiveCard: Color = archiveSubtle,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    /** 极淡分隔：卡片描边、列表内缩分隔线 */
    val borderSubtle: Color,
    /** 稍重分隔：分组边界、聚焦前的输入轮廓 */
    val borderDivider: Color,
    val borderCard: Color = borderSubtle,
    /** 主强调色：朱砂 */
    val accentWarm: Color,
    /** 正向色：松绿 */
    val accentMoss: Color,
    /** 背景肌理叠加色（Folio 不再使用噪点，保留字段以兼容） */
    val grainColor: Color,
    val frameShadowAmbient: Color,
    val frameShadowSpot: Color,
    val cardShadowAmbient: Color = frameShadowAmbient,
    val cardShadowSpot: Color = frameShadowSpot,
    /** 照片衬底：图片加载前与留边处的中性色 */
    val filmMatteBg: Color,
    /** 分类标签色：日常与心愿 */
    val tagDaily: Color = accentMoss,
    val tagWish: Color = Color(0xFF9A6A1F),
    /** 取景框背景色 */
    val cameraViewfinderBg: Color = Color(0xFF000000),
    val isDark: Boolean = false,
    /** 异常 / 警告 / 破坏性操作：胭脂 */
    val alertRed: Color = if (isDark) Color(0xFFF08A95) else Color(0xFFA3263A),
    /** 按压叠加层 */
    val pressOverlay: Color = inkPrimary.copy(alpha = 0.06f),
    /** 结构线：输入框常态描边、空状态线稿 */
    val lineEdge: Color = inkPrimary.copy(alpha = 0.14f),
    /** 禁用态轮廓线 */
    val lineDisabled: Color = inkPrimary.copy(alpha = 0.26f),
    /** 弹层背后遮罩 */
    val scrim: Color = Color(0x66000000),
    /** 主色按压态 */
    val accentPress: Color,
    /** 小号文本与选中标签强调色（对比度达标的朱砂） */
    val accentSmallText: Color,
    val inkDisabled: Color = inkMuted,
    /** 主色之上的前景（按钮文字、FAB 图标） */
    val onAccent: Color = archivePaper,
    /** 反相浮层（Toast、撤销条、导航滑块）底色与前景 */
    val inverseSurface: Color = inkPrimary,
    val inverseInk: Color = archivePaper,
    /** 反相浮层上的强调色（如撤销按钮） */
    val inverseAccent: Color = accentWarm,
    /** 反相浮层上的警示色 */
    val inverseAlert: Color = alertRed,
    /** 悬浮层：底部导航胶囊、下拉菜单 */
    val surfaceRaised: Color = archiveCard,
    /** 画布顶部的一层极淡暖光，给页面一点纵深 */
    val canvasGlow: Color = Color.Transparent
) {
    // ---------- 语义别名 ----------
    val canvas: Color get() = archivePaper
    val surface: Color get() = archiveCard
    val sunken: Color get() = archiveSubtle
    val raised: Color get() = surfaceRaised
    val ink: Color get() = inkPrimary
    val accent: Color get() = accentWarm
    val positive: Color get() = accentMoss
    val negative: Color get() = alertRed
    val wish: Color get() = tagWish
    val hairline: Color get() = borderSubtle

    /** 选中态淡底（与 ArchiveAlpha.selectedTint 配套） */
    val accentTint: Color get() = accentWarm.copy(alpha = ArchiveAlpha.selectedTint)
    val positiveTint: Color get() = accentMoss.copy(alpha = ArchiveAlpha.selectedTint)
    val negativeTint: Color get() = alertRed.copy(alpha = ArchiveAlpha.selectedTint)
    val wishTint: Color get() = tagWish.copy(alpha = ArchiveAlpha.selectedTint)
}

/**
 * 常用透明度台阶。
 */
object ArchiveAlpha {
    /** 选中 / 当前态淡底：胶囊、分段、日期格 */
    const val selectedTint: Float = 0.12f
    /** 悬停 / 按压淡底 */
    const val pressTint: Float = 0.06f
    /** 深色模式下 Material 容器色透明度 */
    const val containerDark: Float = 0.22f
    /** 浅色模式下 Material 容器色透明度 */
    const val containerLight: Float = 0.12f
    /** 禁用内容 */
    const val disabled: Float = 0.38f
}

// ---------- 宣（浅色） ----------
val LightArchiveColors = ArchiveColors(
    archivePaper = Color(0xFFF4F1EA),
    archiveSubtle = Color(0xFFEAE5DA),
    archiveCard = Color(0xFFFEFCF8),
    inkPrimary = Color(0xFF1D1B17),
    inkSecondary = Color(0xFF57524A),
    inkMuted = Color(0xFF6F695F),
    borderSubtle = Color(0x141D1B17),
    borderDivider = Color(0x261D1B17),
    borderCard = Color(0x121D1B17),
    accentWarm = Color(0xFFB4442B),
    accentMoss = Color(0xFF3B6B53),
    grainColor = Color(0x00000000),
    frameShadowAmbient = Color(0x142A2116),
    frameShadowSpot = Color(0x242A2116),
    cardShadowAmbient = Color(0x0D2A2116),
    cardShadowSpot = Color(0x1A2A2116),
    filmMatteBg = Color(0xFFE6E0D4),
    tagDaily = Color(0xFF3B6B53),
    tagWish = Color(0xFF9A6A1F),
    isDark = false,
    alertRed = Color(0xFFA3263A),
    pressOverlay = Color(0xFF1D1B17).copy(alpha = 0.05f),
    lineEdge = Color(0xFF1D1B17).copy(alpha = 0.14f),
    lineDisabled = Color(0xFF1D1B17).copy(alpha = 0.24f),
    scrim = Color(0x731D1B17),
    accentPress = Color(0xFF963622),
    accentSmallText = Color(0xFFA43C25),
    inkDisabled = Color(0xFFA9A398),
    onAccent = Color(0xFFFFF9F3),
    inverseSurface = Color(0xFF221F1B),
    inverseInk = Color(0xFFF4F1EA),
    inverseAccent = Color(0xFFF09B7E),
    inverseAlert = Color(0xFFF2959F),
    surfaceRaised = Color(0xFFFFFDFA),
    canvasGlow = Color(0x66FFFFFF)
)

// ---------- 墨（深色） ----------
val DarkArchiveColors = ArchiveColors(
    archivePaper = Color(0xFF121110),
    archiveSubtle = Color(0xFF25231F),
    archiveCard = Color(0xFF1B1A17),
    inkPrimary = Color(0xFFEDE7DB),
    inkSecondary = Color(0xFFB7B0A3),
    inkMuted = Color(0xFF928B7E),
    borderSubtle = Color(0x14EDE7DB),
    borderDivider = Color(0x26EDE7DB),
    borderCard = Color(0x12EDE7DB),
    accentWarm = Color(0xFFE5806A),
    accentMoss = Color(0xFF7FC0A0),
    grainColor = Color(0x00000000),
    frameShadowAmbient = Color(0x47000000),
    frameShadowSpot = Color(0x70000000),
    cardShadowAmbient = Color(0x33000000),
    cardShadowSpot = Color(0x52000000),
    filmMatteBg = Color(0xFF0A0909),
    tagDaily = Color(0xFF7FC0A0),
    tagWish = Color(0xFFDDB265),
    isDark = true,
    alertRed = Color(0xFFF08A95),
    pressOverlay = Color(0xFFEDE7DB).copy(alpha = 0.06f),
    lineEdge = Color(0xFFEDE7DB).copy(alpha = 0.14f),
    lineDisabled = Color(0xFFEDE7DB).copy(alpha = 0.24f),
    scrim = Color(0xA6000000),
    accentPress = Color(0xFFEE9A86),
    accentSmallText = Color(0xFFEA8D77),
    inkDisabled = Color(0xFF5E5950),
    onAccent = Color(0xFF1A110D),
    inverseSurface = Color(0xFFEDE7DB),
    inverseInk = Color(0xFF151412),
    inverseAccent = Color(0xFFA43C25),
    inverseAlert = Color(0xFFA3263A),
    surfaceRaised = Color(0xFF23211E),
    canvasGlow = Color(0x0FFFF3E0)
)
