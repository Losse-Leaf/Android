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
 * 账本分类色板：十二种低饱和的矿物色，彼此色相间隔均匀，深浅两套各自校准明度。
 */
object LedgerCategoryColors {
    fun palette(isDark: Boolean): List<Color> = if (isDark) {
        listOf(
            Color(0xFFE2B66C), // 0 雌黄 (餐饮)
            Color(0xFFC99B6B), // 1 琥珀 (咖啡饮品)
            Color(0xFF9CCB8A), // 2 嫩柳 (买菜生鲜)
            Color(0xFFE8A482), // 3 杏仁 (零食水果)
            Color(0xFF7CC2C4), // 4 石青 (交通)
            Color(0xFF83AED1), // 5 天青 (旅行度假)
            Color(0xFFE09A7C), // 6 赭石 (购物/日用)
            Color(0xFFD98FAA), // 7 藕荷 (服饰美妆)
            Color(0xFF8DCB9B), // 8 石绿 (住房/水电)
            Color(0xFF9DB3CF), // 9 花青 (数码/学习/通讯)
            Color(0xFFB9A2DE), // 10 青莲 (娱乐/订阅/宠物)
            Color(0xFFE59AAB)  // 11 胭脂 (医疗/运动)
        )
    } else {
        listOf(
            Color(0xFFA9741A), // 0 雌黄
            Color(0xFF8E5D2D), // 1 琥珀
            Color(0xFF4F8242), // 2 嫩柳
            Color(0xFFB8673D), // 3 杏仁
            Color(0xFF2C7779), // 4 石青
            Color(0xFF3E688E), // 5 天青
            Color(0xFFA9573A), // 6 赭石
            Color(0xFF9A4D6B), // 7 藕荷
            Color(0xFF3E7A4E), // 8 石绿
            Color(0xFF4E6B8A), // 9 花青
            Color(0xFF6F5296), // 10 青莲
            Color(0xFFA0445A)  // 11 胭脂
        )
    }

    fun colorForCategory(category: String, isDark: Boolean): Color {
        val p = palette(isDark)
        return when (category) {
            "餐饮" -> p[0]
            "交通" -> p[4]
            "购物", "日用百货", "好物购置", "服饰美妆", "数码器材" -> p[6]
            "居家", "住房", "水电燃气", "通讯网费" -> p[8]
            "订阅", "订阅会员" -> p[9]
            "娱乐", "旅行度假", "运动健康", "宠物" -> p[10]
            "医疗", "健康", "利息手续费" -> p[11]
            "学习", "办公" -> p[5]
            "工资", "工资收入", "奖金补贴" -> p[8]
            "副业", "兼职副业", "稿酬设计" -> p[9]
            "理财", "投资理财" -> p[4]
            "退款", "退款报销", "二手回血" -> p[2]
            "其他", "其他收入", "人情社交", "礼金红包", "亲友转账" ->
                if (isDark) Color(0xFFBEB6A6) else Color(0xFF77705F)
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
