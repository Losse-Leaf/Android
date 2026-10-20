package com.looseleaf.app.darkroom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.looseleaf.app.data.api.APIClient
import com.looseleaf.app.model.Attachment
import com.looseleaf.app.model.CompleteUploadRequest
import com.looseleaf.app.model.PresignUploadRequest
import com.looseleaf.app.utils.CityDictionary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ExtractedExif(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val focalLength: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val inferredCityCode: String? = null,
    val inferredCityName: String? = null
) {
    /**
     * 生成经典胶片机身与底片元数据底款
     * 例如: "LEICA M6 · SUMMICRON 35mm · 1/250s f/2.0 ISO 400"
     */
    fun toFilmMetadataString(): String {
        val parts = mutableListOf<String>()
        val camera = listOfNotNull(make, model).joinToString(" ").trim()
        if (camera.isNotBlank()) parts.add(camera)
        lensModel?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        val shooting = mutableListOf<String>()
        focalLength?.let { shooting.add(it) }
        aperture?.let { shooting.add("f/$it") }
        shutterSpeed?.let { shooting.add("${it}s") }
        iso?.let { shooting.add("ISO $it") }
        if (shooting.isNotEmpty()) {
            parts.add(shooting.joinToString(" "))
        }

        return if (parts.isNotEmpty()) parts.joinToString(" · ") else "散页随行相纸"
    }
}

data class ProcessedImageResult(
    val compressedBytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val exif: ExtractedExif
)

object ImagePipeline {
    private const val TAG = "ImagePipeline"
    /** 最长边约束：2048px（并非 4K；旧注释与 README 中的 3840/85%/WebP 已不成立） */
    private const val MAX_EDGE = 2048
    /** JPEG 质量 82，视觉接近无损，体积约为原图 8%~15% */
    private const val JPEG_QUALITY = 82

    /**
     * 读取图片 EXIF 信息
     */
    fun extractExif(context: Context, uri: Uri): ExtractedExif {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val ex = ExifInterface(stream)
                val make = ex.getAttribute(ExifInterface.TAG_MAKE)?.trim()
                val model = ex.getAttribute(ExifInterface.TAG_MODEL)?.trim()
                val lens = ex.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()
                val focal = ex.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { "${it.roundToInt()}mm" }
                val fNumber = ex.getAttribute(ExifInterface.TAG_F_NUMBER)?.trim()
                val exposure = ex.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.trim()
                val iso = ex.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.trim()
                val date = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.trim()

                val latLong = ex.latLong
                val lat = latLong?.get(0)
                val lng = latLong?.get(1)
                val nearestCity = if (lat != null && lng != null) {
                    CityDictionary.nearestCityForCoordinates(lat, lng)
                } else null

                ExtractedExif(
                    make = make,
                    model = model,
                    lensModel = lens,
                    focalLength = focal,
                    aperture = fNumber,
                    shutterSpeed = exposure,
                    iso = iso,
                    dateTimeOriginal = date,
                    latitude = lat,
                    longitude = lng,
                    inferredCityCode = nearestCity?.first,
                    inferredCityName = nearestCity?.second
                )
            } ?: ExtractedExif()
        } catch (e: Exception) {
            Log.w(TAG, "EXIF extraction failed: ${e.message}")
            ExtractedExif()
        }
    }

    /**
     * 端侧智能压制管线：
     * 1. 提取 EXIF
     * 2. 约束最大边至 2048px
     * 3. 自动根据 EXIF 方向纠正旋转（API 28+ 由 ImageDecoder 完成）
     * 4. 82% 质量 JPEG 编码压缩
     */
    suspend fun processImage(
        context: Context,
        uri: Uri
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val exif = extractExif(context, uri)

        // 解码 Bitmap。
        // API 28+ 用 ImageDecoder 精确设定目标尺寸（同时自动处理 EXIF 方向），
        // 避免 BitmapFactory 只能按 2 的幂采样、导致解码峰值翻倍（48MP 图曾接近 48MB）。
        val usesImageDecoder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val rawBitmap: Bitmap = if (usesImageDecoder) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = max(info.size.width, info.size.height)
                if (maxDim > MAX_EDGE) {
                    val scale = MAX_EDGE.toFloat() / maxDim
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            // 旧路径必须先读原始尺寸才能算采样率；API 28+ 由 ImageDecoder 自己处理，无需多开一次流
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            val origW = boundsOptions.outWidth
            val origH = boundsOptions.outHeight
            if (origW <= 0 || origH <= 0) {
                throw IllegalArgumentException("无法解码图片尺寸")
            }

            var sampleSize = 1
            while (origW / (sampleSize * 2) >= MAX_EDGE || origH / (sampleSize * 2) >= MAX_EDGE) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: throw IllegalStateException("Bitmap 解码失败")
        }

        // 3. 方向纠正：ImageDecoder 已按 EXIF 自动旋转，这里只处理旧路径
        val matrix = Matrix()
        if (!usesImageDecoder) {
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
            }
        }

        // 旧路径按 2 的幂采样，可能仍大于目标边长，补一次缩放
        val currentMax = max(rawBitmap.width, rawBitmap.height)
        if (currentMax > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / currentMax
            matrix.postScale(scale, scale)
        }

        val rotatedBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true).also {
                if (it !== rawBitmap) rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }

        // 4. 压缩为 82% 质量 JPEG
        val outStream = ByteArrayOutputStream(512 * 1024)
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outStream)
        val bytes = outStream.toByteArray()
        val finalWidth = rotatedBitmap.width
        val finalHeight = rotatedBitmap.height
        if (rotatedBitmap !== rawBitmap) rotatedBitmap.recycle()
        rawBitmap.recycle()

        ProcessedImageResult(
            compressedBytes = bytes,
            width = finalWidth,
            height = finalHeight,
            mimeType = "image/jpeg",
            exif = exif
        )
    }

    /**
     * 执行统一媒体直传与多重降级管线：
     * 1. 尝试请求 POST /api/v1/uploads/presign 获取直传授权
     * 2. 执行 HTTP PUT 直传 R2 存储桶
     * 3. 请求 POST /api/v1/uploads/complete 确认入库
     * 当后端未配置 R2 (HTTP 503) 或直传网络受阻时，自动平滑降级至中枢统一中转通道 (/api/v1/files/upload)
     * 行为与 macOS 端 R2DirectUploader 保持 100% 一致
     */
    suspend fun uploadImage(
        apiClient: APIClient,
        processed: ProcessedImageResult,
        filename: String = "photo_${System.currentTimeMillis()}.jpg"
    ): Attachment = withContext(Dispatchers.IO) {
        try {
            val presignReq = PresignUploadRequest(
                filename = filename,
                contentType = processed.mimeType,
                size = processed.compressedBytes.size.toLong()
            )
            val presignRes = apiClient.presignUpload(presignReq)
            apiClient.uploadToR2Direct(
                uploadUrl = presignRes.uploadUrl,
                fileBytes = processed.compressedBytes,
                mimeType = processed.mimeType
            )
            apiClient.completeUpload(
                CompleteUploadRequest(
                    uploadId = presignRes.uploadId,
                    objectKey = presignRes.objectKey,
                    originalFilename = filename,
                    mimeType = processed.mimeType,
                    size = processed.compressedBytes.size.toLong(),
                    width = processed.width,
                    height = processed.height
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "R2 direct upload unavailable (${e.message}), falling back to unified multipart upload")
            apiClient.uploadUnifiedFallback(
                fileBytes = processed.compressedBytes,
                filename = filename,
                mimeType = processed.mimeType,
                folder = "entries"
            )
        }
    }
}
