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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight

data class ArchiveToastMessage(
    val text: String,
    val isError: Boolean = false,
    val durationMs: Long = 2200L,
    /** MOB-P2-47：可选的动作（如「撤销」）。点击后立即关闭纸签并执行动作。 */
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

object ArchiveToastManager {
    private val _messages = MutableSharedFlow<ArchiveToastMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun show(text: String, isError: Boolean = false, durationMs: Long = 2200L) {
        // MOB-P1-79：错误类提示的默认时长由调用方决定；这里对未显式指定时长的错误提示
        // 兜底延长到 4.5s —— 2.2s 根本来不及读完「对齐失败: xxx」这类带异常串的文案。
        val effectiveDuration = if (isError && durationMs == 2200L) 4500L else durationMs
        _messages.tryEmit(ArchiveToastMessage(text = text, isError = isError, durationMs = effectiveDuration))
    }

    /**
     * MOB-P2-47：带动作的提示（如「已删除 · 撤销」）。
     * 动作型提示默认多给 5 秒 —— 用户需要时间判断要不要点「撤销」。
     */
    fun showWithAction(
        text: String,
        actionLabel: String,
        durationMs: Long = 6000L,
        isError: Boolean = false,
        action: () -> Unit
    ) {
        _messages.tryEmit(
            ArchiveToastMessage(
                text = text,
                isError = isError,
                durationMs = durationMs,
                actionLabel = actionLabel,
                action = action
            )
        )
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
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        ArchiveToastManager.messages.collect { msg ->
            // 新消息立即顶掉旧消息，而不是排队等上一条 2.2 秒播完
            dismissJob?.cancel()
            currentMessage = msg
            dismissJob = launch {
                delay(msg.durationMs)
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
                        // MOB-P1-79：整条纸签标记为 live region，错误播报更可靠
                        .semantics { liveRegion = LiveRegionMode.Assertive }
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
                        color = colors.inkPrimary,
                        // MOB-P1-79：Toast 之前是普通 Text —— TalkBack 完全读不到，
                        // 「对齐失败」「相纸装裱失败」「同步失败 N」这些关键错误对读屏用户等于不存在。
                        // liveRegion 让读屏在提示出现时主动播报。
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Assertive
                        }
                    )
                    // MOB-P2-47：可撤销动作
                    msg.actionLabel?.let { label ->
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            fontFamily = ArchiveFontFamilies.EditorialSerif,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = colors.accentWarm,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    dismissJob?.cancel()
                                    currentMessage = null
                                    msg.action?.invoke()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
