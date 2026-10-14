package com.looseleaf.app.data.api

import android.content.Context
import android.util.Log
import com.looseleaf.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class APIException(val code: Int, override val message: String) : Exception("[$code] $message")

class APIClient private constructor(private val context: Context) {

    companion object {
        private const val TAG = "APIClient"
        private const val PREFS_NAME = "looseleaf_api_prefs"
        private const val KEY_BASE_URL = "hub_base_url"

        @Volatile
        private var instance: APIClient? = null

        fun getInstance(context: Context): APIClient {
            return instance ?: synchronized(this) {
                instance ?: APIClient(context.applicationContext).also { instance = it }
            }
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val etagCache = ConcurrentHashMap<String, String>()

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    var baseUrl: String
        get() {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return sp.getString(KEY_BASE_URL, "http://10.0.2.2:8088") ?: "http://10.0.2.2:8088"
        }
        set(value) {
            val cleaned = value.trim().removeSuffix("/")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, cleaned)
                .apply()
        }

    // 回调获取当前持久化的 Token
    var tokenProvider: (() -> String?)? = null

    fun resolveMediaUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val prefix = baseUrl
        val path = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return "$prefix$path"
    }

    private fun buildRequest(
        path: String,
        method: String = "GET",
        queryParams: Map<String, String>? = null,
        body: RequestBody? = null,
        useETag: Boolean = false
    ): Pair<Request, String> {
        val normPath = if (path.startsWith("/")) path else "/$path"
        val httpUrlBuilder = (baseUrl + normPath).let { url ->
            val builder = url.toHttpUrlOrNull()?.newBuilder()
                ?: throw IllegalArgumentException("Invalid URL: $url")
            queryParams?.forEach { (k, v) ->
                builder.addQueryParameter(k, v)
            }
            builder.build()
        }

        val requestBuilder = Request.Builder()
            .url(httpUrlBuilder)
            .header("X-Client-Platform", "android")
            .header("X-Client-Version", "1.1.0")

        val token = tokenProvider?.invoke()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
            requestBuilder.header("X-Device-Token", token)
        }

        val cacheKey = httpUrlBuilder.toString()
        if (useETag && method == "GET") {
            etagCache[cacheKey]?.let { etag ->
                requestBuilder.header("If-None-Match", etag)
            }
        }

        when (method) {
            "POST" -> requestBuilder.post(body ?: "".toRequestBody("application/json".toMediaType()))
            "PUT" -> requestBuilder.put(body ?: "".toRequestBody("application/json".toMediaType()))
            "DELETE" -> requestBuilder.delete(body)
            else -> requestBuilder.get()
        }

        return Pair(requestBuilder.build(), cacheKey)
    }

    private suspend inline fun <reified T> executeEnvelopeRequest(
        path: String,
        method: String = "GET",
        queryParams: Map<String, String>? = null,
        body: RequestBody? = null,
        useETag: Boolean = false
    ): T = withContext(Dispatchers.IO) {
        val (request, cacheKey) = buildRequest(path, method, queryParams, body, useETag)
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }

        response.use { resp ->
            if (resp.code == 304) {
                throw APIException(304, "数据未变更 (304)")
            }

            resp.header("ETag")?.let { newEtag ->
                if (newEtag.isNotBlank()) etagCache[cacheKey] = newEtag
            }

            val bodyString = resp.body?.string() ?: ""

            if (resp.code == 401) {
                throw APIException(401, "设备凭据未授权或已失效，请重新扫码配对")
            }

            val envelope = try {
                json.decodeFromString<APIEnvelope<T>>(bodyString)
            } catch (e: Exception) {
                if (!resp.isSuccessful) {
                    throw APIException(resp.code, "服务器响应异常: HTTP ${resp.code}")
                }
                throw APIException(-2, "数据解析失败: ${e.message}")
            }

            if (envelope.code !in 200..299) {
                throw APIException(envelope.code, envelope.message)
            }

            envelope.data ?: throw APIException(envelope.code, "业务返回数据为空")
        }
    }

    // MARK: - 1. 系统与探针

    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        val (request, _) = buildRequest("/api/health")
        try {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    // MARK: - 2. 鉴权与设备配对

    suspend fun confirmPairing(
        ticket: String,
        deviceName: String,
        deviceModel: String
    ): ConfirmPairingResponse {
        val req = ConfirmPairingRequest(
            ticket = ticket,
            deviceName = deviceName,
            platform = "android",
            deviceModel = deviceModel
        )
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/auth/pair/confirm", "POST", body = body)
    }

    suspend fun verifyMasterPasscode(
        passcode: String,
        deviceName: String
    ): VerifyPasscodeResponse {
        val req = VerifyPasscodeRequest(
            passcode = passcode,
            deviceName = deviceName,
            platform = "android",
            deviceModel = "Android"
        )
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/auth/verify", "POST", body = body)
    }

    // MARK: - 3. 站点配置与主理人档案

    suspend fun fetchSiteConfig(): SiteConfig {
        return executeEnvelopeRequest("/api/v1/config", "GET")
    }

    suspend fun fetchCuratorProfile(): CuratorProfile {
        return executeEnvelopeRequest("/api/v1/profile", "GET")
    }

    suspend fun updateCuratorProfile(profile: CuratorProfile): CuratorProfile {
        val body = json.encodeToString(profile).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/profile", "PUT", body = body)
    }

    // MARK: - 4. 生活档案 (Entries)

    suspend fun fetchEntries(
        page: Int = 1,
        pageSize: Int = 50,
        category: String? = null,
        year: Int? = null,
        since: String? = null
    ): EntriesListResponse {
        val params = mutableMapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString()
        )
        if (!category.isNullOrBlank() && category != "all") params["category"] = category
        if (year != null) params["year"] = year.toString()
        if (!since.isNullOrBlank()) params["since"] = since
        return executeEnvelopeRequest("/api/v1/entries", "GET", queryParams = params)
    }

    suspend fun createEntry(req: CreateEntryRequest): LifeEntry {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/entries", "POST", body = body)
    }

    suspend fun updateEntry(id: Long, req: CreateEntryRequest): LifeEntry {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/entries/$id", "PUT", body = body)
    }

    suspend fun deleteEntry(id: Long, purge: Boolean = false) {
        val params = if (purge) mapOf("purge" to "true") else null
        executeEnvelopeRequest<DeleteResponse>("/api/v1/entries/$id", "DELETE", queryParams = params)
    }

    // MARK: - 5. 记账 (Transactions)

    suspend fun fetchTransactions(
        year: Int? = null,
        month: Int? = null,
        since: String? = null,
        limit: Int = 200,
        offset: Int? = null
    ): List<Transaction> {
        val params = mutableMapOf("limit" to limit.toString())
        if (year != null) params["year"] = year.toString()
        if (month != null) params["month"] = month.toString()
        if (offset != null && offset > 0) params["offset"] = offset.toString()
        if (!since.isNullOrBlank()) params["since"] = since
        return executeEnvelopeRequest("/api/v1/transactions", "GET", queryParams = params)
    }

    suspend fun fetchTransactionSummary(year: Int? = null, month: Int? = null): TransactionSummary {
        val params = mutableMapOf<String, String>()
        if (year != null) params["year"] = year.toString()
        if (month != null) params["month"] = month.toString()
        return executeEnvelopeRequest("/api/v1/transactions/summary", "GET", queryParams = params)
    }

    suspend fun createTransaction(req: CreateTransactionRequest): Transaction {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/transactions", "POST", body = body)
    }

    suspend fun updateTransaction(id: Long, req: CreateTransactionRequest): Transaction {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/transactions/$id", "PUT", body = body)
    }

    suspend fun deleteTransaction(id: Long, purge: Boolean = false) {
        val params = if (purge) mapOf("purge" to "true") else null
        executeEnvelopeRequest<DeleteResponse>("/api/v1/transactions/$id", "DELETE", queryParams = params)
    }

    // MARK: - 6. 造物与心愿 (Projects & Wishes)

    suspend fun fetchProjects(): List<Project> {
        return executeEnvelopeRequest("/api/v1/projects", "GET")
    }

    suspend fun fetchWishes(): List<Wish> {
        return executeEnvelopeRequest("/api/v1/wishes", "GET")
    }

    suspend fun fetchFootprints(year: Int? = null): List<Footprint> {
        val params = if (year != null) mapOf("year" to year.toString()) else null
        return executeEnvelopeRequest("/api/v1/footprints", "GET", queryParams = params)
    }

    // MARK: - 7. 媒体直传 (R2 Presign & Upload)

    suspend fun presignUpload(req: PresignUploadRequest): PresignUploadResponse {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/uploads/presign", "POST", body = body)
    }

    suspend fun uploadToR2Direct(uploadUrl: String, fileBytes: ByteArray, mimeType: String) = withContext(Dispatchers.IO) {
        val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val putRequest = Request.Builder()
            .url(uploadUrl)
            .put(requestBody)
            .build()
        val response = okHttpClient.newCall(putRequest).execute()
        response.use {
            if (!it.isSuccessful) {
                throw APIException(it.code, "R2 存储直传失败: HTTP ${it.code}")
            }
        }
    }

    suspend fun completeUpload(req: CompleteUploadRequest): Attachment {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/uploads/complete", "POST", body = body)
    }

    suspend fun uploadUnifiedFallback(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String,
        folder: String = "entries"
    ): Attachment = withContext(Dispatchers.IO) {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("folder", folder)
            .addFormDataPart(
                "file",
                filename,
                fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val (request, _) = buildRequest("/api/v1/files/upload", "POST", body = multipartBody)
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            val bodyString = resp.body?.string() ?: ""
            val envelope = json.decodeFromString<APIEnvelope<UnifiedUploadResponse>>(bodyString)
            if (!resp.isSuccessful || envelope.code !in 200..299 || envelope.data == null) {
                throw APIException(envelope.code, envelope.message)
            }
            val res = envelope.data
            Attachment(
                id = res.attachmentId,
                objectKey = res.objectKey,
                originalFilename = res.filename,
                mimeType = res.mimeType,
                size = res.size,
                width = res.width,
                height = res.height,
                url = res.url
            )
        }
    }
}
