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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 随行暗房全屏相纸灯箱 (ArchiveLightboxViewer)
 * 提供双指捏合缩放、双击还原与全屏暗房银盐观片体验
 */
@Composable
fun ArchiveLightboxViewer(
    imageUrl: String,
    metadataText: String? = null,
    onDismiss: () -> Unit
) {
    val colors = LooseLeafTheme.colors
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // 双击缩放动画（AOPT-05b）：拖拽时由手势直接写 scale，双击时走动画
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 220),
        label = "lightbox-scale"
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
                    contentDescription = "关闭灯箱",
                    tint = colors.archivePaper,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 图像主视口（支持缩放与拖移）
            Box(
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
                val imageRequest = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        // 不设 memoryCacheKey：让 Coil 按容器尺寸分别缓存缩略图与全屏大图（AOPT-04b）
                        .diskCacheKey(imageUrl)
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
