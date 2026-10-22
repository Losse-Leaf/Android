package com.looseleaf.app.ui.projectswishes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
@Composable
internal fun ProjectsList(
    projects: List<Project>,
    onProjectClick: (Project) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (projects.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text(stringResource(R.string.projects_wishes_empty_projects), fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = // MOB-P2-63：本页没有浮动印章，不需要给 FAB 预留 84dp 底部空间
                    PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        items(projects, key = { it.id }) { proj ->
            PaperCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProjectClick(proj) }
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = proj.title,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.inkPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.projects_wishes_lists_contentDescription_01),
                                tint = colors.inkMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (!proj.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = proj.description,
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                    }

                    if (!proj.githubUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.archivePaper)
                                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_code), contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = proj.githubUrl,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.accentWarm
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WishesList(
    wishes: List<Wish>,
    onResolveClick: (Wish) -> Unit,
    onEditClick: (Wish) -> Unit,
    onDeleteClick: (Wish) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var statusFilter by rememberSaveable { mutableStateOf("all") }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val filteredWishes = remember(wishes, statusFilter) {
        if (statusFilter == "all") wishes
        else wishes.filter { it.status == statusFilter }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 状态筛选条
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val statusFilters = listOf(
                "all" to "全部 (${wishes.size})",
                "wanting" to "向往中 (${wishes.count { it.status == "wanting" }})",
                "fulfilled" to "已圆满 (${wishes.count { it.status == "fulfilled" }})",
                "relinquished" to "已拔草 (${wishes.count { it.status == "relinquished" }})"
            )
            statusFilters.forEach { (k, label) ->
                val isSelected = statusFilter == k
                PaperFilterPill(
                    selected = isSelected,
                    onClick = { statusFilter = k },
                    label = label
                )
            }
        }

        if (filteredWishes.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().weight(1f)) {
                Text(stringResource(R.string.projects_wishes_empty_wishes), fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
            }
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 84.dp)
        ) {
            items(filteredWishes, key = { it.id }) { wish ->
                val isFulfilled = wish.status == "fulfilled" || wish.status == "relinquished"
                PaperCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    painter = if (isFulfilled) painterResource(R.drawable.ic_check_circle) else painterResource(R.drawable.ic_lightbulb),
                                    contentDescription = null,
                                    tint = if (isFulfilled) colors.accentMoss else colors.accentWarm,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = wish.title,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.inkPrimary,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isFulfilled) {
                                    TextButton(onClick = { onResolveClick(wish) }) {
                                        Text(stringResource(R.string.projects_wishes_achieve), fontSize = 12.sp, color = colors.accentMoss)
                                    }
                                } else {
                                    Text(
                                        text = if (wish.status == "relinquished") "已拔草" else "已圆满",
                                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                                        fontSize = 12.sp,
                                        color = if (wish.status == "relinquished") colors.inkMuted else colors.accentMoss
                                    )
                                }
                                IconButton(
                                    onClick = { onEditClick(wish) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.projects_wishes_lists_contentDescription_02),
                                        tint = colors.inkSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteClick(wish) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.projects_wishes_lists_contentDescription_03),
                                        tint = colors.inkMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!wish.note.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = wish.note,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 13.sp,
                                color = colors.inkSecondary
                            )
                        }

                        if (wish.budget > 0 || wish.actualCost > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.projects_wishes_lists_text_tpl_01, FormatUtils.centsToYuanString(wish.budget), FormatUtils.centsToYuanString(wish.actualCost)),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FootprintsList(
    footprints: List<Footprint>,
    onFootprintClick: (Footprint) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    if (footprints.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text(stringResource(R.string.projects_wishes_empty_footprints), fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = // MOB-P2-63：本页没有浮动印章，不需要给 FAB 预留 84dp 底部空间
                    PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        items(footprints, key = { it.id }) { foot ->
            PaperCard(
                modifier = Modifier.clickable { onFootprintClick(foot) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左联：副券存根 (目的地中文名 + 到访日期)
                    Column(
                        modifier = Modifier
                            .width(108.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = foot.cityName,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = foot.visitedAt?.takeIf { it.isNotBlank() }?.let { FormatUtils.formatDisplayDate(it) } ?: "行迹存根",
                            fontFamily = ArchiveFontFamilies.MonoScale,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }

                    // 齿孔撕票分割线
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .padding(vertical = 8.dp)
                            .background(colors.borderDivider)
                    )

                    // 右联：正券手记 + 邮戳印记
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = foot.title?.takeIf { it.isNotBlank() } ?: "${foot.cityName} · 行旅手记",
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.inkPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(R.string.projects_wishes_lists_text_tpl_02, foot.cityCode),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.accentWarm
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = foot.note?.takeIf { it.isNotBlank() } ?: "行至此处，留下一纸驻足印记。",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 12.sp,
                            color = colors.inkSecondary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}
