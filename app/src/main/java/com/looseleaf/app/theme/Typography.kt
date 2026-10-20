package com.looseleaf.app.theme

import androidx.compose.material3.Typography
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
 */
object ArchiveFontFamilies {
    // 衬线 / 无衬线必须在字形上真的不同，否则「纸本编辑体」只是名义上的
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
            color = colors.inkPrimary
        ),
        displayMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            color = colors.inkPrimary
        ),
        displaySmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            color = colors.inkPrimary
        ),
        headlineMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            color = colors.inkPrimary
        ),
        headlineSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            color = colors.inkPrimary
        ),
        titleLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            color = colors.inkPrimary
        ),
        titleMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = colors.inkPrimary
        ),
        titleSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.inkSecondary
        ),
        bodyLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = colors.inkPrimary
        ),
        bodyMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = colors.inkPrimary
        ),
        bodySmall = TextStyle(
            fontFamily = ArchiveFontFamilies.BodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colors.inkSecondary
        ),
        labelLarge = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            // tnum：等宽表格数字，保证金额列严格对齐（AOPT-08c）
            fontFeatureSettings = "tnum",
            color = colors.inkPrimary
        ),
        labelMedium = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFeatureSettings = "tnum",
            color = colors.inkSecondary
        ),
        labelSmall = TextStyle(
            fontFamily = ArchiveFontFamilies.MonoScale,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFeatureSettings = "tnum",
            color = colors.inkMuted
        )
    )
}
