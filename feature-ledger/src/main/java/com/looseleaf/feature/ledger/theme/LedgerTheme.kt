package com.looseleaf.feature.ledger.theme

import androidx.compose.material3.MaterialTheme
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
import com.looseleaf.app.theme.ArchiveColors
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveLines
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveSpacing
import com.looseleaf.app.theme.ArchiveTypeScale
import com.looseleaf.app.theme.DarkArchiveColors
import com.looseleaf.app.theme.LightArchiveColors
import com.looseleaf.app.theme.archiveColorScheme
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
/**
 * 账本视觉令牌 (LedgerColors)
 *
 * 【第 1 批去重】原先这里是一整套独立的 13 字段色盘，取值虽已趋近全局
 * `ArchiveColors`，但字段名不同、结构重复，两套色盘随时会各自漂移。
 * 现在改成 [ArchiveColors] 的一层**只读语义投影**：
 * - 全账本 33 处 `LedgerTheme.colors.xxx` 的字段名一个都不变（本轮零改动调用点）；
 * - 色彩真源只有 `LightArchiveColors` / `DarkArchiveColors` 一份，
 *   全局改色（例如 inkMuted 对比度修复）自动传导到账本，不会再漏。
 * 真正的字段名清理（background → archivePaper 等）留到第 3 批。
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
 * 账本主题 (LedgerTheme)
 *
 * 【第 1 批修复 P0「变天」Bug】此前 `darkTheme` 默认取 `isSystemInDarkTheme()`，
 * 而 `LedgerEntry` 调用时没有传参 —— 于是用户选了「暖色棉纸」但系统是深色时，
 * 一切到账本整页变黑。现在由 `MainActivity` 已解析好的三态 `isDarkTheme` 显式透传进来。
 *
 * 【第 2 批收口】`darkTheme` 改为**必填**，不再保留 `= isSystemInDarkTheme()` 兜底：
 * 默认值本身就是一条旁路 —— 将来若有人直接调 `LedgerTheme { }`，会静默退回原 Bug。
 * 改成必填后漏传直接编译失败；全仓唯一调用点 `LedgerEntry` 已显式传参。
 *
 * 【第 1 批修复「一页紫」】此前这里只映射了 6 个 M3 角色，其余（surfaceVariant /
 * onSurfaceVariant / outline / outlineVariant / error / onError ...）全部退回 M3
 * 基线的薰衣草紫。现在直接复用全局 `archiveColorScheme()`，全仓只有一份映射。
 */
@Composable
fun LedgerTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val archiveColors = remember(darkTheme) {
        if (darkTheme) DarkArchiveColors else LightArchiveColors
    }
    val ledgerColors = remember(darkTheme) {
        if (darkTheme) DarkLedgerColors else LightLedgerColors
    }
    val materialColors = remember(archiveColors, darkTheme) {
        archiveColorScheme(archiveColors, darkTheme)
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
