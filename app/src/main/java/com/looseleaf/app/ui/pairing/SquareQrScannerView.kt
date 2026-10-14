package com.looseleaf.app.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import java.util.concurrent.Executors

/**
 * 经典纸墨正方形取景扫码器 (Square QR Scanner View)
 * 1:1 正方形黄金取景框，四角实体印章标尺与激光呼吸扫描线
 */
@Composable
fun SquareQrScannerView(
    onQrCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LooseLeafTheme.colors

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.archiveSubtle)
                .border(0.5.dp, colors.borderDivider, RoundedCornerShape(4.dp))
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "需要相机权限以扫描配对二维码",
                    fontFamily = ArchiveFontFamilies.EditorialSerif,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentWarm,
                        contentColor = colors.archivePaper
                    )
                ) {
                    Text("授予相机权限", fontFamily = ArchiveFontFamilies.EditorialSerif)
                }
            }
        }
        return
    }

    // 正方形取景框动画扫描线
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black)
    ) {
        // CameraX 预览
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    var isCaptured = false

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isCaptured) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val result = decodeQrFromImage(imageProxy)
                        if (!result.isNullOrBlank()) {
                            isCaptured = true
                            imageProxy.close()
                            previewView.post {
                                onQrCodeScanned(result)
                            }
                        } else {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // 正方形暗角蒙版与取景标尺 (Square Cutout & Framing Overlay)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // 1:1 正方形取景框边长 (限定为 230dp)
            val boxSize = 230.dp.toPx()
            val left = (canvasW - boxSize) / 2f
            val top = (canvasH - boxSize) / 2f
            val right = left + boxSize
            val bottom = top + boxSize

            // 1. 周围深色遮罩 (半透明暗房遮罩)
            val path = Path().apply {
                addRect(Rect(0f, 0f, canvasW, canvasH))
                addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(8.dp.toPx(), 8.dp.toPx())))
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            }
            drawPath(path = path, color = colors.frameShadowSpot.copy(alpha = 0.75f))

            // 2. 四角暖赭石微标刻度 (24dp 长, 3.5dp 粗)
            val cornerLen = 22.dp.toPx()
            val strokeW = 3.5f
            val cornerColor = colors.accentWarm

            // 左上角
            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
            drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)
            // 右上角
            drawLine(cornerColor, Offset(right, top), Offset(right - cornerLen, top), strokeW)
            drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), strokeW)
            // 左下角
            drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW)
            drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW)
            // 右下角
            drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW)
            drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW)

            // 3. 激光扫描线
            val laserY = top + (boxSize * laserProgress)
            drawLine(
                color = cornerColor.copy(alpha = 0.85f),
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(right - 8.dp.toPx(), laserY),
                strokeWidth = 2f
            )
        }

        // 底部提示文字
        Text(
            text = "将 Web 展厅管理台的配对二维码置于方框内",
            fontFamily = ArchiveFontFamilies.EditorialSerif,
            fontSize = 11.sp,
            letterSpacing = 0.5.sp,
            color = colors.archivePaper,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

/**
 * 从 CameraX 图像帧解码二维码文本
 */
private fun decodeQrFromImage(image: ImageProxy): String? {
    val buffer = image.planes[0].buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val width = image.width
    val height = image.height
    val rotation = image.imageInfo.rotationDegrees

    val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    // 旋转校准
    val (sourceData, sWidth, sHeight) = if (rotation == 90) {
        val rotated = ByteArray(data.size)
        var k = 0
        for (x in 0 until width) {
            for (y in height - 1 downTo 0) {
                rotated[k++] = data[y * width + x]
            }
        }
        Triple(rotated, height, width)
    } else {
        Triple(data, width, height)
    }

    return try {
        val source = PlanarYUVLuminanceSource(
            sourceData, sWidth, sHeight, 0, 0, sWidth, sHeight, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val result = reader.decodeWithState(bitmap)
        result.text
    } catch (_: Exception) {
        null
    }
}
