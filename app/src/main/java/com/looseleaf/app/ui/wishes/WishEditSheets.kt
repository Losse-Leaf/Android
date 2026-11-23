package com.looseleaf.app.ui.wishes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.looseleaf.app.R
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.data.sync.SyncManager
import com.looseleaf.app.model.CreateWishRequest
import com.looseleaf.app.model.Wish
import com.looseleaf.app.ui.components.*
import com.looseleaf.app.utils.FormatUtils
import kotlinx.coroutines.launch

@Composable
internal fun EditWishSheetDialog(
    wish: Wish,
    apiClient: APIClient,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val draftContext = LocalContext.current
    val draftStore = remember { DraftStore.get(draftContext) }
    val draftPrefix = remember(wish.id) { "wish_${wish.id}_" }

    var title by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}title") ?: wish.title) }
    var kind by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}kind") ?: (wish.kind ?: "item")) }
    var budgetYuan by remember(wish.id) {
        mutableStateOf(
            draftStore.getString("${draftPrefix}budget")
                ?: FormatUtils.centsToYuanString(wish.budget, includeSymbol = false)
        )
    }
    var spec by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}spec") ?: (wish.spec ?: "")) }
    var note by remember(wish.id) { mutableStateOf(draftStore.getString("${draftPrefix}note") ?: (wish.note ?: "")) }

    LaunchedEffect(title, kind, budgetYuan, spec, note) {
        draftStore.putString("${draftPrefix}title", title)
        draftStore.putString("${draftPrefix}kind", kind)
        draftStore.putString("${draftPrefix}budget", budgetYuan)
        draftStore.putString("${draftPrefix}spec", spec)
        draftStore.putString("${draftPrefix}note", note)
    }
    var isSubmitting by remember { mutableStateOf(false) }
    val budgetCents = FormatUtils.yuanToCents(budgetYuan)
    val budgetInvalid = budgetYuan.isNotBlank() &&
        budgetYuan.trim() != "0" &&
        budgetYuan.trim() != "0.00" &&
        budgetCents == 0L

    ArchiveSheet(
        onDismissRequest = onDismiss,
        dismissEnabled = { !isSubmitting },
        title = stringResource(R.string.wishes_edit_title_tpl, wish.title),
        actionText = if (isSubmitting) "保存中" else "保存",
        isActionEnabled = title.isNotBlank() && !budgetInvalid && !isSubmitting,
        onAction = {
            if (budgetInvalid) {
                ArchiveToastManager.show("金额格式不正确，请输入有效数字", isError = true)
                return@ArchiveSheet
            }
            isSubmitting = true
            scope.launch {
                runCatching {
                    val cents = FormatUtils.yuanToCents(budgetYuan)
                    val syncManager = SyncManager.getInstance(draftContext)
                    val queued = syncManager.updateWishOrQueue(
                        id = wish.id,
                        req = CreateWishRequest(
                            title = title.trim(),
                            kind = kind,
                            budget = cents,
                            spec = spec.trim(),
                            note = note.trim(),
                            targetDate = wish.targetDate,
                            attachmentIds = wish.attachments.map { it.id }.takeIf { it.isNotEmpty() }
                        )
                    )
                    if (queued) ArchiveToastManager.show("当前离线，已排队保存修改")
                    else ArchiveToastManager.show("心愿已修订")
                    draftStore.clearPrefix(draftPrefix)
                    onSaved()
                    onDismiss()
                }.onFailure {
                    ArchiveToastManager.show("修订失败: ${it.message}", isError = true)
                }
                isSubmitting = false
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WishKindSelector(kind = kind, onKindChange = { kind = it })

            ArchiveField(
                value = title,
                onValueChange = { title = it },
                label = stringResource(R.string.wishes_edit_title_label),
                placeholder = "想要的东西、想去的地方、想达成的事",
                singleLine = true,
                errorMessage = if (title.isBlank()) "请输入心愿标题" else null,
                reservesErrorSlot = true
            )

            ArchiveField(
                value = spec,
                onValueChange = { spec = it },
                label = stringResource(R.string.wishes_edit_spec_label),
                placeholder = "规格/型号或补充说明（选填）",
                singleLine = true
            )

            ArchiveField(
                value = budgetYuan,
                onValueChange = { budgetYuan = it },
                label = stringResource(R.string.wishes_edit_budget_label),
                placeholder = "0.00",
                isNumeric = true,
                singleLine = true,
                errorMessage = if (budgetInvalid) "金额格式不正确，请输入有效数字" else null,
                reservesErrorSlot = true
            )

            ArchiveField(
                value = note,
                onValueChange = { note = it },
                label = "手记与备忘",
                placeholder = stringResource(R.string.wishes_edit_note_placeholder),
                singleLine = false,
                minLines = 3
            )
        }
    }
}
