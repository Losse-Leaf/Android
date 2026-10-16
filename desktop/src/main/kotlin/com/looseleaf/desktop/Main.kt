package com.looseleaf.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

/**
 * 散页常用移动机型规格定义
 */
enum class DeviceProfile(
    val title: String,
    val width: Dp,
    val height: Dp,
    val cornerRadius: Dp,
    val hasNotch: Boolean,
    val description: String
) {
    STANDARD("标准手机 (iPhone 16 / Pixel 8)", 390.dp, 844.dp, 44.dp, true, "390 × 844 · 19.5:9 现代黄金比例"),
    TALL("超长比例屏 (Galaxy S24 / Xiaomi)", 392.dp, 892.dp, 40.dp, true, "392 × 892 · 20.5:9 修长屏幕"),
    COMPACT("小屏紧凑机 (Compact / SE)", 360.dp, 640.dp, 28.dp, false, "360 × 640 · 16:9 紧凑单手尺寸"),
    FOLDABLE("折叠屏内屏展开 (Foldable)", 680.dp, 840.dp, 32.dp, false, "680 × 840 · 4:3 展开大视界"),
    TABLET("平板视界 (Tablet Portrait)", 780.dp, 960.dp, 24.dp, false, "780 × 960 · 大幅书卷画布")
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Loose-Leaf · Mobile Device Studio (散页移动端全机型体验视窗)",
        state = WindowState(width = 1260.dp, height = 980.dp)
    ) {
        DeviceStudioApp()
    }
}

@Composable
fun DeviceStudioApp() {
    var selectedDevice by remember { mutableStateOf(DeviceProfile.STANDARD) }
    var isDarkTheme by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("ledger") }
    var simulateKeyboard by remember { mutableStateOf(false) }

    // 主色调：暖色棉纸 (#F9F8F5) 与暗房安全灯 (#181716 / #D9532F)
    val paperBg = if (isDarkTheme) Color(0xFF141312) else Color(0xFFF9F8F5)
    val studioBg = if (isDarkTheme) Color(0xFF0D0C0B) else Color(0xFFECEAE4)
    val textPrimary = if (isDarkTheme) Color(0xFFEBE8E1) else Color(0xFF181716)
    val textMuted = if (isDarkTheme) Color(0xFF88847C) else Color(0xFF8C887F)
    val accentWarm = Color(0xFFA24F2B)
    val accentMoss = Color(0xFF2E6B4E)
    val borderDivider = if (isDarkTheme) Color(0xFF2B2825) else Color(0xFFE2DFD6)

    Row(modifier = Modifier.fillMaxSize().background(studioBg)) {
        // 左侧机型调节与中枢面板
        Column(
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .background(if (isDarkTheme) Color(0xFF1A1918) else Color(0xFFFAF9F6))
                .border(width = 0.5.dp, color = borderDivider)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 工作台刊头
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "散页 · 移动端全机型工作台",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = textPrimary
                )
                Text(
                    text = "无需模拟器 · Compose Desktop 原生 60FPS 实时预览",
                    fontSize = 11.sp,
                    color = textMuted
                )
            }

            HorizontalDivider(color = borderDivider)

            // 机型规格选择器
            Text(
                text = "1. 常用测试机型与屏幕尺寸",
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = textPrimary
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DeviceProfile.values().forEach { profile ->
                    val isSelected = selectedDevice == profile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) accentWarm.copy(alpha = 0.12f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) accentWarm else borderDivider,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedDevice = profile }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = profile.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) accentWarm else textPrimary
                            )
                            Text(
                                text = profile.description,
                                fontSize = 10.sp,
                                color = textMuted
                            )
                        }
                        if (isSelected) {
                            Text("✓", fontSize = 12.sp, color = accentWarm)
                        }
                    }
                }
            }

            HorizontalDivider(color = borderDivider)

            // 屏幕场景切换
            Text(
                text = "2. 交互体验模块",
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = textPrimary
            )
            val screenTabs = listOf(
                "ledger" to "口袋账本 (三栏架构)",
                "stream" to "时光手账 (时间流)",
                "darkroom" to "随行暗房 (4K相纸)",
                "projects" to "造物 · 心愿 · 山川",
                "profile" to "主理人展台"
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                screenTabs.forEach { (route, label) ->
                    val isSelected = currentScreen == route
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) textPrimary else Color.Transparent)
                            .clickable { currentScreen = route }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) (if (isDarkTheme) Color(0xFF141312) else Color.White) else textPrimary
                        )
                    }
                }
            }

            HorizontalDivider(color = borderDivider)

            // 拟真交互开关
            Text(
                text = "3. 硬件环境与场景模拟",
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("胶片暗房模式", fontSize = 12.sp, color = textPrimary)
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = { isDarkTheme = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentWarm, checkedTrackColor = accentWarm.copy(alpha = 0.5f))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("模拟弹出软键盘 (IME)", fontSize = 12.sp, color = textPrimary)
                    Text("检测输入框与底部遮挡安全区", fontSize = 10.sp, color = textMuted)
                }
                Switch(
                    checked = simulateKeyboard,
                    onCheckedChange = { simulateKeyboard = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = accentWarm, checkedTrackColor = accentWarm.copy(alpha = 0.5f))
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Loose-Leaf Core v1.2.0 · 散页移动端工作室",
                fontSize = 10.sp,
                color = textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 右侧：全机型拟真渲染画板 (带铝合金机身外壳与刘海圆角)
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            DeviceBezelFrame(
                profile = selectedDevice,
                isDarkTheme = isDarkTheme,
                simulateKeyboard = simulateKeyboard,
                content = {
                    when (currentScreen) {
                        "ledger" -> MockLedgerScreen(isDarkTheme = isDarkTheme)
                        "stream" -> MockStreamScreen(isDarkTheme = isDarkTheme)
                        "darkroom" -> MockDarkroomScreen(isDarkTheme = isDarkTheme)
                        "projects" -> MockProjectsScreen(isDarkTheme = isDarkTheme)
                        "profile" -> MockProfileScreen(isDarkTheme = isDarkTheme)
                    }
                }
            )
        }
    }
}

/**
 * 拟真硬件机身边框 (铝合金外框 + 天线白带 + 灵动岛/刘海 + Home 手势横条)
 */
@Composable
fun DeviceBezelFrame(
    profile: DeviceProfile,
    isDarkTheme: Boolean,
    simulateKeyboard: Boolean,
    content: @Composable () -> Unit
) {
    val bezelBorder = if (isDarkTheme) Color(0xFF383531) else Color(0xFFC7C3B7)
    val bezelBg = if (isDarkTheme) Color(0xFF22201D) else Color(0xFFE4E1D8)
    val screenBg = if (isDarkTheme) Color(0xFF141312) else Color(0xFFF9F8F5)

    Column(
        modifier = Modifier
            .wrapContentSize()
            .shadow(elevation = 28.dp, shape = RoundedCornerShape(profile.cornerRadius + 6.dp), spotColor = Color.Black.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(profile.cornerRadius + 6.dp))
            .background(bezelBg)
            .border(width = 2.dp, color = bezelBorder, shape = RoundedCornerShape(profile.cornerRadius + 6.dp))
            .padding(10.dp) // 极窄物理黑边
    ) {
        // 手机屏幕视窗
        Box(
            modifier = Modifier
                .size(width = profile.width, height = profile.height)
                .clip(RoundedCornerShape(profile.cornerRadius))
                .background(screenBg)
        ) {
            // 真实屏幕渲染内容
            Box(modifier = Modifier.fillMaxSize().padding(bottom = if (simulateKeyboard) 280.dp else 0.dp)) {
                content()
            }

            // 顶部状态栏与灵动岛/刘海
            if (profile.hasNotch) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .width(110.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black)
                )
            }

            // 模拟软键盘弹出态
            if (simulateKeyboard) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(if (isDarkTheme) Color(0xFF2A2826) else Color(0xFFD4D0C5))
                        .border(width = 0.5.dp, color = Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Keyboard, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                        Text("系统软键盘已调出 (IME Insets)", fontSize = 13.sp, color = Color.Gray)
                        Text("测试输入框是否被键盘遮挡", fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.7f))
                    }
                }
            }

            // 底部 Home 手势指示条
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .width(134.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(if (isDarkTheme) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.2f))
            )
        }
    }
}

// ==========================================
// 拟真互动体验组件 (带真实分位计算与交互)
// ==========================================

data class Palette(
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val warm: Color,
    val moss: Color,
    val border: Color
)

@Composable
fun MockLedgerScreen(isDarkTheme: Boolean) {
    var selectedTab by remember { mutableStateOf(0) }
    var amountString by remember { mutableStateOf("42.50") }
    var selectedType by remember { mutableStateOf("expense") }
    var selectedCategory by remember { mutableStateOf("餐饮") }
    var selectedPayment by remember { mutableStateOf("微信支付") }
    var isInstallment by remember { mutableStateOf(false) }

    val colors = if (isDarkTheme) {
        Palette(
            bg = Color(0xFF141312),
            card = Color(0xFF1E1D1B),
            text = Color(0xFFEBE8E1),
            muted = Color(0xFF88847C),
            warm = Color(0xFFA24F2B),
            moss = Color(0xFF2E6B4E),
            border = Color(0xFF2E2C28)
        )
    } else {
        Palette(
            bg = Color(0xFFF9F8F5),
            card = Color(0xFFFFFFFF),
            text = Color(0xFF181716),
            muted = Color(0xFF8C887F),
            warm = Color(0xFFA24F2B),
            moss = Color(0xFF2E6B4E),
            border = Color(0xFFE6E3DA)
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 26.dp)) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("散页 · 口袋账本", fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.text)
                Text("极速触觉记账 · 资产流动大盘", fontSize = 10.sp, color = colors.muted)
            }
            Text("🔒", fontSize = 14.sp)
        }

        // 三项分段 Tab 切换器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDarkTheme) Color(0xFF242220) else Color(0xFFEDEAE1))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("极速记账", "账单明细 (12)", "资产与信贷").forEachIndexed { idx, tab ->
                val isSel = selectedTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) colors.card else Color.Transparent)
                        .clickable { selectedTab = idx }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                        color = if (isSel) colors.text else colors.muted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> {
                // 记账卡片
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.card)
                            .border(0.5.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 支出/收入与支付方式
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkTheme) Color(0xFF2A2825) else Color(0xFFEDEAE1))
                                    .padding(2.dp)
                            ) {
                                listOf("expense" to "支出", "income" to "收入").forEach { (type, label) ->
                                    val isTypeSel = selectedType == type
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isTypeSel) colors.warm else Color.Transparent)
                                            .clickable { selectedType = type }
                                            .padding(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, color = if (isTypeSel) Color.White else colors.muted)
                                    }
                                }
                            }

                            // 支付方式
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("微信支付", "支付宝", "现金").forEach { pm ->
                                    val isPm = selectedPayment == pm
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isPm) colors.text else Color.Transparent)
                                            .border(0.5.dp, if (isPm) colors.text else colors.border, RoundedCornerShape(8.dp))
                                            .clickable { selectedPayment = pm }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(pm, fontSize = 10.sp, color = if (isPm) colors.card else colors.muted)
                                    }
                                }
                            }
                        }

                        // 大金额展示
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(if (selectedType == "income") "+ ¥" else "¥", fontSize = 18.sp, color = if (selectedType == "income") colors.moss else colors.warm, modifier = Modifier.padding(bottom = 4.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(amountString, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = colors.text)
                        }

                        // 快速备注
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDarkTheme) Color(0xFF171615) else Color(0xFFF2EFE8))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("手冲咖啡 · 瑰夏水洗豆", fontSize = 11.sp, color = colors.muted)
                        }

                        // 分期选项
                        if (selectedType == "expense") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = isInstallment, onCheckedChange = { isInstallment = it })
                                    Text("分期还款入账", fontSize = 11.sp, color = colors.text)
                                }
                                if (isInstallment) {
                                    Text("12 期 (月供 ¥3.54)", fontSize = 10.sp, color = colors.warm)
                                }
                            }
                        }
                    }

                    // 分类胶囊滑动行
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("餐饮", "交通", "购物", "数码器材", "娱乐", "住房", "学习").forEach { cat ->
                            val isSel = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) colors.warm.copy(alpha = 0.15f) else colors.card)
                                    .border(if (isSel) 1.dp else 0.5.dp, if (isSel) colors.warm else colors.border, RoundedCornerShape(12.dp))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(cat, fontSize = 11.sp, color = if (isSel) colors.warm else colors.muted)
                            }
                        }
                    }

                    // 拟真键盘 4x3
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf(".", "0", "⌫")
                        )
                        rows.forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { k ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors.card)
                                            .border(0.5.dp, colors.border, RoundedCornerShape(4.dp))
                                            .clickable {
                                                when (k) {
                                                    "⌫" -> if (amountString.length > 1) amountString = amountString.dropLast(1) else amountString = "0"
                                                    "." -> if (!amountString.contains(".")) amountString += "."
                                                    else -> if (amountString == "0") amountString = k else amountString += k
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(k, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = colors.text)
                                    }
                                }
                            }
                        }
                    }

                    // 盖章记下
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.warm)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("【 盖章记下 (SAVE) 】", fontFamily = FontFamily.Serif, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            1 -> {
                // 账单明细流水 Tab
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 月度指标卡片
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.card)
                            .border(0.5.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("本月支出", fontSize = 10.sp, color = colors.muted)
                                Text("¥ 3,420.50", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.warm)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("本月收入", fontSize = 10.sp, color = colors.muted)
                                Text("¥ 12,500.00", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.moss)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("收支结余", fontSize = 10.sp, color = colors.muted)
                                Text("¥ +9,079.50", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = colors.text)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 分类光谱条
                        Row(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.5.dp))) {
                            Box(modifier = Modifier.weight(0.45f).fillMaxHeight().background(Color(0xFF8B4513)))
                            Box(modifier = Modifier.weight(0.25f).fillMaxHeight().background(Color(0xFF2E6B4E)))
                            Box(modifier = Modifier.weight(0.18f).fillMaxHeight().background(Color(0xFFA24F2B)))
                            Box(modifier = Modifier.weight(0.12f).fillMaxHeight().background(Color(0xFF6A5ACD)))
                        }
                    }

                    // 流水列表
                    listOf(
                        Triple("手冲咖啡 · 瑰夏", "餐饮 · 微信支付", "-¥ 42.50"),
                        Triple("地铁月度扣款", "交通 · 银行卡", "-¥ 186.00"),
                        Triple("二手富士镜头回血", "二手回血 · 支付宝", "+¥ 2,800.00"),
                        Triple("武康路周末法餐", "餐饮 · 微信支付", "-¥ 560.00")
                    ).forEach { (title, subtitle, amt) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.card)
                                .border(0.5.dp, colors.border, RoundedCornerShape(4.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.text)
                                Text(subtitle, fontSize = 10.sp, color = colors.muted)
                            }
                            Text(
                                amt,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (amt.startsWith("+")) colors.moss else colors.warm
                            )
                        }
                    }
                }
            }
            2 -> {
                // 资产大盘 Tab
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.card)
                            .border(0.5.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        Text("个人核心净资产", fontSize = 11.sp, color = colors.muted)
                        Text("¥ 88,430.00", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = colors.text)

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("流动可用资金", fontSize = 10.sp, color = colors.muted)
                                Text("¥ 45,210.00", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.moss)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("在役固定资产", fontSize = 10.sp, color = colors.muted)
                                Text("¥ 48,000.00 (5件)", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.text)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("待还信贷负债", fontSize = 10.sp, color = colors.muted)
                                Text("¥ 4,780.00", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = colors.warm)
                            }
                        }
                    }

                    Text("在役固定资产设备 (5)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.text)
                    listOf(
                        Pair("索尼 A7M4 微单机身", "¥ 13,800.00 · 影像器材"),
                        Pair("苹果 MacBook Air 15寸", "¥ 9,499.00 · 工作台"),
                        Pair("适马 24-70mm F2.8 二代", "¥ 7,899.00 · 镜头群")
                    ).forEach { (name, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.card)
                                .border(0.5.dp, colors.border, RoundedCornerShape(4.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.text)
                                Text(desc, fontSize = 10.sp, color = colors.muted)
                            }
                            Text("出清回血", fontSize = 10.sp, color = colors.warm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockStreamScreen(isDarkTheme: Boolean) {
    val textPrimary = if (isDarkTheme) Color(0xFFEBE8E1) else Color(0xFF181716)
    val textMuted = if (isDarkTheme) Color(0xFF88847C) else Color(0xFF8C887F)
    val cardBg = if (isDarkTheme) Color(0xFF1E1D1B) else Color(0xFFFFFFFF)
    val accentWarm = Color(0xFFA24F2B)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 26.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("散页 · 时光手账", fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Text("生活自留地 · 真实时间轴", fontSize = 10.sp, color = textMuted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔍 检索", fontSize = 11.sp, color = textPrimary)
                Text("+ 记随笔", fontSize = 11.sp, color = accentWarm)
            }
        }

        // 那年今日折叠卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(accentWarm.copy(alpha = 0.08f))
                .border(0.5.dp, accentWarm.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("那年今日 · 岁月回响 (1 篇)", fontSize = 11.sp, color = accentWarm)
            Text("展开 ▼", fontSize = 11.sp, color = accentWarm)
        }

        // 条目 1
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cardBg)
                .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("游历", fontSize = 10.sp, color = accentWarm)
                Text("2026.10.15 · 大理古城", fontSize = 10.sp, color = textMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("洱海边吹风看日落", fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("暮色染红了苍山顶的积雪，水鸟低飞，空气中有木香与微凉的气息。", fontSize = 12.sp, color = textMuted, lineHeight = 18.sp)
        }

        // 条目 2
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(cardBg)
                .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("造物", fontSize = 10.sp, color = accentWarm)
                Text("2026.10.14 · 杭州", fontSize = 10.sp, color = textMuted)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Loose-Leaf 移动端交互闭环完成", fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("重构了三栏式口袋账本与动态网卡探测，手机端扫码连接顺畅无阻。", fontSize = 12.sp, color = textMuted, lineHeight = 18.sp)
        }
    }
}

@Composable
fun MockDarkroomScreen(isDarkTheme: Boolean) {
    val textPrimary = if (isDarkTheme) Color(0xFFEBE8E1) else Color(0xFF181716)
    val textMuted = if (isDarkTheme) Color(0xFF88847C) else Color(0xFF8C887F)
    val accentWarm = Color(0xFFA24F2B)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 26.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("随行暗房 · 现场相纸", fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Text("端侧无损 4K 压制 · 自动解析 EXIF 城市坐标", fontSize = 11.sp, color = textMuted)

        // 按钮组
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(3.dp)).background(accentWarm.copy(alpha = 0.15f)).border(1.dp, accentWarm, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📷 现场快门直拍", fontSize = 12.sp, color = accentWarm)
            }
            Box(
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(3.dp)).background(Color.Gray.copy(alpha = 0.15f)).border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🖼️ 相册装入相纸", fontSize = 12.sp, color = textPrimary)
            }
        }

        // 装裱相框
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isDarkTheme) Color(0xFF1E1D1B) else Color.White)
                .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("轻触或拖拽装入胶片相纸", fontSize = 13.sp, color = textMuted)
                Text("支持最多 9 张接触印相", fontSize = 11.sp, color = textMuted.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
fun MockProjectsScreen(isDarkTheme: Boolean) {
    val textPrimary = if (isDarkTheme) Color(0xFFEBE8E1) else Color(0xFF181716)
    val textMuted = if (isDarkTheme) Color(0xFF88847C) else Color(0xFF8C887F)
    val accentWarm = Color(0xFFA24F2B)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("造物 · 心愿 · 山川", fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text("+ 新建", fontSize = 12.sp, color = accentWarm)
        }

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha = 0.15f)).padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("造物 (3)", "心愿 (8)", "山川 (4)").forEachIndexed { idx, tab ->
                Text(tab, fontSize = 11.sp, color = if (idx == 0) accentWarm else textMuted, modifier = Modifier.padding(vertical = 4.dp))
            }
        }

        listOf(
            Triple("Loose-Leaf 散页数字生活志", "进行中", "多端自留地与银盐暗房"),
            Triple("手工木作咖啡手冲架", "已完成", "北美黑胡桃木与黄铜固定柱"),
            Triple("个人摄影独立出版物", "筹备中", "胶片摄影作品集编排")
        ).forEach { (title, status, desc) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDarkTheme) Color(0xFF1E1D1B) else Color.White)
                    .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                    Text(status, fontSize = 10.sp, color = accentWarm)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = textMuted)
            }
        }
    }
}

@Composable
fun MockProfileScreen(isDarkTheme: Boolean) {
    val textPrimary = if (isDarkTheme) Color(0xFFEBE8E1) else Color(0xFF181716)
    val textMuted = if (isDarkTheme) Color(0xFF88847C) else Color(0xFF8C887F)
    val accentWarm = Color(0xFFA24F2B)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 26.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("主理人展台与设备中枢", fontFamily = FontFamily.Serif, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
        Text("生活自留地 · 随身设备中枢", fontSize = 10.sp, color = textMuted)

        // 名片
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isDarkTheme) Color(0xFF1E1D1B) else Color.White)
                .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(54.dp).clip(CircleShape).background(accentWarm.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("星河", fontSize = 16.sp, color = accentWarm, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("主理人 · 软件与独立造物者", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text("“ 认认真真过好具体的生活，保持安宁与自洽。 ”", fontSize = 11.sp, color = textMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }

        // 设备凭证
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isDarkTheme) Color(0xFF1E1D1B) else Color.White)
                .border(0.5.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("受信任设备凭据", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("设备名称", fontSize = 11.sp, color = textMuted)
                Text("星河的随行手机 [重命名]", fontSize = 11.sp, color = textPrimary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("数据中枢", fontSize = 11.sp, color = textMuted)
                Text("http://100.112.108.52:8088", fontSize = 11.sp, color = textMuted)
            }
        }
    }
}
