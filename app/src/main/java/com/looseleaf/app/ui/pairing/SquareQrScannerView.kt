package com.looseleaf.app.ui.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 经典纸墨正方形取景扫码器 (Square QR Scanner View)
 * 1:1 正方形黄金取景框，四角印章标尺、激光呼吸扫描线、双重二值化屏幕防反光与相册解码支持
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

    // 相册选取二维码图片解码器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val text = decodeQrFromUri(context, uri)
            if (!text.isNullOrBlank()) {
                onQrCodeScanned(text)
            } else {
                Toast.makeText(context, "未能在选中的图片中识别出有效二维码", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 全屏扫码器备用选项 (ZXing 原生)
    val fullscreenScanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        val contents = result.contents
        if (!contents.isNullOrBlank()) {
            onQrCodeScanned(contents)
        }
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

    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black)
                .clickable {
                    // 点击取景框触发中心对焦
                    try {
                        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                        val point = factory.createPoint(0.5f, 0.5f)
                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                            .setAutoCancelDuration(2, TimeUnit.SECONDS)
                            .build()
                        cameraControlRef?.startFocusAndMetering(action)
                    } catch (_: Exception) {}
                }
        ) {
            // CameraX 实时取景
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
                        val multiFormatReader = MultiFormatReader().apply {
                            setHints(
                                mapOf(
                                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                    DecodeHintType.TRY_HARDER to true,
                                    DecodeHintType.CHARACTER_SET to "UTF-8"
                                )
                            )
                        }

                        var isCaptured = false

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (isCaptured) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val result = decodeQrFromImage(imageProxy, multiFormatReader)
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
                            val cam = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            cameraControlRef = cam.cameraControl

                            // 初始自动聚焦中心
                            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                            val centerPoint = factory.createPoint(0.5f, 0.5f)
                            cam.cameraControl.startFocusAndMetering(
                                FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF).build()
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // 正方形取景框暗角遮罩与标尺 (1:1 纯正方形)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // 正方形边长设定为 230dp (自适应居中)
                val boxSize = 230.dp.toPx()
                val left = (canvasW - boxSize) / 2f
                val top = (canvasH - boxSize) / 2f
                val right = left + boxSize
                val bottom = top + boxSize

                // 1. 周围暗房遮罩
                val path = Path().apply {
                    addRect(Rect(0f, 0f, canvasW, canvasH))
                    addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(8.dp.toPx(), 8.dp.toPx())))
                    fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                }
                drawPath(path = path, color = colors.frameShadowSpot.copy(alpha = 0.75f))

                // 2. 四角暖赭石微标刻度
                val cornerLen = 22.dp.toPx()
                val strokeW = 3.5f
                val cornerColor = colors.accentWarm

                drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)
                drawLine(cornerColor, Offset(right, top), Offset(right - cornerLen, top), strokeW)
                drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLen), strokeW)
                drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW)
                drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW)
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

            // 底部提示
            Text(
                text = "轻触框内可手动重新对焦",
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = colors.archivePaper,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 辅助工具行：相册选取与全屏扫描
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Outlined.Photo, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.inkSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("从相册选取二维码", fontSize = 12.sp, color = colors.inkSecondary, fontFamily = ArchiveFontFamilies.EditorialSerif)
            }

            TextButton(
                onClick = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setOrientationLocked(false)
                        setPrompt("请对准配对二维码")
                        setBeepEnabled(true)
                    }
                    fullscreenScanLauncher.launch(options)
                },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Outlined.CropFree, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accentWarm)
                Spacer(modifier = Modifier.width(4.dp))
                Text("全屏扫码器", fontSize = 12.sp, color = colors.accentWarm, fontFamily = ArchiveFontFamilies.EditorialSerif)
            }
        }
    }
}

/**
 * 从 CameraX 图像帧解码二维码：
 * 1. 严格跳过硬件 rowStride padding（解决 vivo/小米/华为画面斜向拉丝错位）
 * 2. 依据传感器旋转角度（0/90/180/270）执行正交旋转
 * 3. 双重二值化（HybridBinarizer + GlobalHistogramBinarizer 防屏幕反光）
 */
private fun decodeQrFromImage(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height

    // 1. 去除硬件 rowStride 对齐填充
    val yBytes = ByteArray(width * height)
    val rowBuffer = ByteArray(width)
    for (row in 0 until height) {
        buffer.position(row * rowStride)
        buffer.get(yBytes, row * width, width)
    }

    val rotation = image.imageInfo.rotationDegrees

    // 2. 依据旋转角度校正像素数组
    val (rotatedBytes, rotW, rotH) = when (rotation) {
        90 -> {
            val rot = ByteArray(width * height)
            var i = 0
            for (x in 0 until width) {
                for (y in height - 1 downTo 0) {
                    rot[i++] = yBytes[y * width + x]
                }
            }
            Triple(rot, height, width)
        }
        270 -> {
            val rot = ByteArray(width * height)
            var i = 0
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    rot[i++] = yBytes[y * width + x]
                }
            }
            Triple(rot, height, width)
        }
        180 -> {
            val rot = ByteArray(width * height)
            var i = 0
            for (y in height - 1 downTo 0) {
                for (x in width - 1 downTo 0) {
                    rot[i++] = yBytes[y * width + x]
                }
            }
            Triple(rot, width, height)
        }
        else -> Triple(yBytes, width, height)
    }

    // 3. 执行识别 (双重二值化重试机制，大幅提升在电脑屏幕与反光环境下的识别率)
    return try {
        val source = PlanarYUVLuminanceSource(
            rotatedBytes, rotW, rotH, 0, 0, rotW, rotH, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decodeWithState(bitmap).text
    } catch (_: NotFoundException) {
        try {
            val source = PlanarYUVLuminanceSource(
                rotatedBytes, rotW, rotH, 0, 0, rotW, rotH, false
            )
            val bitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            null
        }
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

/**
 * 从本地相册 Uri 图片解析二维码
 */
private fun decodeQrFromUri(context: Context, uri: Uri): String? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8"
                )
            )
        }

        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            reader.decodeWithState(binaryBitmap).text
        } catch (_: NotFoundException) {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            reader.decodeWithState(binaryBitmap).text
        }
    } catch (e: Exception) {
        null
    }
}
