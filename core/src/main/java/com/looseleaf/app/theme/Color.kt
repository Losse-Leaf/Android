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
    val tagProject: Color = Color(0xFF825C2C),
    val tagWish: Color = Color(0xFF8B4E4E),
    /** 相机取景框的纯黑衬底：Preview surface 外的 letterbox 必须是绝对黑，两种模式共用 */
    val cameraViewfinderBg: Color = Color(0xFF000000),
    val isDark: Boolean = false,
    /** 低饱和砖红（异常 / 警告 / 破坏性操作专用） */
    val alertRed: Color = if (isDark) Color(0xFFD08888) else Color(0xFFA04848),

    // ——— 语义态令牌（第 1 批新增，注意：默认值按「纸」推导，暗房需显式覆写）———

    /**
     * 按压态叠加层：盖在原色上的一层极淡墨（纸）／极淡白（暗房）。
     * 用于 `indication = null` 的手写按压反馈，替代 Material 默认涟漪。
     */
    val pressOverlay: Color = inkPrimary.copy(alpha = 0.06f),
    /**
     * 结构性细边：比 [borderSubtle]（9%）/[borderDivider]（13%）更明确的一档（16%），
     * 用于输入框下划线、卡片轮廓等「需要被看见」的边界。
     */
    val lineEdge: Color = inkPrimary.copy(alpha = 0.16f),
    /** 全屏遮罩（弹窗/抽屉背后），黑 32% */
    val scrim: Color = Color(0x52000000),
    /**
     * 主色按压态：主按钮按下时的底色。
     * **必填**——它不能靠 alpha 叠加推导：浅底主题按下要更深、深底主题按下要更亮，
     * 一律 alpha 会让深底主题按下瞬间文字对比度崩到 3.72:1。
     */
    val accentPress: Color,
    /**
     * 小字号赤陶（11–14sp 标签、角标、选中态小字）。
     * **必填**——纸面不能用 [accentWarm]：赤陶小字最常见的落点是「12% 赤陶淡底」，
     * 同值时只有 4.28:1 不达标。纸 #8E4423 / 暗房 #E28A63。
     */
    val accentSmallText: Color,
    /**
     * 禁用态前景：只表达「不可用」，不追求 4.5:1 —— WCAG 明确豁免禁用控件。
     * 但必须与「正常次要文字」[inkMuted] 有肉眼可辨的落差，否则用户会反复点击。
     */
    val inkDisabled: Color = inkMuted
)

// 暖色棉纸模式 (Paper Mode)
val LightArchiveColors = ArchiveColors(
    archivePaper = Color(0xFFF6F0E4),   // 暖米色纸底
    archiveSubtle = Color(0xFFF0ECE3),
    archiveCard = Color(0xFFFCFAF5),    // 散页物理纸片：比背景略亮的米白
    inkPrimary = Color(0xFF1D1B18),     // 碳素深棕黑
    inkSecondary = Color(0xFF5C574F),   // 暖木炭灰
    inkMuted = Color(0xFF6E6860),       // 原 #787268 只有 4.20:1(canvas)/4.04:1(surface)，改后 4.85/4.67
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
    // 造物：琥珀。原 #9E723B 在 canvas 上 3.76:1、在自身 10% 淡底上仅 3.36:1；
    // 设计给的 #855F2E 在 10% 淡底上仍只有 4.45:1（差 0.05），故再深半档到 #825C2C。
    tagProject = Color(0xFF825C2C),     // canvas 5.26:1 / 10% 淡底 4.61:1
    tagWish = Color(0xFF8B4E4E),        // 心愿：枯茶红。原 #945353 已达标(5.11)，加深后 5.61 留出小字余量
    isDark = false,
    // 异常/警告/破坏性：原 #B35C5C 只有 4.02:1，改后 5.26:1（10% 淡底 4.58:1）
    alertRed = Color(0xFFA04848),
    accentPress = Color(0xFF8B4022),    // 主色按压：canvas 6.48:1
    // 11–14sp 小字赤陶。**不能等于 accentWarm**：全站最常见的选中表达是
    // 「12% 赤陶淡底 + 赤陶小字」（年份 chip、分段计数徽标、筛选胶囊），
    // #A24F2B 压在 12% 淡底 #EADBCD 上只有 4.28:1，不达标；
    // #8E4423 在 canvas 6.17 / surface 5.93 / 12% 淡底 5.17 / 8% 淡底 5.49，全过。
    accentSmallText = Color(0xFF8E4423),
    inkDisabled = Color(0xFF9A9389),    // 禁用态：与 inkMuted 肉眼可辨，WCAG 豁免
    pressOverlay = Color(0xFF1D1B18).copy(alpha = 0.06f),
    lineEdge = Color(0xFF1D1B18).copy(alpha = 0.16f)
)

// 暗房安全灯模式 (Darkroom Mode)
val DarkArchiveColors = ArchiveColors(
    archivePaper = Color(0xFF181512),   // 夜间台灯下的熟牛皮纸底
    archiveSubtle = Color(0xFF221F1B),
    archiveCard = Color(0xFF23201C),    // 纸面略亮
    inkPrimary = Color(0xFFEDE5D8),     // 暖乳白墨迹
    inkSecondary = Color(0xFFAFA89B),
    inkMuted = Color(0xFF968F83),       // 原 #8A8377 只有 4.32:1(card)，改后 5.67/5.12
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
    // 造物/心愿/砖红三色「同比例加深」在暗房里是反向操作：
    // 暗房是浅字压深底，加深等于降对比。实测同比例 ×0.842 后
    // tagProject 4.67、tagWish 3.60、alertRed 4.28 —— 后两者直接跌破 4.5。
    // 故暗房保持原值（均已 ≥4.88），只提亮、不加深。
    tagProject = Color(0xFFC79A63),     // 造物：琥珀（7.13:1）
    tagWish = Color(0xFFC27878),        // 心愿：枯茶红（5.41:1，10% 淡底 4.78:1）
    isDark = true,
    alertRed = Color(0xFFD08888),       // 6.55:1，10% 淡底 5.71:1
    // 按下态规则：**相对亮度必须朝"与文字相反方向"移动** ——
    // 浅底主题按下要更深（文字仍是纸白），深底主题按下要更亮（文字是深纸色）。
    // 原先这里写 accentWarm.copy(alpha = 0.85f)，合成到 #181512 上得到 #B45537，
    // 按下瞬间文字 #181512 压上去只有 3.72:1，字会糊。改成不透明的 #E28A63（6.97:1）。
    accentPress = Color(0xFFE28A63),
    // 11–12sp 小字赤陶：accentWarm 在 surface 上只有 4.24:1，小字余量太薄
    accentSmallText = Color(0xFFE28A63), // 6.97:1 / surface 6.29 / 12% 淡底 5.82
    inkDisabled = Color(0xFF6B655B),
    pressOverlay = Color(0xFFFFFFFF).copy(alpha = 0.06f),
    lineEdge = Color(0xFFFFFFFF).copy(alpha = 0.16f)
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
