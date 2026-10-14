package com.looseleaf.app.ui.projectswishes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.haptics.HapticsEngine
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.NoiseBackground
import com.looseleaf.app.ui.components.PaperBadge
import com.looseleaf.app.ui.components.PaperCard
import com.looseleaf.app.utils.FormatUtils

/**
 * 造物、心愿与山川足迹视界 (ProjectsWishesScreen)
 * 严格遵从 docs/README.md §三.8 记录为一切的母体原则
 */
@Composable
fun ProjectsWishesScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LooseLeafTheme.colors
    val apiClient = remember { APIClient.getInstance(context) }
    val haptics = remember { HapticsEngine.getInstance(context) }

    var selectedTab by remember { mutableStateOf(0) } // 0: 造物, 1: 心愿, 2: 足迹
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var wishes by remember { mutableStateOf<List<Wish>>(emptyList()) }
    var footprints by remember { mutableStateOf<List<Footprint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            projects = runCatching { apiClient.fetchProjects() }.getOrDefault(emptyList())
            wishes = runCatching { apiClient.fetchWishes() }.getOrDefault(emptyList())
            footprints = runCatching { apiClient.fetchFootprints() }.getOrDefault(emptyList())
        } catch (_: Exception) {
            // keep graceful
        } finally {
            isLoading = false
        }
    }

    NoiseBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "造物 · 心愿 · 山川",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 22.sp,
                    letterSpacing = 1.sp,
                    color = colors.inkPrimary
                )
                Text(
                    text = "生命之流的二级投影 · 真实心迹与行迹",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 11.sp,
                    color = colors.inkMuted
                )
            }

            // Tab 切换器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.archiveSubtle)
                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(16.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val tabs = listOf("造物 (${projects.size})", "心愿 (${wishes.size})", "山川 (${footprints.size})")
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) colors.archivePaper else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable {
                                haptics.tapKey()
                                selectedTab = index
                            }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = title,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) colors.inkPrimary else colors.inkSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                }
            } else {
                when (selectedTab) {
                    0 -> ProjectsList(projects = projects, modifier = Modifier.weight(1f))
                    1 -> WishesList(wishes = wishes, modifier = Modifier.weight(1f))
                    2 -> FootprintsList(footprints = footprints, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProjectsList(projects: List<Project>, modifier: Modifier = Modifier) {
    val colors = LooseLeafTheme.colors

    if (projects.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text("暂无造物记录", fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(projects, key = { it.id }) { proj ->
            PaperCard {
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
                            color = colors.inkPrimary
                        )
                        PaperBadge(text = proj.status ?: "进行中")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Code, contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = proj.githubUrl,
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
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
private fun WishesList(wishes: List<Wish>, modifier: Modifier = Modifier) {
    val colors = LooseLeafTheme.colors

    if (wishes.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text("暂无心愿记录", fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(wishes, key = { it.id }) { wish ->
            val isFulfilled = wish.status == "fulfilled"
            PaperCard {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFulfilled) Icons.Outlined.CheckCircle else Icons.Outlined.Lightbulb,
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
                                color = colors.inkPrimary
                            )
                        }
                        PaperBadge(text = if (isFulfilled) "已圆满" else "向往中")
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
                            text = "预算: ${FormatUtils.centsToYuanString(wish.budget)} · 实际: ${FormatUtils.centsToYuanString(wish.actualCost)}",
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

@Composable
private fun FootprintsList(footprints: List<Footprint>, modifier: Modifier = Modifier) {
    val colors = LooseLeafTheme.colors

    if (footprints.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Text("暂无山川足迹记录", fontFamily = ArchiveFontFamilies.EditorialSerif, color = colors.inkMuted)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(footprints, key = { it.id }) { foot ->
            PaperCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Explore, contentDescription = null, tint = colors.accentWarm, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = foot.cityName,
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = colors.inkPrimary
                            )
                        }
                        if (!foot.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = foot.notes,
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    Text(
                        text = foot.cityCode,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                }
            }
        }
    }
}
