package com.looseleaf.feature.ledger.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveColors
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.DarkArchiveColors
import com.looseleaf.app.theme.LightArchiveColors

/**
 * 账本语义色彩投影，完全复用 [ArchiveColors]，账本不另起一套色板。
 *
 * 账本在 Folio 里是「仪表声部」：支出用墨色（不制造焦虑的红），
 * 收入用松绿，朱砂只留给主操作与需要处理的提醒。
 */
@Immutable
data class LedgerColors(val archive: ArchiveColors) {
    val background: Color get() = archive.archivePaper
    val surface: Color get() = archive.archiveCard
    val surfaceElevated: Color get() = archive.archiveSubtle
    val surfaceRaised: Color get() = archive.surfaceRaised
    val archiveCard: Color get() = archive.archiveCard
    val textPrimary: Color get() = archive.inkPrimary
    val textSecondary: Color get() = archive.inkSecondary
    val textMuted: Color get() = archive.inkMuted
    val border: Color get() = archive.borderDivider
    val borderSubtle: Color get() = archive.borderSubtle
    val accent: Color get() = archive.accentWarm
    val accentSoft: Color get() = archive.accentTint
    val onAccent: Color get() = archive.onAccent
    val expense: Color get() = archive.inkPrimary
    val income: Color get() = archive.accentMoss
    val incomeSoft: Color get() = archive.positiveTint
    val alertRed: Color get() = archive.alertRed
    val inverseSurface: Color get() = archive.inverseSurface
    val inverseInk: Color get() = archive.inverseInk
    val isDark: Boolean get() = archive.isDark
}

val LightLedgerColors = LedgerColors(LightArchiveColors)
val DarkLedgerColors = LedgerColors(DarkArchiveColors)

/**
 * 账本分类色板：八种低饱和的矿物色，彼此色相间隔均匀，深浅两套各自校准明度。
 */
object LedgerCategoryColors {
    fun palette(isDark: Boolean): List<Color> = if (isDark) {
        listOf(
            Color(0xFFE2B66C), // 雌黄
            Color(0xFF7CC2C4), // 石青
            Color(0xFFE09A7C), // 赭石
            Color(0xFFB9A2DE), // 青莲
            Color(0xFF8DCB9B), // 石绿
            Color(0xFF9DB3CF), // 花青
            Color(0xFFE59AAB), // 胭脂
            Color(0xFFBEB6A6)  // 墨灰
        )
    } else {
        listOf(
            Color(0xFFA9741A), // 雌黄
            Color(0xFF2C7779), // 石青
            Color(0xFFA9573A), // 赭石
            Color(0xFF6F5296), // 青莲
            Color(0xFF3E7A4E), // 石绿
            Color(0xFF4E6B8A), // 花青
            Color(0xFFA0445A), // 胭脂
            Color(0xFF77705F)  // 墨灰
        )
    }

    fun colorForCategory(category: String, isDark: Boolean): Color {
        val p = palette(isDark)
        return when (category) {
            "餐饮" -> p[0]
            "交通" -> p[1]
            "购物" -> p[2]
            "娱乐" -> p[3]
            "住房", "居家" -> p[4]
            "学习", "办公", "数码器材" -> p[5]
            "医疗", "健康" -> p[6]
            else -> p[kotlin.math.abs(category.hashCode()) % p.size]
        }
    }
}

object LedgerTypography {
    /** 兼容旧名：账本是仪表声部，界面文字一律无衬线 */
    val serif: FontFamily = ArchiveFontFamilies.Text
    /** 书页声部：只用于英雄数字与大标题 */
    val display: FontFamily = ArchiveFontFamilies.Display
    val monoNumbers: FontFamily = ArchiveFontFamilies.Numeric

    val title = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = ArchiveTypeScale.h2,
        letterSpacing = 0.5.sp
    )
    val subtitle = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Medium,
        fontSize = ArchiveTypeScale.bodyLarge,
        letterSpacing = 0.1.sp
    )
    val body = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.body,
        lineHeight = 21.sp
    )
    val caption = TextStyle(
        fontFamily = ArchiveFontFamilies.Text,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.caption,
        lineHeight = 17.sp,
        letterSpacing = 0.3.sp
    )
    /** 总览英雄数字 */
    val amountHero: TextStyle = ArchiveTextStyles.amountHero
    val amountLarge = TextStyle(
        fontFamily = ArchiveFontFamilies.Display,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.h1,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum, lnum"
    )
    val amountMedium = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.Medium,
        fontSize = ArchiveTypeScale.bodyLarge,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum, lnum"
    )
    val amountSmall = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.body,
        fontFeatureSettings = "tnum, lnum"
    )
}

val LocalLedgerColors = staticCompositionLocalOf { LightLedgerColors }

object LedgerTheme {
    val colors: LedgerColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLedgerColors.current

    val spacing: ArchiveSpacing = ArchiveSpacing
    val lines: ArchiveLines = ArchiveLines
    val shapes: ArchiveShapes = ArchiveShapes
    val typography: LedgerTypography = LedgerTypography
}

/**
 * 账本主题：复用外层 LooseLeafTheme 注入的 MaterialTheme，只提供 LocalLedgerColors 投影。
 */
@Composable
fun LedgerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val ledgerColors = remember(darkTheme) {
        if (darkTheme) DarkLedgerColors else LightLedgerColors
    }
    CompositionLocalProvider(
        LocalLedgerColors provides ledgerColors,
        content = content
    )
}
