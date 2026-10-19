package com.looseleaf.app.data.api

import android.content.Context
import android.util.Log
import com.looseleaf.app.BuildConfig
import com.looseleaf.app.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        private const val DEFAULT_BASE_URL = "http://10.0.2.2:8088"

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

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 防止「卡住但不报错」的连接长期占用协程
            .callTimeout(90, TimeUnit.SECONDS)

        // release 包绝不打印请求日志（URL 里含对象键、设备查询串）
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
        }
        builder.build()
    }

    /**
     * 备份下载专用客户端：整体 callTimeout 关闭、读超时放宽。
     * 主客户端 90s 的 callTimeout 会把大体积快照（几百 MB）在慢网下直接掐断。
     */
    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private var cachedBaseUrl: String? = null

    var baseUrl: String
        get() {
            cachedBaseUrl?.let { return it }
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = sp.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
            cachedBaseUrl = stored
            return stored
        }
        set(value) {
            val cleaned = when (val verdict = HubUrlPolicy.validate(value)) {
                is HubUrlPolicy.Verdict.Allowed -> verdict.url
                is HubUrlPolicy.Verdict.Rejected -> throw IllegalArgumentException(verdict.reason)
            }
            cachedBaseUrl = cleaned
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
        if (
            trimmed.startsWith("http://") ||
            trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") ||
            trimmed.startsWith("content://")
        ) {
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

    suspend fun updateDeviceName(deviceId: String, name: String): DeviceInfo {
        val req = UpdateDeviceNameRequest(name = name)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/auth/devices/$deviceId", "PUT", body = body)
    }

    suspend fun revokeDevice(deviceId: String) {
        executeEnvelopeRequest<DeleteResponse>("/api/v1/auth/devices/$deviceId", "DELETE")
    }

    suspend fun fetchTrustedDevices(): List<TrustedDevice> {
        return executeEnvelopeRequest("/api/v1/auth/devices", "GET")
    }

    suspend fun initPairing(hubUrl: String? = null): PairTicketResponse {
        val req = buildJsonObject {
            if (!hubUrl.isNullOrBlank()) {
                put("hub_url", hubUrl)
            }
        }
        val body = req.toString().toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/auth/pair/init", "POST", body = body)
    }

    // MARK: - 3. 站点配置与主理人档案

    suspend fun fetchSiteConfig(): SiteConfig {
        return executeEnvelopeRequest("/api/v1/config", "GET")
    }

    suspend fun fetchCuratorProfile(): CuratorProfile {
        return executeEnvelopeRequest("/api/v1/profile", "GET")
    }

    suspend fun updateCuratorProfile(req: UpdateProfileRequest): CuratorProfile {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/profile", "PUT", body = body)
    }

    suspend fun fetchCategories(scope: String? = null): List<CategoryOption> {
        val params = if (!scope.isNullOrBlank()) mapOf("scope" to scope) else null
        val resp: CategoriesResponse = executeEnvelopeRequest("/api/v1/categories", "GET", queryParams = params)
        return if (!scope.isNullOrBlank()) resp.items.filter { it.scope == scope } else resp.items
    }

    suspend fun fetchCategoriesResponse(scope: String? = null): CategoriesResponse {
        val params = if (!scope.isNullOrBlank()) mapOf("scope" to scope) else null
        return executeEnvelopeRequest("/api/v1/categories", "GET", queryParams = params)
    }

    suspend fun addCategoryOption(scope: String, name: String, sortOrder: Int? = null): CategoryOption {
        val req = CreateCategoryRequest(scope = scope, name = name, sortOrder = sortOrder)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/categories", "POST", body = body)
    }

    suspend fun updateCategoryOption(id: Long, name: String, sortOrder: Int? = null, renameRecords: Boolean = true): CategoryOption {
        val req = UpdateCategoryRequest(name = name, sortOrder = sortOrder, renameRecords = renameRecords)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/categories/$id", "PUT", body = body)
    }

    suspend fun resetCategories(scope: String = ""): CategoriesResponse {
        val req = ResetCategoriesRequest(scope = scope)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/categories/reset", "POST", body = body)
    }

    suspend fun deleteCategoryOption(id: Long) {
        executeEnvelopeRequest<DeleteResponse>("/api/v1/categories/$id", "DELETE")
    }

    suspend fun searchGlobalArchive(query: String, limit: Int = 20): GlobalSearchResponse {
        val params = mapOf("q" to query.trim(), "limit" to limit.toString())
        return executeEnvelopeRequest("/api/v1/search", "GET", queryParams = params)
    }

    // MARK: - 4. 生活档案 (Entries) & 那年今日 & 年度年鉴

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

    suspend fun fetchOnThisDay(month: Int? = null, day: Int? = null): OnThisDayResponse {
        val params = mutableMapOf<String, String>()
        if (month != null) params["month"] = month.toString()
        if (day != null) params["day"] = day.toString()
        return executeEnvelopeRequest("/api/v1/entries/on-this-day", "GET", queryParams = params.ifEmpty { null })
    }

    suspend fun fetchAlmanacSummary(year: Int): AlmanacSummaryResponse {
        val params = mapOf("year" to year.toString())
        return executeEnvelopeRequest("/api/v1/almanac/summary", "GET", queryParams = params)
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

    // MARK: - 5. 记账 (Transactions) & 资产大盘 (Assets) & 分期渠道

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

    suspend fun fetchAssetsOverview(): AssetsOverview {
        return executeEnvelopeRequest("/api/v1/assets/overview", "GET")
    }

    suspend fun updateLiquidBalance(cents: Long): LiquidBalanceResponse {
        val req = SetLiquidBalanceRequest(initialLiquidBalance = cents)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/assets/liquid-balance", "PUT", body = body)
    }

    suspend fun addGearAsset(req: AddGearAssetRequest): GearItem {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/assets/gear", "POST", body = body)
    }

    suspend fun sellGearAsset(id: String, req: SellGearRequest): GearItem {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/assets/gear/$id/sell", "POST", body = body)
    }

    suspend fun deleteGearAsset(id: String) {
        executeEnvelopeRequest<DeleteResponse>("/api/v1/assets/gear/$id", "DELETE")
    }

    suspend fun fetchCreditChannels(): List<CreditChannel> {
        return executeEnvelopeRequest("/api/v1/credit-channels", "GET")
    }

    suspend fun createCreditChannel(req: CreateCreditChannelRequest): CreditChannel {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/credit-channels", "POST", body = body)
    }

    suspend fun updateCreditChannel(id: Long, req: CreateCreditChannelRequest): CreditChannel {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/credit-channels/$id", "PUT", body = body)
    }

    suspend fun deleteCreditChannel(id: Long) {
        executeEnvelopeRequest<DeleteResponse>("/api/v1/credit-channels/$id", "DELETE")
    }

    // MARK: - 6. 造物、心愿与山川足迹 (Projects, Wishes & Footprints)

    suspend fun fetchProjects(): List<Project> {
        return executeEnvelopeRequest("/api/v1/projects", "GET")
    }

    suspend fun createProject(req: CreateProjectRequest): Project {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/projects", "POST", body = body)
    }

    suspend fun updateProject(id: Long, req: CreateProjectRequest): Project {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/projects/$id", "PUT", body = body)
    }

    suspend fun deleteProject(id: Long, purge: Boolean = false) {
        val params = if (purge) mapOf("purge" to "true") else null
        executeEnvelopeRequest<DeleteResponse>("/api/v1/projects/$id", "DELETE", queryParams = params)
    }

    suspend fun fetchWishes(
        page: Int = 1,
        pageSize: Int = 100,
        kind: String? = null,
        status: String? = null
    ): List<Wish> {
        val params = mutableMapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString()
        )
        if (!kind.isNullOrBlank() && kind != "all") params["kind"] = kind
        if (!status.isNullOrBlank() && status != "all") params["status"] = status
        val resp: WishesListResponse = executeEnvelopeRequest("/api/v1/wishes", "GET", queryParams = params)
        return resp.wishes
    }

    suspend fun createWish(req: CreateWishRequest): Wish {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/wishes", "POST", body = body)
    }

    suspend fun updateWish(id: Long, req: CreateWishRequest): Wish {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/wishes/$id", "PUT", body = body)
    }

    suspend fun resolveWish(id: Long, req: ResolveWishRequest): Wish {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/wishes/$id/resolve", "POST", body = body)
    }

    suspend fun deleteWish(id: Long, purge: Boolean = false) {
        val params = if (purge) mapOf("purge" to "true") else null
        executeEnvelopeRequest<DeleteResponse>("/api/v1/wishes/$id", "DELETE", queryParams = params)
    }

    suspend fun fetchFootprints(year: Int? = null): List<Footprint> {
        val params = if (year != null) mapOf("year" to year.toString()) else null
        return executeEnvelopeRequest("/api/v1/footprints", "GET", queryParams = params)
    }

    suspend fun createFootprint(req: CreateFootprintRequest): Footprint {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/footprints", "POST", body = body)
    }

    suspend fun updateFootprint(id: Long, req: CreateFootprintRequest): Footprint {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/footprints/$id", "PUT", body = body)
    }

    suspend fun deleteFootprint(id: Long, purge: Boolean = false) {
        val params = if (purge) mapOf("purge" to "true") else null
        executeEnvelopeRequest<DeleteResponse>("/api/v1/footprints/$id", "DELETE", queryParams = params)
    }

    suspend fun fetchCityMemoryDrawer(cityCode: String): CityMemoryDrawerResponse {
        return executeEnvelopeRequest("/api/v1/footprints/cities/$cityCode/drawer", "GET")
    }

    suspend fun fetchProvinceMemoryDrawer(adcode: String): ProvinceMemoryDrawerResponse {
        return executeEnvelopeRequest("/api/v1/footprints/provinces/$adcode/drawer", "GET")
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
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
        response.use { resp ->
            val bodyString = resp.body?.string() ?: ""
            val envelope = try {
                json.decodeFromString<APIEnvelope<UnifiedUploadResponse>>(bodyString)
            } catch (e: Exception) {
                throw APIException(resp.code, "文件上传响应解析失败: ${e.message}")
            }
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

    suspend fun fetchGlobalStats(): GlobalStats {
        return executeEnvelopeRequest("/api/v1/stats", "GET")
    }

    // MARK: - 8. 对象存储配置与全量快照灾备 (Storage & Backup)

    suspend fun fetchStorageStatus(): StorageStatus {
        return executeEnvelopeRequest("/api/v1/storage/status", "GET")
    }

    suspend fun testStorageConfig(req: StorageConfigRequest): StorageTestResponse {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/storage/test", "POST", body = body)
    }

    suspend fun updateStorageConfig(req: StorageConfigRequest): StorageStatus {
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/storage/config", "PUT", body = body)
    }

    suspend fun resetStorageConfig(): StorageStatus {
        return executeEnvelopeRequest("/api/v1/storage/config", "DELETE")
    }

    suspend fun migrateStorageMedia(): StorageMigrateResponse {
        return executeEnvelopeRequest("/api/v1/storage/migrate", "POST")
    }

    suspend fun fetchBackupList(): List<BackupItem> {
        return executeEnvelopeRequest("/api/v1/system/backup/list", "GET")
    }

    suspend fun createBackupNow(): BackupItem {
        return executeEnvelopeRequest("/api/v1/system/backup/now", "POST")
    }

    suspend fun deleteBackup(filename: String): List<BackupItem> {
        return executeEnvelopeRequest("/api/v1/system/backup/$filename", "DELETE")
    }

    suspend fun restoreBackup(filename: String): RestoreBackupResponse {
        val req = RestoreBackupRequest(filename = filename)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/system/backup/restore", "POST", body = body)
    }

    /**
     * 流式下载全量快照到调用方提供的 OutputStream。
     * 不再把整个 ZIP 读进 ByteArray——大备份（含媒体元数据）会直接把设备打爆。
     */
    suspend fun downloadBackupTo(
        filename: String,
        outputStream: java.io.OutputStream
    ): Long = withContext(Dispatchers.IO) {
        val (request, _) = buildRequest("/api/v1/system/backup/download/$filename", "GET")
        val response = try {
            downloadClient.newCall(request).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
        response.use { res ->
            if (!res.isSuccessful) {
                throw APIException(res.code, "下载备份失败 (HTTP ${res.code})")
            }
            val body = res.body ?: throw APIException(-1, "备份文件数据为空")
            body.byteStream().use { input ->
                input.copyTo(outputStream, bufferSize = 64 * 1024)
            }
            outputStream.flush()
            body.contentLength().takeIf { it > 0 } ?: 0L
        }
    }
}
