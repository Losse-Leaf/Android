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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
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
import android.net.Uri
import com.looseleaf.app.darkroom.ImagePipeline
import com.looseleaf.app.darkroom.ProcessedImageResult
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.OfflineMediaFileItem
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.CreateEntryRequest
import com.looseleaf.app.model.CreateProjectRequest
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Footprint
import com.looseleaf.app.model.Project
import com.looseleaf.app.model.Wish
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.utils.FormatUtils
import com.looseleaf.app.utils.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickNoteSheet(
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
        mutableStateOf(draftPrefs.getString("type", "life") ?: "life")
    } // "life", "travel", "project", "wish"

    // 日常与通用手记字段
    var noteTitle by remember { mutableStateOf(draftPrefs.getString("title", "").orEmpty()) }
    var noteContent by remember { mutableStateOf(draftPrefs.getString("content", "").orEmpty()) }
    var noteCityCode by remember { mutableStateOf(draftPrefs.getString("city_code", null)) }
    var noteCityName by remember { mutableStateOf(draftPrefs.getString("city_name", null)) }
    var noteLocation by remember { mutableStateOf(draftPrefs.getString("location", "").orEmpty()) }
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
    var selectedFootprintId by remember { mutableStateOf<Long?>(null) }

    // 造物专属字段。
    // MOB 表单审计 #17：状态统一中文四态（进行中/已完成/维护中/已搁置），
    // 与新建弹窗/编辑抽屉同一套枚举（后端白名单中英全集放行，此处收敛为中文）。
    var projectMode by remember { mutableStateOf("entry") } // "entry" (进展手记) vs "new" (立项新造物)
    var selectedProjectId by remember { mutableStateOf<Long?>(null) }
    var projTitle by remember { mutableStateOf("") }
    var projSubtitle by remember { mutableStateOf("") }
    var projCategory by remember { mutableStateOf("软件") }
    var projStatus by remember { mutableStateOf("进行中") }
    var projDescription by remember { mutableStateOf("") }
    var projStory by remember { mutableStateOf("") }
    var projGithubUrl by remember { mutableStateOf("") }

    // 心愿专属字段
    var wishKind by remember { mutableStateOf("item") } // "item", "travel", "milestone"
    var wishTitle by remember { mutableStateOf("") }
    var wishBudgetYuan by remember { mutableStateOf("") }
    var wishSpec by remember { mutableStateOf("") }
    var wishTargetDate by remember { mutableStateOf<LocalDate?>(null) }
    var wishNote by remember { mutableStateOf("") }
    // MOB 表单审计 #13：心愿-旅程目的地用独立状态——
    // 不复用速记共享属地 noteCity*（GPS 定位/上一模式残留会被误预填为「远方旅程」目的地），
    // 提交也不再借道不可见的 noteLocation 做地名反推。
    var wishCityCode by remember { mutableStateOf("") }
    var wishCityName by remember { mutableStateOf("") }

    // 照片装裱状态
    var attachedPhotos by remember { mutableStateOf<List<AttachedPhotoItem>>(emptyList()) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    fun clearAttachedPhotos() {
        attachedPhotos.forEach { photo -> runCatching { photo.savedFile.delete() } }
        attachedPhotos = emptyList()
    }

    // 心愿模式不展示装裱区：从其它形态带过来的相纸不能继续被静默上传。
    LaunchedEffect(noteType) {
        if (noteType == "wish" && attachedPhotos.isNotEmpty()) {
            clearAttachedPhotos()
        }
    }

    // 未提交就关闭速记抽屉：清掉已落盘的压缩图，避免 outbox_media 无限堆积孤儿文件
    // （DarkroomScreen 有同样的 DisposableEffect 清理，这里此前漏了）
    var noteSubmitted by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (!noteSubmitted) {
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            fetchCurrentLocation()
        }
    }

    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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

    val canSave = when (noteType) {
        "life" -> (noteTitle.isNotBlank() || noteContent.isNotBlank() || attachedPhotos.isNotEmpty()) && !isSubmitting
        "travel" -> noteContent.isNotBlank() && (!noteCityCode.isNullOrBlank() || !noteCityName.isNullOrBlank() || noteLocation.isNotBlank()) && !isSubmitting
        "project" -> if (projectMode == "entry") {
            selectedProjectId != null && noteContent.isNotBlank() && !isSubmitting
        } else {
            projTitle.isNotBlank() && projDescription.isNotBlank() && !isSubmitting
        }
        "wish" -> wishTitle.isNotBlank() && !isSubmitting
        else -> false
    }

    val headerTitle = when (noteType) {
        "life" -> "《新散页》"
        "travel" -> "《游历手记》"
        "project" -> if (projectMode == "entry") "《造物手记》" else "《立项新造物》"
        "wish" -> "《封存心愿》"
        else -> "《散页》"
    }

    val submitText = if (isSubmitting) "归册中…" else when (noteType) {
        "life" -> "存入散页"
        "travel" -> "归册游记"
        "project" -> if (projectMode == "entry") "存入手记" else "确认立项"
        "wish" -> "封存心愿"
        else -> "归册"
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.archivePaper,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier.imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 顶栏：取消 / 标题 / 提交
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(stringResource(R.string.quick_note_cancel), color = colors.inkSecondary, fontSize = 14.sp)
                }

                Text(
                    text = headerTitle,
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = colors.inkPrimary
                )

                TextButton(
                    enabled = canSave,
                    onClick = {
                        scope.launch {
                            isSubmitting = true

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
                                                footprintId = selectedFootprintId
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
                                                footprintId = selectedFootprintId
                                            )
                                            ArchiveToastManager.show("游历手记已归册")
                                        }
                                    }

                                    "project" -> {
                                        if (projectMode == "entry") {
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
                                                    projectId = selectedProjectId
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
                                                    projectId = selectedProjectId
                                                )
                                                ArchiveToastManager.show("造物进展已归册")
                                            }
                                        } else {
                                            // 新建造物不展示装裱区：隐藏的相纸不能阻止提交，
                                            // 也不能在提交成功后留下孤儿文件。
                                            if (attachedPhotos.isNotEmpty()) {
                                                clearAttachedPhotos()
                                            }
                                            val req = CreateProjectRequest(
                                                title = projTitle.trim(),
                                                subtitle = projSubtitle.trim().takeIf { it.isNotBlank() },
                                                description = projDescription.trim(),
                                                story = projStory.trim().takeIf { it.isNotBlank() },
                                                category = projCategory.trim().takeIf { it.isNotBlank() },
                                                status = projStatus,
                                                githubUrl = projGithubUrl.trim().takeIf { it.isNotBlank() }
                                            )
                                            val queued = withContext(Dispatchers.IO) {
                                                val q = syncManager.createProjectOrQueue(req)
                                                // 拉对数据集：造物页读的是 cached_projects，
                                                // 只拉时间流（ENTRIES）会让新造物在本会话里
                                                // 一直不可见（造物页首拉门禁已过）。
                                                if (!q) syncManager.pullProjectsWishesData()
                                                q
                                            }
                                            ArchiveToastManager.show(
                                                if (queued) "当前离线，已排队；联网后自动立项"
                                                else "新造物「${projTitle.trim()}」已成功立项"
                                            )
                                        }
                                    }

                                    "wish" -> {
                                        val budgetCents = FormatUtils.yuanStringToCents(wishBudgetYuan)
                                        val targetIso = wishTargetDate?.atStartOfDay()?.let { FormatUtils.toBeijingIsoString(it) }
                                        // 心愿支持附件：先上传相纸再带着 attachment_ids 建档。
                                        // 原先生成的照片被直接丢弃（且提交成功后不再清理），既丢图又留孤儿文件。
                                        val attachmentIds = withContext(Dispatchers.IO) {
                                            attachedPhotos.map { photo ->
                                                ImagePipeline.uploadImage(
                                                    apiClient = apiClient,
                                                    file = photo.savedFile,
                                                    mimeType = photo.processed.mimeType,
                                                    filename = photo.savedFile.name,
                                                    width = photo.processed.width,
                                                    height = photo.processed.height
                                                ).id
                                            }
                                        }
                                        val req = CreateWishRequest(
                                            title = wishTitle.trim(),
                                            kind = wishKind,
                                            note = wishNote.trim().takeIf { it.isNotBlank() },
                                            spec = wishSpec.trim().takeIf { it.isNotBlank() && wishKind == "item" },
                                            // MOB 表单审计 #13：目的地取心愿专属状态，
                                            // 不再复用速记共享属地/地名反推
                                            cityCode = if (wishKind == "travel") wishCityCode.trim().takeIf { it.isNotBlank() } else null,
                                            cityName = if (wishKind == "travel") wishCityName.trim().takeIf { it.isNotBlank() } else null,
                                            budget = budgetCents,
                                            targetDate = targetIso,
                                            attachmentIds = attachmentIds.takeIf { it.isNotEmpty() }
                                        )
                                        val queued = withContext(Dispatchers.IO) {
                                            val q = syncManager.createWishOrQueue(req)
                                            // 同「立项新造物」：心愿页读的是 cached_wishes，
                                            // 必须拉 WISHES 数据集，否则新心愿本会话不可见。
                                            if (!q) syncManager.pullProjectsWishesData()
                                            q
                                        }
                                        ArchiveToastManager.show(
                                            if (queued) "当前离线，已排队；联网后自动封存"
                                            else "心愿「${wishTitle.trim()}」已封存入册"
                                        )
                                    }
                                }
                                // 提交成功：接管相纸文件所有权，关闭抽屉时不再清理；草稿同时清空
                                noteSubmitted = true
                                draftPrefs.edit().clear().apply()
                                onSuccess()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                ArchiveToastManager.show("归册失败: ${e.message}", isError = true)
                            } finally {
                                isSubmitting = false
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = submitText,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = if (canSave) colors.accentWarm else colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 四种生活形态胶囊切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("life" to "日常", "travel" to "游历", "project" to "造物", "wish" to "心愿").forEach { (k, label) ->
                    PaperFilterPill(
                        selected = noteType == k,
                        onClick = { noteType = k },
                        label = label
                    )
                }
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
                                        contentDescription = null,
                                        tint = colors.accentWarm,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(stringResource(R.string.quick_note_mount_paper), fontSize = 11.5.sp, color = colors.accentWarm)
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
                                fontSize = 11.5.sp,
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
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(34.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_photo_library),
                                contentDescription = null,
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
                        .heightIn(min = 90.dp, max = 220.dp),
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
                    fontSize = 11.5.sp,
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
                    fontSize = 11.5.sp,
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
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.archiveSubtle.copy(alpha = 0.5f))
                        .border(0.8.dp, colors.accentWarm.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.quick_note_sheet_text_06),
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.accentWarm
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
                        .heightIn(min = 90.dp, max = 220.dp),
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
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                PaperDateTimeSplitRow(
                    selectedDateTime = noteOccurredAt,
                    onDateTimeSelected = { noteOccurredAt = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

            }

            // ==================== 3. 造物 (project) ====================
            else if (noteType == "project") {
                // 子模式切换：撰写已有进展手记 vs 立项新造物
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperFilterPill(
                        selected = projectMode == "entry",
                        onClick = { projectMode = "entry" },
                        label = stringResource(R.string.quick_note_sheet_label_02)
                    )
                    PaperFilterPill(
                        selected = projectMode == "new",
                        onClick = { projectMode = "new" },
                        label = stringResource(R.string.quick_note_sheet_label_03)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (projectMode == "entry") {
                    if (projects.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.archiveSubtle)
                                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(6.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.quick_note_sheet_text_10),
                                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                                    fontSize = 13.sp,
                                    color = colors.inkSecondary
                                )
                                Button(
                                    onClick = { projectMode = "new" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentWarm,
                                        contentColor = colors.archivePaper
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.quick_note_create_project), fontSize = 12.sp)
                                }
                            }
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
                                .heightIn(min = 90.dp, max = 220.dp),
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
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PaperDateTimeSplitRow(
                            selectedDateTime = noteOccurredAt,
                            onDateTimeSelected = { noteOccurredAt = it }
                        )
                    }
                } else {
                    // 立项新造物表单
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PaperTextField(
                            value = projTitle,
                            onValueChange = { projTitle = it },
                            label = stringResource(R.string.quick_note_sheet_label_05),
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_03),
                            singleLine = true
                        )

                        PaperTextField(
                            value = projSubtitle,
                            onValueChange = { projSubtitle = it },
                            label = stringResource(R.string.quick_note_sheet_label_06),
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_04),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PaperSelectField(
                                label = stringResource(R.string.quick_note_sheet_label_07),
                                selectedValue = projCategory,
                                options = listOf(
                                    "软件" to "软件",
                                    "硬件" to "硬件",
                                    "设计" to "设计",
                                    "手作" to "手作",
                                    "写作" to "写作",
                                    "生活" to "生活"
                                ),
                                onSelected = { projCategory = it },
                                modifier = Modifier.weight(1f)
                            )

                            PaperSelectField(
                                label = stringResource(R.string.quick_note_sheet_label_08),
                                selectedValue = projStatus,
                                options = listOf(
                                    // MOB 表单审计 #17：统一中文四态（对齐新建弹窗与编辑抽屉）
                                    "进行中" to "进行中",
                                    "已完成" to "已完成",
                                    "维护中" to "维护中",
                                    "已搁置" to "已搁置"
                                ),
                                onSelected = { projStatus = it },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        PaperTextField(
                            value = projDescription,
                            onValueChange = { projDescription = it },
                            label = stringResource(R.string.quick_note_sheet_label_09),
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_05)
                        )

                        PaperTextField(
                            value = projStory,
                            onValueChange = { projStory = it },
                            label = stringResource(R.string.quick_note_sheet_label_10),
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_06)
                        )

                        PaperTextField(
                            value = projGithubUrl,
                            onValueChange = { projGithubUrl = it },
                            label = stringResource(R.string.quick_note_sheet_label_11),
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_07),
                            singleLine = true
                        )
                    }
                }
            }

            // ==================== 4. 心愿 (wish) ====================
            else if (noteType == "wish") {
                // 三维形态切换胶囊
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("item" to "好物心愿", "travel" to "远方旅程", "milestone" to "阶段里程碑").forEach { (kind, label) ->
                        PaperFilterPill(
                            selected = wishKind == kind,
                            onClick = { wishKind = kind },
                            label = label
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PaperTextField(
                        value = wishTitle,
                        onValueChange = { wishTitle = it },
                        label = stringResource(R.string.quick_note_sheet_label_12),
                        placeholder = when (wishKind) {
                            "item" -> "期许拥有的物件名称…"
                            "travel" -> "想要抵达的目的地或旅程…"
                            else -> "期许实现的里程碑目标…"
                        },
                        singleLine = true
                    )

                    PaperTextField(
                        value = wishBudgetYuan,
                        onValueChange = { wishBudgetYuan = it },
                        label = stringResource(R.string.quick_note_sheet_label_13),
                        placeholder = stringResource(R.string.quick_note_sheet_placeholder_08),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    if (wishKind == "item") {
                        PaperTextField(
                            value = wishSpec,
                            onValueChange = { wishSpec = it },
                            label = stringResource(R.string.quick_note_sheet_label_14),
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_09),
                            singleLine = true
                        )
                    } else if (wishKind == "travel") {
                        // MOB 表单审计 #13：绑定心愿专属目的地状态
                        PaperCityPickerField(
                            selectedCityCode = wishCityCode,
                            selectedCityName = wishCityName,
                            onCitySelected = { code, name ->
                                wishCityCode = code
                                wishCityName = name
                            },
                            placeholder = stringResource(R.string.quick_note_sheet_placeholder_10)
                        )
                    }

                    // MOB 表单审计 #14：未选择目标期时显示「未设置」占位，
                    // 不再显示一个不会被提交的「今天」
                    PaperDateField(
                        selectedDate = wishTargetDate ?: LocalDate.now(FormatUtils.BeijingZone),
                        onDateSelected = { wishTargetDate = it },
                        label = stringResource(R.string.quick_note_sheet_label_15),
                        unselectedText = if (wishTargetDate == null) "未设置" else null
                    )

                    PaperTextField(
                        value = wishNote,
                        onValueChange = { wishNote = it },
                        label = stringResource(R.string.quick_note_sheet_label_16),
                        placeholder = stringResource(R.string.quick_note_sheet_placeholder_11)
                    )

                    // 心愿模式不提供装裱入口：提交分支不携带附件（attachment_ids 未接线），
                    // 装了也只是既不上传也不清理、留给 outbox_media 变孤儿文件。
                    // 需要给心愿配图时请先在别处上传后再编辑心愿附件。
                    // PhotoMountingSection()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
