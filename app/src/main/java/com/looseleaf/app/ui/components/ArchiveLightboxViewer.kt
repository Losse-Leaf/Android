package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import coil.size.Precision
import coil.size.Scale
import coil.size.Size
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R

/**
 * 随行暗房全屏相纸灯箱 (ArchiveLightboxViewer)
 * 提供双指捏合缩放、双击还原与全屏暗房银盐观片体验
 *
 * @param requestOriginalResolution 兜底入口：仅当用户显式选择「查看原图」时才传 true。
 * 当前灯箱 UI 没有该入口，默认路径始终按容器尺寸解码；保留此开关而不是在默认路径里
 * 请求 ORIGINAL，避免高像素照片在解码瞬间 OOM。
 */
@Composable
fun ArchiveLightboxViewer(
    imageUrl: String,
    metadataText: String? = null,
    onDismiss: () -> Unit,
    requestOriginalResolution: Boolean = false
) {
    val colors = LooseLeafTheme.colors
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // 双击缩放动画（AOPT-05b）：拖拽时由手势直接写 scale，双击时走动画
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 220),
        label = stringResource(R.string.archive_lightbox_viewer_label_01)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.frameShadowSpot.copy(alpha = 0.94f))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 顶栏关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.archivePaper.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.archive_lightbox_viewer_contentDescription_01),
                    tint = colors.archivePaper,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 图像主视口（支持缩放与拖移）
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 60.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                val maxOffsetX = (size.width * (scale - 1)) / 2
                                val maxOffsetY = (size.height * (scale - 1)) / 2
                                offsetX = (offsetX + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    // 手势语义（AOPT-05b）：
                    // 双击 = 放大到 2.5 倍 / 还原；单击 = 仅在未放大时退出。
                    // 不再用 clickable——放大后单指拖拽细节时手指抬起会误触发它，图片被瞬间缩回 1f。
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            },
                            onTap = {
                                if (scale <= 1.05f) {
                                    onDismiss()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val density = LocalDensity.current.density
                // P0：按容器尺寸 × density 计算解码目标（默认 2× 过采样），
                // 单边 ≤ 4096 且总像素 ≤ 16MP，避免 12MP/48MP 原图直接解码导致 OOM。
                val targetSize = remember(maxWidth, maxHeight, density) {
                    LightboxDecodeSize.compute(
                        widthDp = maxWidth.value,
                        heightDp = maxHeight.value,
                        density = density
                    )
                }
                val imageRequest = remember(imageUrl, targetSize, requestOriginalResolution) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        // 不设 memoryCacheKey：沿用 Coil 默认 key（同一张图只保留一份），
                        // 请求尺寸不匹配时 Coil 会自行淘汰重解码（AOPT-04b）
                        .diskCacheKey(imageUrl)
                        .apply {
                            if (requestOriginalResolution) {
                                // 兜底路径：只有用户显式「查看原图」才走这里。
                                // 当前 UI 未提供入口，默认路径不会命中，避免峰值内存失控。
                                size(Size.ORIGINAL)
                            } else {
                                // MOB-P2-66：灯箱要「放大看」，按布局尺寸解码会在 4× 放大后
                                // 出现马赛克；这里按容器尺寸过采样，而不是请求整张原图。
                                // FIT 保证解码宽高都落在目标框内，像素上限才真正成立
                                // （默认 FILL 遇到宽高比差异大的照片会解出更大的尺寸）。
                                size(targetSize.width, targetSize.height)
                                scale(Scale.FIT)
                            }
                        }
                        .precision(Precision.INEXACT)
                        // 硬件位图（API 26+）：解码后驻留 GPU 显存，减少 Java 堆占用。
                        .allowHardware(true)
                        .build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = metadataText ?: "Full Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = animatedScale,
                            scaleY = animatedScale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }

            // 底栏胶片相机与拍摄参数
            if (!metadataText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(colors.frameShadowSpot.copy(alpha = 0.75f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = metadataText,
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = colors.archivePaper.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
