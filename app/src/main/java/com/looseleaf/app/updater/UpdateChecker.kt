package com.looseleaf.app.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.looseleaf.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val body: String,
    val apkUrl: String,
    val publishedAt: String,
    /** GitHub Release 资产自带的 sha256 摘要（若上游提供），下载后强制校验 */
    val apkSha256: String? = null
)

sealed class UpdateCheckResult {
    data class HasUpdate(val release: ReleaseInfo) : UpdateCheckResult()
    object AlreadyLatest : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class InAppDownloadState {
    object Idle : InAppDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : InAppDownloadState()
    data class ReadyToInstall(val apkFile: File) : InAppDownloadState()
    data class Failed(val error: String) : InAppDownloadState()
}

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    const val REPO = "Losse-Leaf/Android"

    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 检查云端 GitHub Releases 是否有新版本
     */
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$REPO/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "LooseLeaf-Android/$currentVersionName")
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    if (resp.code == 404) {
                        return@withContext UpdateCheckResult.AlreadyLatest
                    }
                    return@withContext UpdateCheckResult.Error("无法连接至 GitHub Releases (HTTP ${resp.code})")
                }

                val bodyStr = resp.body?.string() ?: return@withContext UpdateCheckResult.Error("响应为空")
                val rootObj = json.parseToJsonElement(bodyStr).jsonObject

                val tagName = rootObj["tag_name"]?.jsonPrimitive?.content ?: ""
                val releaseBody = rootObj["body"]?.jsonPrimitive?.content ?: ""
                val publishedAt = rootObj["published_at"]?.jsonPrimitive?.content ?: ""
                val assets = rootObj["assets"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

                val cleanRemoteTag = tagName.removePrefix("v").trim()
                val cleanLocalTag = currentVersionName.removePrefix("v").trim()

                if (cleanRemoteTag.isEmpty()) {
                    return@withContext UpdateCheckResult.AlreadyLatest
                }

                if (isVersionGreaterThan(cleanRemoteTag, cleanLocalTag)) {
                    var apkDownloadUrl: String? = null
                    var apkDigest: String? = null
                    for (asset in assets) {
                        val assetObj = asset.jsonObject
                        val name = assetObj["name"]?.jsonPrimitive?.content ?: ""
                        val downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: ""
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = downloadUrl
                            // GitHub 资产摘要格式为 "sha256:<hex>"
                            apkDigest = assetObj["digest"]?.jsonPrimitive?.content
                                ?.removePrefix("sha256:")
                                ?.takeIf { it.length == 64 }
                            break
                        }
                    }

                    val finalUrl = apkDownloadUrl ?: "https://github.com/$REPO/releases/tag/$tagName"

                    val releaseInfo = ReleaseInfo(
                        tagName = tagName,
                        versionName = cleanRemoteTag,
                        body = releaseBody,
                        apkUrl = finalUrl,
                        publishedAt = publishedAt,
                        apkSha256 = apkDigest
                    )
                    Log.i(TAG, "Found newer version: $cleanRemoteTag (current: $cleanLocalTag)")
                    return@withContext UpdateCheckResult.HasUpdate(releaseInfo)
                } else {
                    return@withContext UpdateCheckResult.AlreadyLatest
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed checking for updates: ${e.message}")
            UpdateCheckResult.Error(e.message ?: "网络连接异常")
        }
    }

    /**
     * 应用内流式高速下载 APK（带实时进度回调，完全绕过外部浏览器）。
     * 若上游 Release 提供了 sha256 摘要，下载完成后强制校验；不一致立即删除并报错。
     */
    suspend fun downloadApkStream(
        context: Context,
        apkUrl: String,
        versionName: String,
        expectedSha256: String? = null,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "LooseLeaf-Android/$currentVersionName")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("下载请求失败: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("下载响应体为空")
            val totalBytes = body.contentLength()

            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.cacheDir, "downloads").apply { mkdirs() }
            val apkFile = File(downloadDir, "LooseLeaf-v$versionName.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            val digest = MessageDigest.getInstance("SHA-256")

            val buffer = ByteArray(8192)
            var downloadedBytes = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val progress = if (totalBytes > 0) {
                            downloadedBytes.toFloat() / totalBytes.toFloat()
                        } else 0f
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            if (!expectedSha256.isNullOrBlank()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    apkFile.delete()
                    throw IOException("安装包校验失败（SHA-256 不匹配），已删除下载文件")
                }
            }

            apkFile
        }
    }

    /**
     * 判断下载到的 APK 是否与当前已安装应用使用同一签名。
     *
     * 发布密钥沿用仓库内原钥（alias looseleaf），正常情况下同钥应当直接放行；
     * 这道校验是兜底——一旦出现签名不一致（误换了签名的包 / 包被篡改），
     * 明确告知用户「需卸载重装」，而不是让系统安装器抛一个看不懂的错误。
     */
    fun isSignedByCurrentApp(context: Context, apkFile: File): Boolean {
        return try {
            val pm = context.packageManager
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
            } ?: return false

            val archiveCerts: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archiveInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                archiveInfo.signatures?.map { it.toByteArray() }.orEmpty()
            }

            val selfInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val selfCerts: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                selfInfo.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                selfInfo.signatures?.map { it.toByteArray() }.orEmpty()
            }

            archiveCerts.isNotEmpty() && archiveCerts.any { candidate ->
                selfCerts.any { it.contentEquals(candidate) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Signature check failed: ${e.message}")
            false
        }
    }

    /**
     * 直接调起系统安装器安装 APK（支持 FileProvider 与 Android 8.0+ 未知应用安装意图）。
     * 返回值：true 表示已拉起安装流程；false 表示签名不一致 / 文件异常，调用方需提示用户。
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

        if (!isSignedByCurrentApp(context, apkFile)) {
            Log.w(TAG, "APK signature does not match installed app; reinstall required")
            return false
        }

        try {
            // Android 8.0+ 检查未知来源安装权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return true
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching package installer: ${e.message}", e)
            openDownloadPage(context, apkFile.absolutePath)
            return false
        }
    }

    /**
     * 语义版本对比 (如 1.0.1 > 1.0.0)。
     * 每段只取前导数字：`1.4.6-beta` 视为 `1.4.6`，不会因为后缀整段变成 0。
     */
    fun isVersionGreaterThan(remote: String, local: String): Boolean {
        val rParts = parseVersion(remote)
        val lParts = parseVersion(local)
        val maxLen = maxOf(rParts.size, lParts.size)

        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun parseVersion(version: String): List<Int> =
        version.removePrefix("v").trim()
            .split(".")
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /**
     * 兜底调起浏览器下载
     */
    fun openDownloadPage(context: Context, apkUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed opening download URL: ${e.message}")
        }
    }
}
