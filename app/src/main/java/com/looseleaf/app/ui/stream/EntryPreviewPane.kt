package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.looseleaf.app.R
import com.looseleaf.app.model.LifeEntry
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.utils.FormatUtils

/**
 * MOB-P2-40：宽屏（平板 / 折叠屏展开态）下的「详情侧栏」。
 *
 * 窄屏时记录详情走底部弹窗（`EntryDetailSheetDialog`）；但在平板/折叠屏展开态，
 * 弹窗会盖住整块屏幕，而两侧又是大片空白 —— 这里把详情**内嵌**成右栏：
 * 左边仍是可滚动的时间流，右边随手展示选中记录的全文与相纸。
 *
 * 只做**只读**呈现：修订与删除仍复用原有的确认弹窗，避免两套编辑逻辑分叉。
 */
private val PaneDateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

@Composable
internal fun EntryPreviewPane(
    entry: LifeEntry?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.archivePaper.copy(alpha = 0.55f))
            .border(0.5.dp, colors.borderDivider.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "从左侧选一条记录\n这里会摊开它的全文",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        // 标题行 + 操作
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { "无题随笔" },
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp,
                    lineHeight = 26.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = FormatUtils.parseToLocalDateTime(entry.occurredAt)
                        .format(PaneDateFormatter),
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "修订这条记录",
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除这条记录",
                    tint = colors.accentWarm,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // 地点
        val place = entry.cityName?.takeIf { it.isNotBlank() }
            ?: entry.location?.takeIf { it.isNotBlank() }
        if (place != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = place,
                    fontFamily = ArchiveFontFamilies.BodySans,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 正文 + 相纸
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (entry.content.isNotBlank()) {
                Text(
                    text = entry.content,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 14.5.sp,
                    lineHeight = 25.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (entry.attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.attachments.forEach { att ->
                        AsyncImage(
                            model = att.url,
                            contentDescription = att.originalFilename,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPhotoClick(att.url, att.mimeType) }
                        )
                    }
                }
            }
        }
    }
}
