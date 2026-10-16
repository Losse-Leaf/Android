package com.looseleaf.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ArchiveToastMessage(
    val text: String,
    val isError: Boolean = false,
    val durationMs: Long = 2200L
)

object ArchiveToastManager {
    private val _messages = MutableSharedFlow<ArchiveToastMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun show(text: String, isError: Boolean = false, durationMs: Long = 2200L) {
        _messages.tryEmit(ArchiveToastMessage(text = text, isError = isError, durationMs = durationMs))
    }
}

/**
 * 散页纸墨通知宿主 (ArchiveToastHost)
 * 彻底替换 Android 原生灰黑底 Toast，以典雅纸签悬浮于屏幕上方
 */
@Composable
fun ArchiveToastHost(
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    var currentMessage by remember { mutableStateOf<ArchiveToastMessage?>(null) }

    LaunchedEffect(Unit) {
        ArchiveToastManager.messages.collect { msg ->
            currentMessage = msg
            delay(msg.durationMs)
            if (currentMessage == msg) {
                currentMessage = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
        ) {
            currentMessage?.let { msg ->
                val shape = RoundedCornerShape(20.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .shadow(elevation = 6.dp, shape = shape, spotColor = colors.frameShadowSpot)
                        .clip(shape)
                        .background(colors.archivePaper)
                        .border(
                            width = 0.5.dp,
                            color = if (msg.isError) colors.accentWarm else colors.borderDivider,
                            shape = shape
                        )
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(3.5.dp))
                            .background(if (msg.isError) colors.accentWarm else colors.accentMoss)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg.text,
                        fontFamily = ArchiveFontFamilies.EditorialSerif,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        color = colors.inkPrimary
                    )
                }
            }
        }
    }
}
