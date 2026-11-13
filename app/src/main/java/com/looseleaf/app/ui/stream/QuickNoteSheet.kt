package com.looseleaf.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import com.looseleaf.app.darkroom.ExtractedExif
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
import androidx.compose.ui.res.painterResource
/**
 * 已附加照片的**轻量元数据**。
 *
 * MOB-P1-33：原先这里直接持有 `ProcessedImageResult`，
 * 而它内部带着 `compressedBytes`（压缩后的整张 JPEG 字节）——
 * 于是同一份照片在堆里被存了两遍（文件 + 字节数组），
 * 一次装裱 9 张就是 ~4.5MB 常驻，**抽屉关闭才释放**。
 * 现在只保留真正需要的元数据（EXIF / 尺寸 / MIME），字节交给文件流处理。
 */
internal data class AttachedPhotoMeta(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val exif: ExtractedExif
)

internal data class AttachedPhotoItem(
    val previewUri: String,
    val processed: AttachedPhotoMeta,
    val savedFile: File
)

@Serializable
internal data class AttachedPhotoSaveModel(
    val previewUri: String,
    val filePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val exif: com.looseleaf.app.darkroom.ExtractedExif
)

internal fun AttachedPhotoItem.toSaveModel() = AttachedPhotoSaveModel(
    previewUri = previewUri,
    filePath = savedFile.absolutePath,
    mimeType = processed.mimeType,
    width = processed.width,
    height = processed.height,
    exif = processed.exif
)

internal fun AttachedPhotoSaveModel.toItem() = AttachedPhotoItem(
    previewUri = previewUri,
    processed = AttachedPhotoMeta(
        mimeType = mimeType,
        width = width,
        height = height,
        exif = exif
    ),
    savedFile = File(filePath)
)

internal val attachedPhotosSaver = androidx.compose.runtime.saveable.Saver<MutableState<List<AttachedPhotoItem>>, String>(
    save = { state ->
        val models = state.value.map { it.toSaveModel() }
        Json.encodeToString(models)
    },
    restore = { jsonStr ->
        val models = runCatching { Json.decodeFromString<List<AttachedPhotoSaveModel>>(jsonStr) }.getOrDefault(emptyList())
        mutableStateOf(models.filter { File(it.filePath).exists() }.map { it.toItem() })
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickNoteSheet(
    onDismiss: () -> Unit,
    syncManager: SyncManager,
    apiClient: APIClient,
    onSuccess: () -> Unit
) {
    QuickNoteSheetBody(onDismiss, syncManager, apiClient, onSuccess)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteSheetBody(
    onDismiss: () -> Unit,
    syncManager: SyncManager,
    apiClient: APIClient,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LooseLeafTheme.colors

    val projects by syncManager.projectsFlow.collectAsStateWithLifecycle()
    val footprints by syncManager.footprintsFlow.collectAsStateWithLifecycle()

    // 草稿防丢（AOPT-07b）：标题/正文/类型/属地绑定到 SharedPreferences，
    // 意外下拉关闭或按返回键后重新打开自动恢复；提交成功才清空
    val draftPrefs = remember {
        context.getSharedPreferences("quick_note_draft", android.content.Context.MODE_PRIVATE)
    }

    var noteType by remember {
        val saved = draftPrefs.getString("type", "life") ?: "life"
        mutableStateOf(if (saved in setOf("life", "travel", "project")) saved else "life")
    } // "life", "travel", "project"

    // 日常与通用手记字段
    var noteTitle by remember { mutableStateOf(draftPrefs.getString("title", "").orEmpty()) }
    var noteContent by remember { mutableStateOf(draftPrefs.getString("content", "").orEmpty()) }
    var noteCityCode by remember { mutableStateOf(draftPrefs.getString("city_code", null)) }
    var noteCityName by remember { mutableStateOf(draftPrefs.getString("city_name", null)) }
    var noteLocation by remember { mutableStateOf(draftPrefs.getString("location", "").orEmpty()) }
    var isPrivate by remember { mutableStateOf(false) }
    var isLocating by remember { mutableStateOf(false) }
    // 草稿同样覆盖「发生时刻」（epoch millis）：原先不落草稿，
    // 意外关闭后重开时正文/属地都恢复、唯独时刻被静默重置为现在。
    var noteOccurredAt by remember {
        mutableStateOf(
            draftPrefs.getString("occurred_at_millis", null)?.toLongOrNull()
                ?.let { millis ->
                    java.time.Instant.ofEpochMilli(millis)
                        .atZone(FormatUtils.BeijingZone)
                        .toLocalDateTime()
                }
                ?: LocalDateTime.now(FormatUtils.BeijingZone)
        )
    }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(noteType, noteTitle, noteContent, noteCityCode, noteCityName, noteLocation, noteOccurredAt) {
        delay(300)
        draftPrefs.edit()
            .putString("type", noteType)
            .putString("title", noteTitle)
            .putString("content", noteContent)
            .putString("city_code", noteCityCode)
            .putString("city_name", noteCityName)
            .putString("location", noteLocation)
            .putString(
                "occurred_at_millis",
                noteOccurredAt.atZone(FormatUtils.BeijingZone).toInstant().toEpochMilli().toString()
            )
            .apply()
    }

    // 游历专属字段
    var selectedFootprintId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 造物专属字段
    var selectedProjectId by rememberSaveable { mutableStateOf<Long?>(null) }

    // 照片装裱状态 (P1-9)：使用 Saver 保护相纸，旋转屏幕不丢失相纸
    var attachedPhotos by rememberSaveable(saver = attachedPhotosSaver) { mutableStateOf<List<AttachedPhotoItem>>(emptyList()) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    // 未提交就关闭速记抽屉：清掉已落盘的压缩图，避免 outbox_media 无限堆积孤儿文件
    // （DarkroomScreen 有同样的 DisposableEffect 清理，这里此前漏了；屏幕旋转等配置变更时不应误删）
    var noteSubmitted by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            val isChangingConfig = (context as? android.app.Activity)?.isChangingConfigurations == true
            if (!noteSubmitted && !isChangingConfig) {
                attachedPhotos.forEach { photo ->
                    runCatching { photo.savedFile.delete() }
                }
            }
        }
    }

    LaunchedEffect(projects) {
        if (selectedProjectId == null && projects.isNotEmpty()) {
            selectedProjectId = projects.first().id
        }
    }

    // 定位获取与逆地理反查
    val fetchCurrentLocation: () -> Unit = {
        scope.launch {
            isLocating = true
            val res = LocationHelper.getCurrentLocation(context)
            if (res != null) {
                if (noteCityCode.isNullOrBlank() && !res.cityCode.isNullOrBlank()) {
                    noteCityCode = res.cityCode
                    noteCityName = res.cityName
                }
                if (noteLocation.isBlank() && res.locationName.isNotBlank()) {
                    noteLocation = res.locationName
                }
            }
            isLocating = false
        }
    }

    // EV-07：抽屉不再自动弹定位权限框；定位一律由用户点「使用当前定位」触发。
    // 权限被永久拒绝（勾选「不再询问」）时给出 Toast 并把入口切成「去设置」。
    var locationPermanentlyDenied by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            locationPermanentlyDenied = false
            fetchCurrentLocation()
        } else {
            val activity = context as? android.app.Activity
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION
                )
            if (permanentlyDenied) {
                locationPermanentlyDenied = true
                ArchiveToastManager.show("定位权限已被永久拒绝，请到系统设置开启", isError = true)
            }
        }
    }

    // 相册加选
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            // 满额时给明确反馈，而不是静默丢掉多选的图
            if (attachedPhotos.size >= 9) {
                ArchiveToastManager.show("相纸已满 9 张，请先移除后再装入", isError = true)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isProcessingPhoto = true
                try {
                    val remainingSlots = 9 - attachedPhotos.size
                    val targetUris = uris.take(remainingSlots)
                    val newItems = withContext(Dispatchers.IO) {
                        targetUris.map { uri ->
                            async {
                                val res = ImagePipeline.processImage(context, uri)
                                val saved = syncManager.saveCompressedImageToOutboxDir(res.compressedBytes)
                                AttachedPhotoItem(
                                    previewUri = "file://${saved.absolutePath}",
                                    processed = AttachedPhotoMeta(
                                        mimeType = res.mimeType,
                                        width = res.width,
                                        height = res.height,
                                        exif = res.exif
                                    ),
                                    savedFile = saved
                                )
                            }
                        }.awaitAll()
                    }
                    if (newItems.isNotEmpty()) {
                        attachedPhotos = (attachedPhotos + newItems).take(9)
                        val first = newItems.first()
                        if (noteTitle.isBlank()) {
                            noteTitle = "现场记录 · " + (first.processed.exif.model ?: "相纸影像")
                        }
                        if (noteCityCode.isNullOrBlank()) {
                            val code = first.processed.exif.inferredCityCode
                            val name = first.processed.exif.inferredCityName ?: code?.let { CityDictionary.cityNameForCode(it) }
                            if (!code.isNullOrBlank()) {
                                noteCityCode = code
                                noteCityName = name
                            }
                        }
                        if (noteLocation.isBlank()) {
                            val city = first.processed.exif.inferredCityName ?: first.processed.exif.inferredCityCode?.let { CityDictionary.cityNameForCode(it) }
                            if (!city.isNullOrBlank()) {
                                noteLocation = city
                            }
                        }
                        first.processed.exif.dateTimeOriginal?.let { raw ->
                            runCatching { noteOccurredAt = FormatUtils.parseToLocalDateTime(raw) }
                        }
                        ArchiveToastManager.show("已装裱 ${newItems.size} 张相纸")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ArchiveToastManager.show("相纸装裱失败: ${e.message}", isError = true)
                } finally {
                    isProcessingPhoto = false
                }
            }
        }
    }

    val canSave = !isProcessingPhoto && !isSubmitting && when (noteType) {
        "life" -> (noteTitle.isNotBlank() || noteContent.isNotBlank() || attachedPhotos.isNotEmpty())
        "travel" -> noteContent.isNotBlank() && (!noteCityCode.isNullOrBlank() || !noteCityName.isNullOrBlank() || noteLocation.isNotBlank())
        "project" -> selectedProjectId != null && noteContent.isNotBlank()
        else -> false
    }

    val headerTitle = when (noteType) {
        "life" -> "新散页"
        "travel" -> "游历手记"
        "project" -> "造物手记"
        else -> "新散页"
    }

    val submitText = if (isSubmitting) "提交中…" else "归册"

    val onSubmitAction: () -> Unit = onSubmitAction@{
        if (isSubmitting) return@onSubmitAction
        isSubmitting = true
        scope.launch {
            try {
                val finalCode = noteCityCode ?: CityDictionary.codeForLocation(noteLocation)
                val finalCityName = noteCityName ?: finalCode?.let { CityDictionary.cityNameForCode(it) }

                when (noteType) {
                    "life" -> {
                        val effectiveTitle = noteTitle.ifBlank {
                            if (attachedPhotos.isNotEmpty()) "现场快门 · " + (attachedPhotos.first().processed.exif.model ?: "胶片相纸")
                            else "无题随笔"
                        }
                        if (attachedPhotos.isNotEmpty()) {
                            val primary = attachedPhotos.first()
                            val extraFiles = attachedPhotos.drop(1).map {
                                OfflineMediaFileItem(
                                    filePath = it.savedFile.absolutePath,
                                    mimeType = it.processed.mimeType,
                                    width = it.processed.width,
                                    height = it.processed.height
                                )
                            }
                            val req = CreateEntryRequest(
                                type = "life",
                                title = effectiveTitle,
                                content = noteContent.trim(),
                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                cityCode = finalCode,
                                cityName = finalCityName,
                                isPrivate = isPrivate,
                            )
                            syncManager.createMediaEntryOffline(
                                localImageFilePath = primary.savedFile.absolutePath,
                                mimeType = primary.processed.mimeType,
                                width = primary.processed.width,
                                height = primary.processed.height,
                                req = req,
                                additionalMediaFiles = extraFiles
                            )
                            ArchiveToastManager.show("图文散页已装裱归册")
                        } else {
                            syncManager.createEntryOptimistic(
                                type = "life",
                                title = effectiveTitle,
                                content = noteContent.trim(),
                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                cityCode = finalCode,
                                cityName = finalCityName,
                                isPrivate = isPrivate,
                            )
                            ArchiveToastManager.show("随笔已归册")
                        }
                    }

                    "travel" -> {
                        val effectiveTitle = noteTitle.ifBlank {
                            val dest = finalCityName ?: "远方"
                            "游历 · $dest"
                        }
                        if (attachedPhotos.isNotEmpty()) {
                            val primary = attachedPhotos.first()
                            val extraFiles = attachedPhotos.drop(1).map {
                                OfflineMediaFileItem(
                                    filePath = it.savedFile.absolutePath,
                                    mimeType = it.processed.mimeType,
                                    width = it.processed.width,
                                    height = it.processed.height
                                )
                            }
                            val req = CreateEntryRequest(
                                type = "travel",
                                title = effectiveTitle,
                                content = noteContent.trim(),
                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                cityCode = finalCode,
                                cityName = finalCityName,
                                footprintId = selectedFootprintId,
                                isPrivate = isPrivate
                            )
                            syncManager.createMediaEntryOffline(
                                localImageFilePath = primary.savedFile.absolutePath,
                                mimeType = primary.processed.mimeType,
                                width = primary.processed.width,
                                height = primary.processed.height,
                                req = req,
                                additionalMediaFiles = extraFiles
                            )
                            ArchiveToastManager.show("游历图文手记已归册")
                        } else {
                            syncManager.createEntryOptimistic(
                                type = "travel",
                                title = effectiveTitle,
                                content = noteContent.trim(),
                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                cityCode = finalCode,
                                cityName = finalCityName,
                                footprintId = selectedFootprintId,
                                isPrivate = isPrivate
                            )
                            ArchiveToastManager.show("游历手记已归册")
                        }
                    }

                    "project" -> {
                        val effectiveTitle = noteTitle.ifBlank {
                            val proj = projects.firstOrNull { it.id == selectedProjectId }
                            if (proj != null) "${proj.title} · 进展记录" else "造物手记"
                        }
                        if (attachedPhotos.isNotEmpty()) {
                            val primary = attachedPhotos.first()
                            val extraFiles = attachedPhotos.drop(1).map {
                                OfflineMediaFileItem(
                                    filePath = it.savedFile.absolutePath,
                                    mimeType = it.processed.mimeType,
                                    width = it.processed.width,
                                    height = it.processed.height
                                )
                            }
                            val req = CreateEntryRequest(
                                type = "project",
                                title = effectiveTitle,
                                content = noteContent.trim(),
                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                cityCode = finalCode,
                                cityName = finalCityName,
                                projectId = selectedProjectId,
                                isPrivate = isPrivate
                            )
                            syncManager.createMediaEntryOffline(
                                localImageFilePath = primary.savedFile.absolutePath,
                                mimeType = primary.processed.mimeType,
                                width = primary.processed.width,
                                height = primary.processed.height,
                                req = req,
                                additionalMediaFiles = extraFiles
                            )
                            ArchiveToastManager.show("造物进展手记已装裱归册")
                        } else {
                            syncManager.createEntryOptimistic(
                                type = "project",
                                title = effectiveTitle,
                                content = noteContent.trim(),
                                occurredAt = FormatUtils.toBeijingIsoString(noteOccurredAt),
                                location = noteLocation.trim().takeIf { it.isNotBlank() },
                                cityCode = finalCode,
                                cityName = finalCityName,
                                projectId = selectedProjectId,
                                isPrivate = isPrivate
                            )
                            ArchiveToastManager.show("造物进展已归册")
                        }
                    }
                }
                noteSubmitted = true
                draftPrefs.edit().clear().apply()
                onSuccess()
            } catch (e: CancellationException) {
                isSubmitting = false
                throw e
            } catch (e: Exception) {
                isSubmitting = false
                ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
            } finally {
                if (!noteSubmitted) {
                    isSubmitting = false
                }
            }
        }
    }

    ArchiveSheet(
        // 提交中禁止下滑/点遮罩/取消关闭：组合销毁会取消 rememberCoroutineScope 里的落库协程，
        // 相纸已清理但记录未入库，界面却毫无提示（EV-20）。
        onDismissRequest = onDismiss,
        dismissEnabled = { !isSubmitting },
        title = headerTitle,
        cancelText = stringResource(R.string.quick_note_cancel),
        actionText = submitText,
        onAction = onSubmitAction,
        isActionEnabled = canSave
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // 四种生活形态可选胶囊切换（配低饱和分类色点）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categoryOptions = listOf(
                    Triple("life", "日常", colors.tagDaily),
                    Triple("travel", "游历", colors.tagTravel),
                    Triple("project", "造物", colors.tagProject)
                )
                categoryOptions.forEach { (k, label, dotColor) ->
                    ArchiveSelectableCapsule(
                        text = label,
                        selected = noteType == k,
                        onClick = { noteType = k },
                        leadingDotColor = dotColor
                    )
                }
                ArchiveSelectableCapsule(
                    text = if (isPrivate) "仅自己可见" else "随展厅公开",
                    selected = isPrivate,
                    onClick = { isPrivate = !isPrivate },
                    leadingDotColor = if (isPrivate) colors.accentWarm else colors.inkMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 相纸装裱槽位辅助 Composable
            @Composable
            fun PhotoMountingSection() {
                if (attachedPhotos.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.quick_note_sheet_text_tpl_01, attachedPhotos.size),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                        if (attachedPhotos.size < 9) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        galleryLauncher.launch(
                                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_photo_library),
                                        contentDescription = "装裱相纸",
                                        // 与右侧「装裱相纸」文字同组同色
                                        tint = colors.accentSmallText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(stringResource(R.string.quick_note_mount_paper), fontSize = 12.sp, color = colors.accentSmallText)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        attachedPhotos.forEachIndexed { idx, photo ->
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.archiveSubtle)
                                    .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            ) {
                                AsyncImage(
                                    model = photo.previewUri,
                                    contentDescription = stringResource(R.string.quick_note_sheet_contentDescription_01),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.inkPrimary.copy(alpha = 0.65f))
                                        .clickable {
                                            // 移除即删盘上的临时相纸：否则它既不会被提交
                                            //（已从列表摘除）也不会被关闭清理（仍被列表外的
                                            // 无人引用），成为 outbox_media 里的永久孤儿文件。
                                            runCatching { photo.savedFile.delete() }
                                            attachedPhotos = attachedPhotos.filterIndexed { i, _ -> i != idx }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.quick_note_sheet_contentDescription_02),
                                        tint = colors.archivePaper,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(3.dp)
                                        .background(colors.inkPrimary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.quick_note_sheet_text_tpl_02, idx + 1),
                                        fontFamily = ArchiveFontFamilies.MonoScale,
                                        fontSize = 11.sp,
                                        color = colors.archivePaper
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.archiveSubtle.copy(alpha = 0.45f))
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isProcessingPhoto) "相纸显影处理中…" else "装裱现场相纸 (可选 9 张)",
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 12.sp,
                                color = colors.inkMuted
                            )
                            if (isProcessingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = colors.accentWarm,
                                    strokeWidth = 1.5.dp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.archivePaper,
                                contentColor = colors.inkPrimary
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(colors.borderDivider)),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_photo_library),
                                contentDescription = "相册装裱",
                                tint = colors.accentWarm,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.quick_note_album_mount), fontSize = 12.sp, fontFamily = ArchiveFontFamilies.BodySans)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }
            }

            // ==================== 1. 日常 (life) ====================
            if (noteType == "life") {
                androidx.compose.foundation.text.BasicTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteTitle.isEmpty()) {
                            Text(
                                text = stringResource(R.string.quick_note_sheet_text_01),
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.inkMuted.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.foundation.text.BasicTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteContent.isEmpty()) {
                            Text(
                                text = stringResource(R.string.quick_note_sheet_text_02),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.inkMuted.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                PhotoMountingSection()

                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.quick_note_sheet_text_03),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                PaperDateTimeSplitRow(
                    selectedDateTime = noteOccurredAt,
                    onDateTimeSelected = { noteOccurredAt = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.quick_note_sheet_text_04),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                PaperCityPickerField(
                    selectedCityCode = noteCityCode,
                    selectedCityName = noteCityName,
                    onCitySelected = { code, name ->
                        noteCityCode = code
                        noteCityName = name
                    },
                    placeholder = if (isLocating) "正在定位…" else "选择城市…"
                )

                Spacer(modifier = Modifier.height(8.dp))

                PaperLocationField(
                    value = noteLocation,
                    onValueChange = { noteLocation = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

            }

            // ==================== 2. 游历 (travel) ====================
            else if (noteType == "travel") {
                // 游历专属顶置卡片：到访城市必选高亮 + 关联足迹
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.archiveSubtle.copy(alpha = 0.5f))
                        .border(0.8.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.quick_note_sheet_text_06),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentSmallText
                        )

                        PaperCityPickerField(
                            selectedCityCode = noteCityCode,
                            selectedCityName = noteCityName,
                            onCitySelected = { code, name ->
                                noteCityCode = code
                                noteCityName = name
                            },
                            placeholder = if (isLocating) "正在定位到访城市…" else "选择到访城市 (必选) *"
                        )

                        // EV-07：定位改为显式点击触发；永久拒绝时按钮变「去设置」直达应用详情页，
                        // 并常驻一行说明，避免 canSave 因缺城市永久灰着却无任何提示。
                        ArchiveTextButton(
                            text = when {
                                isLocating -> "正在定位…"
                                locationPermanentlyDenied -> "去设置"
                                else -> "使用当前定位"
                            },
                            enabled = !isLocating,
                            onClick = {
                                when {
                                    locationPermanentlyDenied -> context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                    LocationHelper.hasLocationPermission(context) -> fetchCurrentLocation()
                                    else -> locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        )
                        Text(
                            text = "可手动选择到访城市，或点「使用当前定位」自动回填",
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )

                        PaperLocationField(
                            value = noteLocation,
                            onValueChange = { noteLocation = it },
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_01)
                        )

                        if (footprints.isNotEmpty()) {
                            val footprintOptions = listOf(null to "-- 自动聚合至同城近期旅程 / 开启新旅程 --") +
                                footprints.map { fp ->
                                    fp.id to (fp.title?.ifBlank { "${fp.cityName}之行" } ?: "${fp.cityName}之行")
                                }
                            PaperSelectField(
                                label = stringResource(R.string.quick_note_sheet_label_01),
                                selectedValue = selectedFootprintId,
                                options = footprintOptions,
                                onSelected = { selectedFootprintId = it },
                                placeholder = stringResource(R.string.quick_note_sheet_placeholder_02)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                androidx.compose.foundation.text.BasicTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteTitle.isEmpty()) {
                            Text(
                                text = stringResource(R.string.quick_note_sheet_text_07),
                                fontFamily = ArchiveFontFamilies.EditorialSerif,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.inkMuted.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                androidx.compose.foundation.text.BasicTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    textStyle = TextStyle(
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = colors.inkPrimary
                    ),
                    cursorBrush = SolidColor(colors.accentWarm),
                    decorationBox = { innerTextField ->
                        if (noteContent.isEmpty()) {
                            Text(
                                text = stringResource(R.string.quick_note_sheet_text_08),
                                fontFamily = ArchiveFontFamilies.BodySans,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = colors.inkMuted.copy(alpha = 0.45f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                PhotoMountingSection()

                HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.projects_wishes_sheets_label_04),
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                PaperDateTimeSplitRow(
                    selectedDateTime = noteOccurredAt,
                    onDateTimeSelected = { noteOccurredAt = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

            }

            // ==================== 3. 造物进展 (project) ====================
            else if (noteType == "project") {
                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.archiveSubtle)
                            .border(0.5.dp, colors.borderDivider, RoundedCornerShape(12.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无造物项目，请前往底部「工坊」页点击右上角「+ 立项」创建新造物后再写进展。",
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                    }
                } else {
                    PaperSelectField(
                        label = stringResource(R.string.quick_note_sheet_label_04),
                        selectedValue = selectedProjectId,
                        options = projects.map { it.id to "${it.title} (${it.status ?: "活跃"})" },
                        onSelected = { selectedProjectId = it }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkPrimary
                        ),
                        cursorBrush = SolidColor(colors.accentWarm),
                        decorationBox = { innerTextField ->
                            if (noteTitle.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.quick_note_sheet_text_11),
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = colors.inkMuted.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp),
                        textStyle = TextStyle(
                            fontFamily = ArchiveFontFamilies.BodySans,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            color = colors.inkPrimary
                        ),
                        cursorBrush = SolidColor(colors.accentWarm),
                        decorationBox = { innerTextField ->
                            if (noteContent.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.quick_note_sheet_text_12),
                                    fontFamily = ArchiveFontFamilies.BodySans,
                                    fontSize = 15.sp,
                                    lineHeight = 24.sp,
                                    color = colors.inkMuted.copy(alpha = 0.45f)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    PhotoMountingSection()

                    HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.quick_note_sheet_text_13),
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    PaperDateTimeSplitRow(
                        selectedDateTime = noteOccurredAt,
                        onDateTimeSelected = { noteOccurredAt = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
