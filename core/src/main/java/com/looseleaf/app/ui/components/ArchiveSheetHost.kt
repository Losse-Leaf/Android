package com.looseleaf.app.ui.components

import androidx.compose.runtime.*

/**
 * 散页全局抽屉宿主状态 (ArchiveSheetHostState)
 * 挂载于应用最根部（MainActivity 根层 Box），确保弹层遮罩与宿主覆盖 Scaffold、底栏与状态栏，
 * 半高与全高均不露出底栏。
 */
class ArchiveSheetHostState {
    var currentSheet by mutableStateOf<(@Composable () -> Unit)?>(null)
        internal set

    val isVisible: Boolean get() = currentSheet != null

    fun setSheet(sheet: (@Composable () -> Unit)?) {
        currentSheet = sheet
    }

    fun clearSheet() {
        currentSheet = null
    }

    companion object {
        val None = ArchiveSheetHostState()
    }
}

val LocalArchiveSheetHost = staticCompositionLocalOf { ArchiveSheetHostState.None }

@Composable
fun ArchiveSheetHost(
    hostState: ArchiveSheetHostState = LocalArchiveSheetHost.current
) {
    hostState.currentSheet?.invoke()
}
