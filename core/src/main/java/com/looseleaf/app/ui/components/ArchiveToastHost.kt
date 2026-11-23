package com.looseleaf.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveMotion
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class ArchiveToastMessage(
    val text: String,
    val isError: Boolean = false,
    val durationMs: Long = 2200L,
    /** 可选的动作（如「撤销」）。点击后立即关闭提示并执行动作。 */
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

object ArchiveToastManager {
    private val _messages = MutableSharedFlow<ArchiveToastMessage>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private fun isCancellationNoise(text: String, isError: Boolean): Boolean {
        if (!isError) return false
        val lower = text.lowercase()
        return lower.contains("coroutine scope left the composition") ||
            lower.contains("cancellationexception") ||
            lower.contains("standalonecoroutine was cancelled") ||
            lower.contains("job was cancelled")
    }

    fun show(text: String, isError: Boolean = false, durationMs: Long = 2200L) {
        // 生命周期正常的协程取消绝不应作为错误提示展示给用户
        if (isCancellationNoise(text, isError)) return
        // 未显式指定时长的错误提示兜底延长到 4.5s，留足阅读时间
        val effectiveDuration = if (isError && durationMs == 2200L) 4500L else durationMs
        _messages.tryEmit(ArchiveToastMessage(text = text, isError = isError, durationMs = effectiveDuration))
    }

    /**
     * 带动作的提示（如「已删除 · 撤销」），默认展示 6 秒。
     */
    fun showWithAction(
        text: String,
        actionLabel: String,
        durationMs: Long = 6000L,
        isError: Boolean = false,
        action: () -> Unit
    ) {
        if (isCancellationNoise(text, isError)) return
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
 * 全局提示宿主 (ArchiveToastHost)
 *
 * 屏幕顶部下落的反相墨色胶囊（类似「灵动岛」的克制版本）：
 * - 成功：左侧松绿圆点；错误：左侧胭脂警示图形，且胶囊停留更久
 * - 新消息立即顶替旧消息，不排队
 * - 整条提示为无障碍 live region，读屏会主动播报
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
            .padding(top = 10.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentMessage != null,
            enter = fadeIn(tween(ArchiveMotion.DurationFast)) +
                slideInVertically(tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseOutEmphasized)) { -it } +
                scaleIn(tween(ArchiveMotion.DurationBase, easing = ArchiveMotion.EaseOutEmphasized), initialScale = 0.92f),
            exit = fadeOut(tween(ArchiveMotion.DurationFast)) +
                slideOutVertically(tween(ArchiveMotion.DurationFast, easing = ArchiveMotion.EaseIn)) { -it / 2 } +
                scaleOut(tween(ArchiveMotion.DurationFast), targetScale = 0.96f)
        ) {
            currentMessage?.let { msg ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                        .shadow(
                            elevation = ArchiveShadows.floatingLayerElevation,
                            shape = ArchiveShapes.full,
                            spotColor = colors.frameShadowSpot,
                            ambientColor = colors.frameShadowAmbient
                        )
                        .clip(ArchiveShapes.full)
                        .background(colors.inverseSurface)
                        .padding(
                            start = 16.dp,
                            end = if (msg.actionLabel != null) 6.dp else 20.dp,
                            top = 11.dp,
                            bottom = 11.dp
                        )
                ) {
                    if (msg.isError) {
                        Icon(
                            imageVector = FolioGlyphs.Alert,
                            contentDescription = null,
                            tint = colors.inverseAlert,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(ArchiveShapes.full)
                                .background(colors.accentMoss)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = msg.text,
                        style = ArchiveTextStyles.body,
                        color = colors.inverseInk,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                    msg.actionLabel?.let { label ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = ArchiveTextStyles.label,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inverseAccent,
                            modifier = Modifier
                                .clip(ArchiveShapes.full)
                                .clickable {
                                    dismissJob?.cancel()
                                    currentMessage = null
                                    msg.action?.invoke()
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
