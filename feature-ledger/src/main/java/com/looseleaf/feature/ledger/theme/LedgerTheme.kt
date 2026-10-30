package com.looseleaf.feature.ledger.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.DarkArchiveColors
import com.looseleaf.app.theme.LightArchiveColors
import java.util.Locale

/**
 * 账本独立视觉体系 (LedgerTheme)
 *
 * 遵循散页统一自然大地色盘与时间流视觉规则：
 * 1. 一块纸色底（无投影、无白卡片、无冷灰蓝底）。
 * 2. 一种 1dp 淡暖灰实线 (ArchiveLines.hairline)。
 * 3. 两套字体：古典衬线体 (EditorialSerif) + 等宽数字 (MonoScale)。
 * 4. 赤陶色 (accentWarm) 只表示"当前 / 可操作 / 选中态 / 主按钮 / FAB"，去掉所有深蓝。
 * 5. 金额颜色规范：支出 = 正文色；收入 = 低饱和苔绿；负结余 = 正文色加负号；无异常时不使用红色。
 * 6. 分类色：8 个低饱和度色彩（严格避开赤陶），仅用于 6dp 色点与占比条。
 * 7. 圆角严格收敛为 4dp (radiusSmall) 与 全圆 (radiusFull) 两档。
 * 8. 间距只用 4/8/12/16/24/32dp (ArchiveSpacing)。
 */
@Immutable
data class LedgerColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderSubtle: Color,
    val accent: Color,
    val expense: Color,
    val income: Color,
    val alertRed: Color,
    val isDark: Boolean
)

val LightLedgerColors = LedgerColors(
    background = LightArchiveColors.archivePaper,      // 暖米色纸底 #F6F0E4
    surface = LightArchiveColors.archivePaper,         // 与背景同一张纸
    surfaceElevated = LightArchiveColors.archiveSubtle,// 暖米微纸层 #F0ECE3
    textPrimary = LightArchiveColors.inkPrimary,       // 碳素深棕黑 #1D1B18
    textSecondary = LightArchiveColors.inkSecondary,   // 暖木炭灰 #5C574F
    textMuted = LightArchiveColors.inkMuted,           // 次要暖灰 #787268
    border = LightArchiveColors.borderSubtle,          // 1dp 淡暖灰实线 0x183A2E1C
    borderSubtle = LightArchiveColors.borderSubtle,
    accent = LightArchiveColors.accentWarm,            // 赤陶色 #A24F2B
    expense = LightArchiveColors.inkPrimary,           // 支出 = 正文色
    income = LightArchiveColors.accentMoss,            // 收入 = 低饱和苔绿 #4A6753
    alertRed = Color(0xFFB35C5C),                      // 低饱和砖红（异常/警告预留）
    isDark = false
)

val DarkLedgerColors = LedgerColors(
    background = DarkArchiveColors.archivePaper,       // 暗房深棕纸底 #141312
    surface = DarkArchiveColors.archivePaper,
    surfaceElevated = DarkArchiveColors.archiveSubtle, // 暗房微纸层 #1B1A18
    textPrimary = DarkArchiveColors.inkPrimary,        // 碳素纸白 #ECE7DE
    textSecondary = DarkArchiveColors.inkSecondary,    // 暖浅炭灰 #99948A
    textMuted = DarkArchiveColors.inkMuted,            // 暗调灰 #8A8377
    border = DarkArchiveColors.borderSubtle,           // 1dp 淡暖灰实线 0x1CEEE6D8
    borderSubtle = DarkArchiveColors.borderSubtle,
    accent = DarkArchiveColors.accentWarm,             // 提亮赤陶 #D0603D
    expense = DarkArchiveColors.inkPrimary,            // 支出 = 正文色
    income = DarkArchiveColors.accentMoss,             // 收入 = 提亮苔绿 #6E9479
    alertRed = Color(0xFFD08888),                      // 提亮低饱和砖红
    isDark = true
)

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

    private val userColorOverrides = mutableStateMapOf<String, Int>()

    fun setCategoryColorIndex(category: String, colorIndex: Int) {
        userColorOverrides[category] = colorIndex
    }

    fun getCategoryColorIndex(category: String): Int? = userColorOverrides[category]

    fun colorForCategory(category: String, isDark: Boolean): Color {
        val p = palette(isDark)
        val overrideIdx = userColorOverrides[category]
        if (overrideIdx != null && overrideIdx in p.indices) {
            return p[overrideIdx]
        }
        return if (isDark) {
            when (category) {
                "餐饮" -> p[0]
                "交通" -> p[1]
                "购物" -> p[2]
                "娱乐" -> p[3]
                "住房", "居家" -> p[4]
                "学习", "办公", "数码器材" -> p[5]
                "医疗", "健康" -> p[6]
                else -> p[kotlin.math.abs(category.hashCode()) % p.size]
            }
        } else {
            when (category) {
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
}

/**
 * 等宽数字金额格式化工具
 * 支持千分位、保留两位小数、符号与右对齐
 */
object LedgerFormat {
    fun formatMoney(cents: Long, includeSymbol: Boolean = true): String {
        val absCents = kotlin.math.abs(cents)
        val yuan = absCents / 100
        val remainder = absCents % 100
        val sign = if (cents < 0) "-" else ""
        val formattedYuan = String.format(Locale.CHINA, "%,d", yuan)
        val numStr = "$formattedYuan.${remainder.toString().padStart(2, '0')}"
        return if (includeSymbol) "${sign}¥$numStr" else "$sign$numStr"
    }
}

object LedgerTypography {
    val serif: FontFamily = ArchiveFontFamilies.EditorialSerif
    val monoNumbers: FontFamily = ArchiveFontFamilies.MonoScale

    val title = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    )
    val subtitle = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )
    val body = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
    val caption = TextStyle(
        fontFamily = serif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
    val amountLarge = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    )
    val amountMedium = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    )
    val amountSmall = TextStyle(
        fontFamily = monoNumbers,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
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

@Composable
fun LedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val ledgerColors = remember(darkTheme) {
        if (darkTheme) DarkLedgerColors else LightLedgerColors
    }

    val materialColors = remember(darkTheme, ledgerColors) {
        if (darkTheme) {
            darkColorScheme(
                primary = ledgerColors.accent,
                onPrimary = Color.White,
                background = ledgerColors.background,
                surface = ledgerColors.surface,
                onBackground = ledgerColors.textPrimary,
                onSurface = ledgerColors.textPrimary
            )
        } else {
            lightColorScheme(
                primary = ledgerColors.accent,
                onPrimary = Color.White,
                background = ledgerColors.background,
                surface = ledgerColors.surface,
                onBackground = ledgerColors.textPrimary,
                onSurface = ledgerColors.textPrimary
            )
        }
    }

    CompositionLocalProvider(
        LocalLedgerColors provides ledgerColors
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
