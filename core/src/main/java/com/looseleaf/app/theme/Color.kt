package com.looseleaf.app.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 散页 (Loose Leaf) · 自然大地色盘 (Earthy Palette) 跨端实现
 * 严格遵循 docs/ecosystem.md §4.2 与 docs/web.md §2.2 / §2.3 规范定义
 */
@Immutable
data class ArchiveColors(
    val archivePaper: Color,
    val archiveSubtle: Color,
    val archiveCard: Color = archiveSubtle,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val borderSubtle: Color,
    val borderDivider: Color,
    val borderCard: Color = borderSubtle,
    val accentWarm: Color,
    val accentMoss: Color,
    val grainColor: Color,
    val frameShadowAmbient: Color,
    val frameShadowSpot: Color,
    val cardShadowAmbient: Color = frameShadowAmbient,
    val cardShadowSpot: Color = frameShadowSpot,
    val filmMatteBg: Color,
    /** 手账低饱和四色分类 */
    val tagDaily: Color = accentMoss,
    val tagTravel: Color = Color(0xFF3B5B75),
    val tagProject: Color = Color(0xFF9E723B),
    val tagWish: Color = Color(0xFF945353),
    /** 相机取景框的纯黑衬底：Preview surface 外的 letterbox 必须是绝对黑，两种模式共用 */
    val cameraViewfinderBg: Color = Color(0xFF000000),
    val isDark: Boolean = false,
    /** 低饱和砖红（异常 / 警告 / 破坏性操作专用） */
    val alertRed: Color = if (isDark) Color(0xFFD08888) else Color(0xFFB35C5C)
)

// 暖色棉纸模式 (Paper Mode)
val LightArchiveColors = ArchiveColors(
    archivePaper = Color(0xFFF6F0E4),   // 暖米色纸底
    archiveSubtle = Color(0xFFF0ECE3),
    archiveCard = Color(0xFFFCFAF5),    // 散页物理纸片：比背景略亮的米白
    inkPrimary = Color(0xFF1D1B18),     // 碳素深棕黑
    inkSecondary = Color(0xFF5C574F),   // 暖木炭灰
    inkMuted = Color(0xFF787268),       // 对比度 >= 4.5:1
    borderSubtle = Color(0x183A2E1C),   // 暖纸裁切折痕
    borderDivider = Color(0x223A2E1C),
    borderCard = Color(0x1A3A2E1C),     // 散页微纸边
    accentWarm = Color(0xFFA24F2B),     // 暖赭石/赤陶
    accentMoss = Color(0xFF4A6753),     // 苔藓绿
    grainColor = Color(0x0A3A2E1C),
    frameShadowAmbient = Color(0x1F181716),
    frameShadowSpot = Color(0x2E181716),
    cardShadowAmbient = Color(0x122C2216), // 温暖棕灰柔和阴影，绝非冷黑
    cardShadowSpot = Color(0x182C2216),
    filmMatteBg = Color(0xFFE8E5DF),
    tagDaily = Color(0xFF4A6753),       // 日常：苔绿
    tagTravel = Color(0xFF3B5B75),      // 游历：靛蓝
    tagProject = Color(0xFF9E723B),     // 造物：琥珀
    tagWish = Color(0xFF945353),        // 心愿：枯茶红
    isDark = false
)

// 暗房安全灯模式 (Darkroom Mode)
val DarkArchiveColors = ArchiveColors(
    archivePaper = Color(0xFF181512),   // 夜间台灯下的熟牛皮纸底
    archiveSubtle = Color(0xFF221F1B),
    archiveCard = Color(0xFF23201C),    // 纸面略亮
    inkPrimary = Color(0xFFEDE5D8),     // 暖乳白墨迹
    inkSecondary = Color(0xFFAFA89B),
    inkMuted = Color(0xFF8A8377),       // 对比度 >= 4.5:1
    borderSubtle = Color(0x1CEEE6D8),
    borderDivider = Color(0x2CEEE6D8),
    borderCard = Color(0x20EEE6D8),
    accentWarm = Color(0xFFD0603D),     // 暗房安全灯赤红（提亮）
    accentMoss = Color(0xFF6E9479),     // 苔藓绿提亮
    grainColor = Color(0x06FFFFFF),
    frameShadowAmbient = Color(0x26000000),
    frameShadowSpot = Color(0x40000000),
    cardShadowAmbient = Color(0x26000000),
    cardShadowSpot = Color(0x40000000),
    filmMatteBg = Color(0xFF0D0C0B),
    tagDaily = Color(0xFF6E9479),       // 日常：苔绿（提亮）
    tagTravel = Color(0xFF698DAA),      // 游历：靛蓝（提亮）
    tagProject = Color(0xFFC79A63),     // 造物：琥珀（提亮）
    tagWish = Color(0xFFC27878),        // 心愿：枯茶红（提亮）
    isDark = true
)

// 支出类目光谱色 (Earthy Spectrum) - docs/web.md §2.3
object SpectrumColors {
    val Dining = Color(0xFFA24F2B)      // 餐饮 - 暖赭石
    val Transit = Color(0xFF4A6B82)     // 交通 - 青石灰
    val Shopping = Color(0xFF8C6D46)    // 购物 - 旧皮褐
    val Leisure = Color(0xFF7A5980)     // 娱乐 - 暮紫
    val Housing = Color(0xFF3F5A4A)     // 住房 - 苔藓绿
    val Study = Color(0xFF5C6773)       // 学习 - 墨色
    val Medical = Color(0xFFB35C5C)     // 医疗 - 枯叶红
    val Other = Color(0xFF8A857B)       // 其他 - 米灰

    // 暗房模式下的同色系提亮版本：深底 (#141312) 上原色对比度不足 3:1
    private val DiningDark = Color(0xFFC97A52)
    private val TransitDark = Color(0xFF7C9CB3)
    private val ShoppingDark = Color(0xFFC0A075)
    private val LeisureDark = Color(0xFFB08CB8)
    private val HousingDark = Color(0xFF6F9478)
    private val StudyDark = Color(0xFF93A2B3)
    private val MedicalDark = Color(0xFFD08888)
    private val OtherDark = Color(0xFFB5AFA3)

    fun colorForCategory(category: String, isDark: Boolean = false): Color {
        return when (category.trim()) {
            "餐饮" -> if (isDark) DiningDark else Dining
            "交通" -> if (isDark) TransitDark else Transit
            "购物", "数码器材" -> if (isDark) ShoppingDark else Shopping
            "娱乐" -> if (isDark) LeisureDark else Leisure
            "住房" -> if (isDark) HousingDark else Housing
            "学习" -> if (isDark) StudyDark else Study
            "医疗" -> if (isDark) MedicalDark else Medical
            else -> if (isDark) OtherDark else Other
        }
    }
}
