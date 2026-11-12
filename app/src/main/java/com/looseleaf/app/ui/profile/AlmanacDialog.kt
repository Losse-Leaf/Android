package com.looseleaf.app.ui.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.AlmanacSummaryResponse
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import com.looseleaf.app.ui.components.ArchiveConfirmDialog
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource

@Composable
internal fun AlmanacDialog(
    apiClient: APIClient,
    // MOB-P1-32：原先传入全量档案只为算「最早年份」—— 分页化后不再有全量列表，
    // 改为直接传入最早年份（由 SQL DISTINCT 查询得到）。
    earliestEntryYear: Int?,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    val currentYear = remember { LocalDate.now(FormatUtils.BeijingZone).year }
    val earliestYear = remember(earliestEntryYear, currentYear) {
        if (earliestEntryYear != null) minOf(earliestEntryYear, currentYear) else currentYear
    }
    val availableYears = remember(earliestYear, currentYear) {
        (earliestYear..currentYear).toList().reversed()
    }
    var selectedYear by remember { mutableStateOf(currentYear) }
    var summary by remember { mutableStateOf<AlmanacSummaryResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedYear) {
        isLoading = true
        errorMsg = null
        try {
            summary = apiClient.fetchAlmanacSummary(selectedYear)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMsg = e.message
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(4.dp),
            color = colors.archivePaper,
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_tpl_02, selectedYear),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_05),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.paper_components_contentDescription_02), tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 年份切换胶囊 (从刚进入的年份至当前年份，支持横滑)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (yr in availableYears) {
                        val isSelected = yr == selectedYear
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) colors.accentWarm.copy(alpha = 0.12f) else colors.archiveCard)
                                .border(1.dp, if (isSelected) colors.accentWarm else colors.borderSubtle, RoundedCornerShape(4.dp))
                                .clickable { selectedYear = yr }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_03, yr),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) colors.accentWarm else colors.inkPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                ArchiveDivider()
                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        CircularProgressIndicator(color = colors.accentWarm, modifier = Modifier.size(24.dp))
                    }
                } else if (errorMsg != null) {
                    Text(
                        text = stringResource(R.string.stream_dialogs_text_tpl_04, errorMsg.orEmpty()),
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 13.sp,
                        color = colors.accentWarm
                    )
                } else if (summary != null) {
                    val data = summary ?: AlmanacSummaryResponse()

                    // 第一幕：岁月卷首
                    AlmanacChapterSection(
                        chapterNumber = "第一幕",
                        title = stringResource(R.string.stream_dialogs_title_01)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_tpl_05, data.prologue.totalEntries, data.prologue.totalWords, data.prologue.activeDays),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colors.inkSecondary
                        )
                        if (data.prologue.firstDate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_06, data.prologue.firstDate, data.prologue.lastDate),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    ArchiveDivider()

                    // 第二幕：足迹
                    AlmanacChapterSection(
                        chapterNumber = "第二幕",
                        title = stringResource(R.string.stream_dialogs_title_tpl_01, data.atlas.totalCities)
                    ) {
                        if (data.atlas.cities.isEmpty()) {
                            Text(stringResource(R.string.stream_dialogs_empty_footprints), fontSize = 12.sp, color = colors.inkMuted)
                        } else {
                            Text(
                                text = data.atlas.cities.joinToString(" · ") { it.cityName },
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }

                    ArchiveDivider()

                    // 第三幕：心愿圆满
                    AlmanacChapterSection(
                        chapterNumber = "第三幕",
                        title = stringResource(R.string.stream_dialogs_title_02)
                    ) {
                        Text(
                            text = stringResource(R.string.stream_dialogs_text_tpl_07, data.wishes.fulfilledCount, data.wishes.wantingCount),
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                        if (data.wishes.totalActual > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_08, FormatUtils.centsToYuanString(data.wishes.totalActual)),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                    }

                    ArchiveDivider()

                    // 第四幕：浮光掠影
                    AlmanacChapterSection(
                        chapterNumber = "第四幕",
                        title = stringResource(R.string.stream_dialogs_title_tpl_02, data.highlights.photos.size)
                    ) {
                        val seasonItems = listOfNotNull(
                            data.highlights.spring?.let { "春 · ${it.title}" },
                            data.highlights.summer?.let { "夏 · ${it.title}" },
                            data.highlights.autumn?.let { "秋 · ${it.title}" },
                            data.highlights.winter?.let { "冬 · ${it.title}" }
                        )
                        if (seasonItems.isEmpty()) {
                            Text(stringResource(R.string.stream_dialogs_empty_footprints_desc), fontSize = 12.sp, color = colors.inkMuted)
                        } else {
                            seasonItems.forEach { line ->
                                Text(
                                    text = line,
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.inkPrimary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    ArchiveDivider()

                    // 第五幕：柴米油盐
                    AlmanacChapterSection(
                        chapterNumber = "第五幕",
                        title = stringResource(R.string.stream_dialogs_title_03)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_09, FormatUtils.centsToYuanString(data.finances.totalExpense)),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = stringResource(R.string.stream_dialogs_text_tpl_10, FormatUtils.centsToYuanString(data.finances.totalIncome)),
                                fontFamily = ArchiveFontFamilies.MonoScale,
                                fontSize = 12.sp,
                                color = colors.accentMoss
                            )
                        }
                        if (data.finances.categories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            data.finances.categories.take(4).forEach { cat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(cat.name, fontSize = 12.sp, color = colors.inkSecondary)
                                    Text(
                                        FormatUtils.centsToYuanString(cat.amount),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 12.sp,
                                        color = colors.inkPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlmanacChapterSection(
    chapterNumber: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LooseLeafTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chapterNumber,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 12.sp,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.inkPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
