package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 筛选条件持久化模型
 */
data class StreamFilterCriteria(
    val categories: Set<String> = emptySet(), // subset of "daily", "travel", "project", "wish"
    val year: Int? = null,
    val month: Int? = null, // 1..12
    val contents: Set<String> = emptySet() // subset of "has_image", "pending", "failed"
) {
    val isActive: Boolean
        get() = categories.isNotEmpty() || year != null || month != null || contents.isNotEmpty()

    val activeCount: Int
        get() {
            var count = categories.size
            if (year != null || month != null) count += 1
            count += contents.size
            return count
        }

    fun buildSummaryText(): String {
        val parts = mutableListOf<String>()
        if (categories.contains("daily")) parts.add("日常")
        if (categories.contains("travel")) parts.add("游历")
        if (categories.contains("project")) parts.add("造物")
        if (categories.contains("wish")) parts.add("心愿")

        if (year != null) {
            if (month != null) {
                parts.add("${year} 年 ${month} 月")
            } else {
                parts.add("${year} 年")
            }
        }

        if (contents.contains("has_image")) parts.add("有图")
        if (contents.contains("pending")) parts.add("待放行")
        if (contents.contains("failed")) parts.add("待重验")

        return parts.joinToString(" · ")
    }
}

val StreamFilterCriteriaSaver = listSaver<StreamFilterCriteria, Any>(
    save = { criteria ->
        listOf(
            criteria.categories.toList(),
            criteria.year ?: -1,
            criteria.month ?: -1,
            criteria.contents.toList()
        )
    },
    restore = { list ->
        @Suppress("UNCHECKED_CAST")
        val cats = (list[0] as List<String>).toSet()
        val y = (list[1] as Int).takeIf { it != -1 }
        val m = (list[2] as Int).takeIf { it != -1 }
        @Suppress("UNCHECKED_CAST")
        val conts = (list[3] as List<String>).toSet()
        StreamFilterCriteria(
            categories = cats,
            year = y,
            month = m,
            contents = conts
        )
    }
)

/**
 * 散页时间流 · 筛选面板 (Stream Filter Bottom Sheet)
 *
 * 规范要求：
 * - 底部弹层，纸色，无投影
 * - 四组组间只用间距分隔，不加线、不加卡片
 * - 分类：日常 / 游历 / 造物 / 心愿，多选，前置 6dp 色点，选中为正文色加粗 + 对勾，未选中为次要色；全不选等于全部
 * - 时间：年份（全部 / 有记录的年份）；选某年后展开 12 个月份文字格，有记录可点，无记录置灰
 * - 内容：有图、待放行、待重验（多选）
 * - 底部固定一行：左侧"清除"，右侧赤陶主按钮"查看 N 条"，N 随选择实时变化
 * - 所有可点击项热区 >= 48dp，字号与正文一致，衬线 + 等宽数字
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamFilterSheet(
    criteria: StreamFilterCriteria,
    availableYears: List<Int>,
    syncManager: SyncManager,
    onApply: (StreamFilterCriteria) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    var tempCriteria by remember { mutableStateOf(criteria) }

    // 实时联动：查询当前组合条件下的记录总数（本地 SQLite 毫秒级计算）
    var matchCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(tempCriteria) {
        matchCount = syncManager.countStreamEntriesFiltered(
            categories = tempCriteria.categories,
            year = tempCriteria.year,
            month = tempCriteria.month,
            contents = tempCriteria.contents
        )
    }

    // 实时查询选中年份下有记录的月份列表 (1~12)
    var availableMonths by remember(tempCriteria.year) { mutableStateOf<List<Int>>(emptyList()) }
    LaunchedEffect(tempCriteria.year) {
        val y = tempCriteria.year
        if (y != null) {
            val months = syncManager.getAvailableMonthsForYear(y)
            availableMonths = months
            // 若原先选中的月份在新年份下无记录，自动重置月份
            if (tempCriteria.month != null && !months.contains(tempCriteria.month)) {
                tempCriteria = tempCriteria.copy(month = null)
            }
        } else {
            availableMonths = emptyList()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetMaxHeight = (configuration.screenHeightDp * 0.85f).dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(colors.borderSubtle.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // 前三组可纵向滚动，保证无论字体缩放或月份展开，底部按钮始终固定可见
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // ================= 1. 分类组 =================
            Text(
                text = "分类",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 13.sp,
                color = colors.inkMuted
            )
            Spacer(modifier = Modifier.height(4.dp))

            val categoryItems = listOf(
                Triple("daily", "日常", colors.tagDaily),
                Triple("travel", "游历", colors.tagTravel),
                Triple("project", "造物", colors.tagProject),
                Triple("wish", "心愿", colors.tagWish)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((key, label, dotColor) in categoryItems) {
                    val isSelected = tempCriteria.categories.contains(key)
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val nextCats = if (isSelected) {
                                    tempCriteria.categories - key
                                } else {
                                    tempCriteria.categories + key
                                }
                                tempCriteria = tempCriteria.copy(categories = nextCats)
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(dotColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "✓",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }
            }

            // 组间仅用间距分隔
            Spacer(modifier = Modifier.height(20.dp))

            // ================= 2. 时间组 =================
            Text(
                text = "时间",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 13.sp,
                color = colors.inkMuted
            )
            Spacer(modifier = Modifier.height(4.dp))

            // 年份选项横向排列
            val yearScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(yearScrollState),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "全部"年份
                val isAllYear = tempCriteria.year == null
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            tempCriteria = tempCriteria.copy(year = null, month = null)
                        }
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "全部",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = if (isAllYear) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAllYear) colors.inkPrimary else colors.inkSecondary
                        )
                        if (isAllYear) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "✓",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }

                // 有记录的年份列表
                for (y in availableYears) {
                    val isYearSelected = tempCriteria.year == y
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                tempCriteria = if (isYearSelected) {
                                    tempCriteria.copy(year = null, month = null)
                                } else {
                                    tempCriteria.copy(year = y, month = null)
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${y}年",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 15.sp,
                                fontWeight = if (isYearSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isYearSelected) colors.inkPrimary else colors.inkSecondary
                            )
                            if (isYearSelected) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "✓",
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }
            }

            // 选了某一年后，下方出现 12 个月份文字格
            if (tempCriteria.year != null) {
                Spacer(modifier = Modifier.height(8.dp))

                // 第一行：1~6 月
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (m in 1..6) {
                        val isAvailable = availableMonths.contains(m)
                        val isMonthSelected = tempCriteria.month == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .then(
                                    if (isAvailable) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            tempCriteria = tempCriteria.copy(
                                                month = if (isMonthSelected) null else m
                                            )
                                        }
                                    } else Modifier
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${m}月",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 14.5.sp,
                                fontWeight = if (isMonthSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    !isAvailable -> colors.inkMuted.copy(alpha = 0.35f)
                                    isMonthSelected -> colors.inkPrimary
                                    else -> colors.inkSecondary
                                }
                            )
                        }
                    }
                }

                // 第二行：7~12 月
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (m in 7..12) {
                        val isAvailable = availableMonths.contains(m)
                        val isMonthSelected = tempCriteria.month == m
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .then(
                                    if (isAvailable) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            tempCriteria = tempCriteria.copy(
                                                month = if (isMonthSelected) null else m
                                            )
                                        }
                                    } else Modifier
                                )
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${m}月",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 14.5.sp,
                                fontWeight = if (isMonthSelected) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    !isAvailable -> colors.inkMuted.copy(alpha = 0.35f)
                                    isMonthSelected -> colors.inkPrimary
                                    else -> colors.inkSecondary
                                }
                            )
                        }
                    }
                }
            }

            // 组间仅用间距分隔
            Spacer(modifier = Modifier.height(20.dp))

            // ================= 3. 内容组 =================
            Text(
                text = "内容",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 13.sp,
                color = colors.inkMuted
            )
            Spacer(modifier = Modifier.height(4.dp))

            val contentItems = listOf(
                "has_image" to "有图",
                "pending" to "待放行",
                "failed" to "待重验"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((key, label) in contentItems) {
                    val isSelected = tempCriteria.contents.contains(key)
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val nextContents = if (isSelected) {
                                    tempCriteria.contents - key
                                } else {
                                    tempCriteria.contents + key
                                }
                                tempCriteria = tempCriteria.copy(contents = nextContents)
                            }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "✓",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }
            }

            }

            // 组间仅用间距分隔
            Spacer(modifier = Modifier.height(16.dp))

            // ================= 4. 底部固定一行 =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧文字按钮"清除"
                TextButton(
                    onClick = {
                        tempCriteria = StreamFilterCriteria()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "清除",
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 15.sp,
                        color = colors.inkMuted
                    )
                }

                // 右侧赤陶主按钮"查看 N 条"
                Button(
                    onClick = {
                        onApply(tempCriteria)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.archivePaper
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "查看 ",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$matchCount",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " 条",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
