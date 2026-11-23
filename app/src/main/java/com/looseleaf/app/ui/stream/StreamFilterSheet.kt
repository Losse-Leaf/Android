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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveSheet

/**
 * 筛选条件持久化模型
 */
data class StreamFilterCriteria(
    val categories: Set<String> = emptySet(), // subset of "daily", "wish"
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
 * - 分类：日常 / 心愿（多选，全不选等于全部）
 * - 时间：年份（全部 / 有记录的年份）；选某年后展开 12 个月份格
 * - 内容：有图、待放行、待重验（多选）
 * - 底部固定一行：左侧"清除"，右侧主按钮"查看 N 条"
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

    ArchiveSheet(
        onDismissRequest = onDismiss,
        title = "筛选归档",
        cancelText = "取消",
        actionText = if (tempCriteria.isActive) "重置" else null,
        onAction = { tempCriteria = StreamFilterCriteria() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 前三组可纵向滚动，保证无论字体缩放或月份展开，底部按钮始终固定可见
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                // ================= 1. 分类组 =================
            FilterGroupLabel("分类")
            Spacer(modifier = Modifier.height(10.dp))

            val categoryItems = listOf(
                Triple("daily", "日常", colors.tagDaily),
                Triple("wish", "心愿", colors.tagWish)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((key, label, dotColor) in categoryItems) {
                    val isSelected = tempCriteria.categories.contains(key)
                    com.looseleaf.app.ui.components.ArchiveSelectableCapsule(
                        text = label,
                        selected = isSelected,
                        leadingDotColor = dotColor,
                        onClick = {
                            val nextCats = if (isSelected) {
                                tempCriteria.categories - key
                            } else {
                                tempCriteria.categories + key
                            }
                            tempCriteria = tempCriteria.copy(categories = nextCats)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ================= 2. 时间组 =================
            FilterGroupLabel("时间")
            Spacer(modifier = Modifier.height(10.dp))

            val yearScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(yearScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllYear = tempCriteria.year == null
                com.looseleaf.app.ui.components.ArchiveSelectableCapsule(
                    text = "全部",
                    selected = isAllYear,
                    onClick = { tempCriteria = tempCriteria.copy(year = null, month = null) }
                )
                for (y in availableYears) {
                    val isYearSelected = tempCriteria.year == y
                    com.looseleaf.app.ui.components.ArchiveSelectableCapsule(
                        text = "${y}年",
                        selected = isYearSelected,
                        isMonospace = true,
                        onClick = {
                            tempCriteria = if (isYearSelected) {
                                tempCriteria.copy(year = null, month = null)
                            } else {
                                tempCriteria.copy(year = y, month = null)
                            }
                        }
                    )
                }
            }

            // 选了某一年后，下方出现 12 个月份格（两行六列）
            if (tempCriteria.year != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ArchiveShapes.lg)
                        .background(colors.archiveSubtle)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (rowMonths in listOf(1..6, 7..12)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (m in rowMonths) {
                                val isAvailable = availableMonths.contains(m)
                                val isMonthSelected = tempCriteria.month == m
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(ArchiveShapes.full)
                                        .background(if (isMonthSelected) colors.inverseSurface else Color.Transparent)
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
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${m}月",
                                        style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                                        fontWeight = if (isMonthSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            isMonthSelected -> colors.inverseInk
                                            !isAvailable -> colors.inkMuted.copy(alpha = 0.35f)
                                            else -> colors.inkSecondary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ================= 3. 内容组 =================
            FilterGroupLabel("内容")
            Spacer(modifier = Modifier.height(10.dp))

            val contentItems = listOf(
                "has_image" to "有图",
                "pending" to "待放行",
                "failed" to "待重验"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((key, label) in contentItems) {
                    val isSelected = tempCriteria.contents.contains(key)
                    com.looseleaf.app.ui.components.ArchiveSelectableCapsule(
                        text = label,
                        selected = isSelected,
                        onClick = {
                            val nextContents = if (isSelected) {
                                tempCriteria.contents - key
                            } else {
                                tempCriteria.contents + key
                            }
                            tempCriteria = tempCriteria.copy(contents = nextContents)
                        }
                    )
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
                        text = "重置",
                        style = com.looseleaf.app.theme.ArchiveTextStyles.label,
                        color = colors.inkSecondary
                    )
                }

                // 右侧主按钮"查看 N 条"
                Button(
                    onClick = {
                        onApply(tempCriteria)
                        onDismiss()
                    },
                    shape = ArchiveShapes.full,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.onAccent
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 50.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
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

/** 筛选分组小标：加宽字距的无衬线小字 */
@Composable
private fun FilterGroupLabel(text: String) {
    val colors = LooseLeafTheme.colors
    Text(
        text = text,
        style = com.looseleaf.app.theme.ArchiveTextStyles.overline,
        color = colors.inkMuted
    )
}
