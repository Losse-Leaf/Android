package com.looseleaf.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 散页排版字体栈映射 (Typography Mapping)
 * 严格遵循 docs/ecosystem.md §4.3 与 docs/web.md §2.4 规范
 * Android 平台倾向：
 * - 古典衬线体 (Serif Editorial): Noto Serif SC -> 系统衬线
 * - 现代无衬线 (Sans Body): Roboto -> Noto Sans SC -> 系统无衬线
 * - 等宽刻度 (Monospace Scale): JetBrains Mono -> 系统等宽
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
            fontSize = 32.sp,
            lineHeight = 40.sp,
            color = Color.Unspecified
        ),
        displayMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            color = Color.Unspecified
        ),
        displaySmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            color = Color.Unspecified
        ),
        headlineMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = Color.Unspecified
        ),
        headlineSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            color = Color.Unspecified
        ),
        titleLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            color = Color.Unspecified
        ),
        titleMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Color.Unspecified
        ),
        titleSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = Color.Unspecified
        ),
        bodyLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = Color.Unspecified
        ),
        bodyMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Color.Unspecified
        ),
        bodySmall = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = Color.Unspecified
        ),
        labelLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = "tnum",
            color = Color.Unspecified
        ),
        labelMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFeatureSettings = "tnum",
            color = Color.Unspecified
        ),
        labelSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFeatureSettings = "tnum",
            color = Color.Unspecified
        )
    )
}
