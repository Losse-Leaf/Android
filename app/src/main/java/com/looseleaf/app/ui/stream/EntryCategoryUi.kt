package com.looseleaf.app.ui.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.looseleaf.app.model.EntryCategory
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveColors
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.ArchiveSelectableCapsule

/**
 * 散页分类与标题的展示规则（时间流卡片、详情页、筛选面板共用）。
 */

/** 分类色点：日常松绿，心愿赭金，四个画廊分类各取一种矿物色 */
internal fun entryCategoryTint(category: EntryCategory, colors: ArchiveColors): Color = when (category) {
    EntryCategory.DAILY -> colors.tagDaily
    EntryCategory.PHOTOGRAPHY -> colors.tagPhotography
    EntryCategory.TRAVEL -> colors.tagTravel
    EntryCategory.STILL_LIFE -> colors.tagStillLife
    EntryCategory.MOTION -> colors.tagMotion
}

/** 卡片 / 详情页首行的分类签：心愿优先，其次画廊分类，空分类显示日常 */
internal fun entryTagOf(entry: LifeEntry, colors: ArchiveColors): Pair<String, Color> =
    if (entry.type == "wish") {
        "心愿" to colors.tagWish
    } else {
        val category = EntryCategory.fromCode(entry.category)
        category.label to entryCategoryTint(category, colors)
    }

/** 历史版本自动塞入的占位标题：它们不是用户写下的标题，不应作为大标题渲染 */
private val LEGACY_PLACEHOLDER_TITLES = setOf("无题随笔", "无题")
private val LEGACY_PLACEHOLDER_PREFIXES = listOf("现场快门 · ", "现场记录 · ")

/**
 * 标题是否值得单独成行。
 *
 * 以下情形视为「派生或占位」，界面隐藏标题行、直接展示正文，避免同一句话出现两次：
 * - 标题为空；
 * - 历史占位标题（无题随笔、现场快门 / 现场记录前缀）；
 * - 标题只是正文首句的截取（后端或旧客户端从正文首行派生，可能带省略号）。
 */
internal fun LifeEntry.hasDistinctTitle(): Boolean {
    val t = title.trim()
    if (t.isEmpty()) return false
    if (t in LEGACY_PLACEHOLDER_TITLES) return false
    if (LEGACY_PLACEHOLDER_PREFIXES.any { t.startsWith(it) }) return false
    val core = t.removeSuffix("…").removeSuffix("...").trim()
    if (core.isEmpty()) return false
    val body = content.trim()
    return !(body.isNotEmpty() && body.startsWith(core))
}

/**
 * 原生分类选择胶囊组：日常 / 摄影 / 旅途 / 静物 / 影像，单选。
 * 用 FlowRow 排布，窄屏或大字号下自动换行，任何一枚胶囊都不会被截断。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryCategoryPicker(
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    val selected = EntryCategory.fromCode(selectedCode)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EntryCategory.entries.forEach { category ->
            ArchiveSelectableCapsule(
                text = category.label,
                selected = category == selected,
                leadingDotColor = entryCategoryTint(category, colors),
                onClick = { onSelect(category.code) }
            )
        }
    }
}
