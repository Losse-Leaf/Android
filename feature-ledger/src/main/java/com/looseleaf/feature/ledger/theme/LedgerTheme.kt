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
import com.looseleaf.app.theme.ArchiveColors
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.DarkArchiveColors
import com.looseleaf.app.theme.LightArchiveColors

/**
 * 账本视觉令牌 (LedgerColors) —— ArchiveColors 的只读语义投影
 */
@Immutable
data class LedgerColors(val archive: ArchiveColors) {
    val background: Color get() = archive.archivePaper
    val surface: Color get() = archive.archivePaper
    val surfaceElevated: Color get() = archive.archiveSubtle
    val textPrimary: Color get() = archive.inkPrimary
    val textSecondary: Color get() = archive.inkSecondary
    val textMuted: Color get() = archive.inkMuted
    val border: Color get() = archive.borderSubtle
    val borderSubtle: Color get() = archive.borderSubtle
    val accent: Color get() = archive.accentWarm
    val expense: Color get() = archive.inkPrimary
    val income: Color get() = archive.accentMoss
    val alertRed: Color get() = archive.alertRed
    val isDark: Boolean get() = archive.isDark
}

val LightLedgerColors = LedgerColors(LightArchiveColors)
val DarkLedgerColors = LedgerColors(DarkArchiveColors)

/**
 * 8 个低饱和度分类色（严格避开赤陶），只用于 6dp/8dp 色点与分类占比条
 */
object LedgerCategoryColors {
    fun palette(isDark: Boolean): List<Color> = if (isDark) {
        listOf(
            Color(0xFFD4A346), // 暖麦秆金黄
            Color(0xFF7C9CB3), // 青石灰
            Color(0xFFC0A075), // 旧皮褐
            Color(0xFFB08CB8), // 暮紫
            Color(0xFF6F9478), // 苔绿
            Color(0xFF93A2B3), // 墨蓝灰
            Color(0xFFC88282), // 枯茶豆沙
            Color(0xFFB5AFA3)  // 暖米灰
        )
    } else {
        listOf(
            Color(0xFFB8860B), // 麦秆黄/暖琥珀
            Color(0xFF4A6B82), // 青石灰
            Color(0xFF8C6D46), // 旧皮褐
            Color(0xFF7A5980), // 暮紫
            Color(0xFF4A6753), // 苔藓绿
            Color(0xFF5C6773), // 墨蓝灰
            Color(0xFF9E6565), // 豆沙红
            Color(0xFF8A857B)  // 暖米灰
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
    val serif: FontFamily = ArchiveFontFamilies.EditorialSerif
    val monoNumbers: FontFamily = ArchiveFontFamilies.MonoScale

    val title = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Bold,
        fontSize = ArchiveTypeScale.h2
    )
    val subtitle = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = ArchiveTypeScale.bodyLarge
    )
    val body = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.body
    )
    val caption = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.caption
    )
    val amountLarge = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.Bold,
        fontSize = ArchiveTypeScale.h1,
        fontFeatureSettings = "tnum"
    )
    val amountMedium = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.SemiBold,
        fontSize = ArchiveTypeScale.bodyLarge,
        fontFeatureSettings = "tnum"
    )
    val amountSmall = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.Normal,
        fontSize = ArchiveTypeScale.body,
        fontFeatureSettings = "tnum"
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
 * 账本主题 (LedgerTheme)：复用外层 LooseLeafTheme 已注入的 MaterialTheme，
 * 仅提供 LocalLedgerColors 语义投影，避免双重嵌套 MaterialTheme。
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
