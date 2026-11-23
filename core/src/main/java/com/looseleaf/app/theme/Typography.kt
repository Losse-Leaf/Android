package com.looseleaf.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 字号阶梯（约 1.2 倍音阶，向下取整到偶数附近，保证中文笔画清晰）
 */
object ArchiveTypeScale {
    val micro: TextUnit = 11.sp
    val caption: TextUnit = 12.sp
    val body: TextUnit = 14.sp
    val bodyLarge: TextUnit = 16.sp
    val title: TextUnit = 18.sp
    val h2: TextUnit = 24.sp
    val h1: TextUnit = 32.sp
    val display: TextUnit = 44.sp
}

/**
 * 字体家族令牌
 *
 * Folio 的排版双声部：
 * - 书页声部（Serif / 宋体）：刊头、标题、随笔正文、英雄数字 —— 安静、有书卷气；
 * - 仪表声部（Sans / 黑体）：控件、标签、元信息、行内金额 —— 精确、易扫读。
 *
 * 旧别名 EditorialSerif 回归其本义（衬线），BodySans / MonoScale 落在仪表声部。
 */
object ArchiveFontFamilies {
    val Display: FontFamily = FontFamily.Serif
    val Text: FontFamily = FontFamily.SansSerif
    val Reading: FontFamily = FontFamily.Serif
    val Numeric: FontFamily = FontFamily.SansSerif

    // 兼容别名
    val EditorialSerif: FontFamily = Display
    val BodySans: FontFamily = Text
    val MonoScale: FontFamily = Numeric
}

private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

/**
 * 语义文本样式令牌（颜色由调用方或 LocalContentColor 提供）
 */
object ArchiveTextStyles {
    /** 刊头：散页大标题 */
    val masthead = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 2.sp,
        platformStyle = NoFontPadding
    )

    /** 一级页面大标题 */
    val pageTitle = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.sp,
        platformStyle = NoFontPadding
    )

    /** 抽屉 / 弹窗标题 */
    val sheetTitle = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = ArchiveTypeScale.title,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp
    )

    /** 分组小标：小号、加宽字距、次要色 */
    val overline = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Medium,
        fontSize = ArchiveTypeScale.micro,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
        platformStyle = NoFontPadding
    )

    /** 控件标签：按钮、分段、胶囊 */
    val label = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Medium,
        fontSize = ArchiveTypeScale.body,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        platformStyle = NoFontPadding
    )

    /** 列表主行 */
    val rowTitle = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )

    /** 正文 */
    val body = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.body,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )

    /** 元信息：时间、地点、账户 */
    val meta = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.caption,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
        fontFeatureSettings = "tnum"
    )

    /** 随笔正文：衬线、宽行距，阅读优先 */
    val essay = TextStyle(
        fontFamily = ArchiveFontFamilies.Reading,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.3.sp
    )

    /** 随笔标题 */
    val essayTitle = TextStyle(
        fontFamily = ArchiveFontFamilies.Reading,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.4.sp
    )

    /** 英雄数字：总览金额 */
    val amountHero = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.display,
        lineHeight = 50.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "lnum, tnum",
        platformStyle = NoFontPadding
    )

    /** 行内金额 */
    val amount = TextStyle(
        fontFamily = ArchiveFontFamilies.Numeric,
        fontWeight = FontWeight.Medium,
        fontSize = ArchiveTypeScale.bodyLarge,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum, lnum"
    )

    /** 日期大字：时间流日签 */
    val dayNumeral = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "lnum, tnum",
        platformStyle = NoFontPadding
    )

    /** 按钮 */
    val button = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
        platformStyle = NoFontPadding
    )
}

fun createArchiveTypography(@Suppress("UNUSED_PARAMETER") colors: ArchiveColors): Typography {
    val serif = ArchiveFontFamilies.Display
    val sans = ArchiveFontFamilies.Text
    return Typography(
        displayLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.display, lineHeight = 52.sp,
            letterSpacing = (-0.5).sp, color = Color.Unspecified
        ),
        displayMedium = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Normal,
            fontSize = 36.sp, lineHeight = 44.sp,
            letterSpacing = (-0.3).sp, color = Color.Unspecified
        ),
        displaySmall = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.h1, lineHeight = 40.sp,
            letterSpacing = 0.sp, color = Color.Unspecified
        ),
        headlineLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 36.sp,
            letterSpacing = 0.5.sp, color = Color.Unspecified
        ),
        headlineMedium = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = ArchiveTypeScale.h2, lineHeight = 32.sp,
            letterSpacing = 0.5.sp, color = Color.Unspecified
        ),
        headlineSmall = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp, lineHeight = 28.sp,
            letterSpacing = 0.4.sp, color = Color.Unspecified
        ),
        titleLarge = TextStyle(
            fontFamily = serif, fontWeight = FontWeight.SemiBold,
            fontSize = ArchiveTypeScale.title, lineHeight = 26.sp,
            letterSpacing = 0.4.sp, color = Color.Unspecified
        ),
        titleMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.bodyLarge, lineHeight = 22.sp,
            letterSpacing = 0.1.sp, color = Color.Unspecified
        ),
        titleSmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.body, lineHeight = 20.sp,
            letterSpacing = 0.1.sp, color = Color.Unspecified
        ),
        bodyLarge = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.bodyLarge, lineHeight = 25.sp,
            letterSpacing = 0.1.sp, color = Color.Unspecified
        ),
        bodyMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.body, lineHeight = 22.sp,
            letterSpacing = 0.1.sp, color = Color.Unspecified
        ),
        bodySmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Normal,
            fontSize = ArchiveTypeScale.caption, lineHeight = 18.sp,
            letterSpacing = 0.2.sp, color = Color.Unspecified
        ),
        labelLarge = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.SemiBold,
            fontSize = ArchiveTypeScale.body, lineHeight = 20.sp,
            letterSpacing = 0.3.sp, fontFeatureSettings = "tnum",
            color = Color.Unspecified
        ),
        labelMedium = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.caption, lineHeight = 16.sp,
            letterSpacing = 0.4.sp, fontFeatureSettings = "tnum",
            color = Color.Unspecified
        ),
        labelSmall = TextStyle(
            fontFamily = sans, fontWeight = FontWeight.Medium,
            fontSize = ArchiveTypeScale.micro, lineHeight = 14.sp,
            letterSpacing = 0.8.sp, fontFeatureSettings = "tnum",
            color = Color.Unspecified
        )
    )
}
