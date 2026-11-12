package com.looseleaf.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 散页统一字阶令牌 (Archive Type Scale)
 * 全端收敛为 7 级主字阶 + 1 级微标号，消除 0.5sp 与相邻 1sp 的散写字面量：
 * - micro (11sp)：角标徽章、热力图坐标刻度
 * - caption (12sp)：元信息、表单顶标、时间戳、辅助说明
 * - body (14sp)：标准正文、按钮、可选胶囊、列表副信息
 * - bodyLarge (16sp)：条目主标题、输入框正文、分组标题、抽屉标题
 * - title (18sp)：弹窗标题、强调金额
 * - h2 (22sp)：次级大字、滚轮焦点、子页标题
 * - h1 (28sp)：页面主标题、核心汇总金额
 * - display (32sp)：净资产 / 封面展示大字
 *
 * 【待办 · 第 2 批】本阶梯外仍散落 22 处裸字面量（10 / 13 / 15 / 17 / 20 / 26sp）。
 * 它们必须跟布局一起改（字号一改，行高、固定高度、卡片密度全部连带），
 * 故第 1 批只在此登记、不批量替换。
 */
object ArchiveTypeScale {
    val micro: TextUnit = 11.sp
    val caption: TextUnit = 12.sp
    val body: TextUnit = 14.sp
    val bodyLarge: TextUnit = 16.sp
    val title: TextUnit = 18.sp
    val h2: TextUnit = 22.sp
    val h1: TextUnit = 28.sp
    val display: TextUnit = 32.sp
}

/**
 * 散页排版字体栈映射 (Typography Mapping)
 * 严格遵循 docs/ecosystem.md §4.3 与 docs/web.md §2.4 规范
 * Android 平台倾向：
 * - 古典衬线体 (Serif Editorial): Noto Serif SC -> 系统衬线 —— **只用于标题与大字**
 * - 现代无衬线 (Sans Body): Roboto -> Noto Sans SC -> 系统无衬线 —— **正文一律无衬线**
 * - 等宽刻度 (Monospace Scale): JetBrains Mono -> 系统等宽 —— 数字与金额
 *
 * 注意：在 Compose Material3 中，Typography 样式不应硬编码 color（应保持 Color.Unspecified），
 * 这样可让 Text 自动继承组件层级的 LocalContentColor（如 Button、Surface 的前景文字颜色），
 * 避免出现按钮深色背景上渲染黑字导致文字不可见的 UI 对比度 Bug。
 */
object ArchiveFontFamilies {
    val EditorialSerif: FontFamily = FontFamily.Serif
    val BodySans: FontFamily = FontFamily.SansSerif
    val MonoScale: FontFamily = FontFamily.Monospace
}

fun createArchiveTypography(colors: ArchiveColors): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.display,
            lineHeight = 40.sp,
            color = Color.Unspecified
        ),
        displayMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.h1,
            lineHeight = 36.sp,
            color = Color.Unspecified
        ),
        displaySmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.h2,
            lineHeight = 30.sp,
            color = Color.Unspecified
        ),
        headlineMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.h2,
            lineHeight = 30.sp,
            color = Color.Unspecified
        ),
        headlineSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.title,
            lineHeight = 26.sp,
            color = Color.Unspecified
        ),
        titleLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.title,
            lineHeight = 26.sp,
            color = Color.Unspecified
        ),
        titleMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.bodyLarge,
            lineHeight = 24.sp,
            color = Color.Unspecified
        ),
        titleSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.body,
            lineHeight = 20.sp,
            color = Color.Unspecified
        ),
        bodyLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.bodyLarge,
            lineHeight = 24.sp,
            color = Color.Unspecified
        ),
        bodyMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.body,
            lineHeight = 21.sp,
            color = Color.Unspecified
        ),
        bodySmall = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.caption,
            lineHeight = 18.sp,
            color = Color.Unspecified
        ),
        labelLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.body,
            lineHeight = 18.sp,
            letterSpacing = 0.6.sp,
            fontFeatureSettings = "tnum",
            color = Color.Unspecified
        ),
        labelMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.caption,
            lineHeight = 16.sp,
            letterSpacing = 0.6.sp,
            fontFeatureSettings = "tnum",
            color = Color.Unspecified
        ),
        labelSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.micro,
            lineHeight = 14.sp,
            letterSpacing = 0.5.sp,
            fontFeatureSettings = "tnum",
            color = Color.Unspecified
        )
    )
}
