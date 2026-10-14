package com.looseleaf.app.darkroom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
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
    val inferredCityCode: String? = null
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
    private const val MAX_EDGE = 3840 // 4K 超清限定

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
                    longitude = lng
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
     * 2. 约束最大边至 3840px (4K)
     * 3. 自动根据 EXIF 方向纠正旋转
     * 4. 85% 质量 JPEG / WebP 编码压缩
     */
    suspend fun processImage(
        context: Context,
        uri: Uri
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val exif = extractExif(context, uri)

        // 1. 读取原始尺寸
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOptions)
        }

        var origW = boundsOptions.outWidth
        var origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) {
            throw IllegalArgumentException("无法解码图片尺寸")
        }

        // 计算采样率
        var sampleSize = 1
        val maxDim = max(origW, origH)
        while (maxDim / (sampleSize * 2) >= MAX_EDGE) {
            sampleSize *= 2
        }

        // 2. 解码 Bitmap
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: throw IllegalStateException("Bitmap 解码失败")

        // 3. 读取方向并纠正
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

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        // 缩放至最大边 3840
        val currentMax = max(rawBitmap.width, rawBitmap.height)
        if (currentMax > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / currentMax
            matrix.postScale(scale, scale)
        }

        val rotatedBitmap = if (!matrix.isIdentity) {
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        // 4. 压缩为 85% 质量 JPEG
        val outStream = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
        val bytes = outStream.toByteArray()

        ProcessedImageResult(
            compressedBytes = bytes,
            width = rotatedBitmap.width,
            height = rotatedBitmap.height,
            mimeType = "image/jpeg",
            exif = exif
        )
    }

    /**
     * 执行直传 R2 链路，降级时走统一中转
     */
    suspend fun uploadImage(
        apiClient: APIClient,
        processed: ProcessedImageResult,
        filename: String = "photo_${System.currentTimeMillis()}.jpg"
    ): Attachment = withContext(Dispatchers.IO) {
        try {
            // 1. 请求预签名凭证
            val presignReq = PresignUploadRequest(
                filename = filename,
                mimeType = processed.mimeType,
                size = processed.compressedBytes.size.toLong(),
                folder = "entries",
                width = processed.width,
                height = processed.height
            )
            val presignResp = apiClient.presignUpload(presignReq)

            // 2. 直传 R2
            apiClient.uploadToR2Direct(
                uploadUrl = presignResp.uploadUrl,
                fileBytes = processed.compressedBytes,
                mimeType = processed.mimeType
            )

            // 3. 确认上传入库
            val completeReq = CompleteUploadRequest(
                objectKey = presignResp.objectKey,
                filename = filename,
                mimeType = processed.mimeType,
                size = processed.compressedBytes.size.toLong(),
                width = processed.width,
                height = processed.height
            )
            apiClient.completeUpload(completeReq)
        } catch (e: Exception) {
            Log.w(TAG, "R2 direct presign failed (${e.message}), falling back to unified upload")
            // 降级使用统一上传接口
            apiClient.uploadUnifiedFallback(
                fileBytes = processed.compressedBytes,
                filename = filename,
                mimeType = processed.mimeType,
                folder = "entries"
            )
        }
    }
}
