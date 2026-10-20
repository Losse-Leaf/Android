package com.looseleaf.app.data.api

import android.content.Context
import android.content.SharedPreferences
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
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class APIException(val code: Int, override val message: String) : Exception("[$code] $message")

/**
 * MOB-P1-36：服务端权威增量游标的响应头名。
 *
 * 后端在增量响应里带回「本次这页记录的最大变动时间」（RFC3339 北京时间），
 * 客户端可以把它原样塞回 ?since=，不必再在本地把每条记录的时间戳解析一遍。
 */
const val SYNC_CURSOR_HEADER = "X-Sync-Cursor"

/**
 * MOB-P1-36：一次请求的响应元信息。
 *
 * 用可变对象而不是返回值，是因为请求管线（executeEnvelopeRequest）只返回业务数据，
 * 把响应头塞进返回值会污染所有调用方。
 */
class ResponseMeta {
    /** 服务端权威增量游标；老服务端不返回时为 null */
    @Volatile
    var syncCursor: String? = null
}

/**
 * MOB-P0-03：文件流式请求体。
 *
 * 原实现把整张照片 `File.readBytes()` 读进堆，再由 OkHttp 的
 * `toRequestBody` **再拷贝一份**进 Buffer，且多张并发 ⇒ 峰值内存翻倍。
 * 这里直接从磁盘以 64KB 缓冲流式写出，峰值内存恒定为缓冲区大小。
 */
fun fileRequestBody(file: File, mimeType: String): RequestBody = object : RequestBody() {
    override fun contentType(): MediaType? = mimeType.toMediaTypeOrNull()
    override fun contentLength(): Long = file.length()
    override fun writeTo(sink: BufferedSink) {
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
            }
        }
    }
}

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
        // MOB-P1-35：不再把「等于默认值的字段」原样写进请求体。
        //
        // 过去每一笔记账/每一条随笔都要带上十几个 null 字段，
        // 请求体与发件箱（outbox payload 存的就是这份请求体）一起膨胀。
        //
        // 前提是**服务端已把「省略」与「显式默认值」视为同一件事**
        // （后端 handlers 里 entry.type / transaction.type / wish.kind 等
        // 都已补上「省略即默认」的回落），因此这里可以安全地省略。
        encodeDefaults = false
    }

    /**
     * 设备凭据失效（HTTP 401）广播（AOPT-09b）。
     * extraBufferCapacity = 1 + tryEmit：后台同步与前台请求可能同时撞上 401，
     * 不阻塞调用方，也不丢「至少一次」的提示机会。
     */
    val unauthorizedEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * R2 预签名直传专用客户端：直传对象存储用于超大视频 / RAW 底片，
     * 90s 的 callTimeout 会在慢网下掐断合法上传，因此这里关闭整体超时。
     */
    private val uploadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            // MOB-P1-44：原先是「无限超时」—— 弱网下 1KB/s 的慢速上传会一直挂着，
            // 直到 WorkManager 的 10 分钟上限把它杀掉，然后**从 0 字节重传**，
            // R2 侧还会留下孤儿对象。这里给出略小于上限的整体超时（9 分钟），
            // 失败可以走退避重试，而不是被系统粗暴砍断。
            .callTimeout(9, TimeUnit.MINUTES)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedBaseUrl: String? = null

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
            // MOB-P1-38：补 HTTP 缓存 —— 原先每次冷启动/切 Tab 都要重新走网络拉
            // /entries、/profile、/config、/categories、/assets/overview，
            // 弱网下首屏白屏时间翻倍且白白浪费流量。
            .cache(Cache(File(context.cacheDir, "http_cache"), 32L * 1024 * 1024))

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

    /** MOB-P1-39：缓存 SharedPreferences 句柄，避免每次读 baseUrl 都重新解析 XML */
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() {
            cachedBaseUrl?.let { return it }
            val stored = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
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
        body: RequestBody? = null
    ): Request {
        val httpUrl = buildHubUrl(baseUrl, path, queryParams)

        val requestBuilder = Request.Builder()
            .url(httpUrl)

        val token = tokenProvider?.invoke()
        if (!token.isNullOrBlank()) {
            // docs/backend.md §六：两种请求头等效，只发一种即可，避免重复
            requestBuilder.header("X-Device-Token", token)
        }
        // 不再发送 X-Client-Platform / X-Client-Version：
        // docs/backlog.md RC-125 已规定移除后端未消费的这两个自定义头（避免 CORS 预检冲突，
        // 全端统一口径；Android 原生虽不走 CORS，但规格一致优先）

        // MOB-P1-40：原先这里会构造一个完整的 URL 字符串作为 cacheKey，
        // 但所有调用方都用 `val (request, _) = …` 丢弃它 —— 纯浪费，已移除。


        when (method) {
            "POST" -> requestBuilder.post(body ?: "".toRequestBody("application/json".toMediaType()))
            "PUT" -> requestBuilder.put(body ?: "".toRequestBody("application/json".toMediaType()))
            "DELETE" -> requestBuilder.delete(body)
            else -> requestBuilder.get()
        }

        return requestBuilder.build()
    }

    private suspend inline fun <reified T> executeEnvelopeRequest(
        path: String,
        method: String = "GET",
        queryParams: Map<String, String>? = null,
        body: RequestBody? = null,
        meta: ResponseMeta? = null
    ): T = withContext(Dispatchers.IO) {
        val request = buildRequest(path, method, queryParams, body)
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }

        // 「读响应体」同样可能抛 IOException（连接在读头后中断、读超时、callTimeout 触发）。
        // 必须统一转成 APIException(-1)，否则调用方的
        // 「网络不可达 → 入发件箱离线补发」分支（catch APIException）会被裸 IOException 穿透，
        // 造物/心愿这类用户输入就会既没创建成功、也没进队列，直接丢失。
        try {
            response.use { resp ->
                if (resp.code == 304) {
                    // 客户端不再发送 If-None-Match，服务端理论上不会返 304；留作兜底
                    throw APIException(304, "数据未变更 (304)")
                }

                // MOB-P1-36：抓取服务端权威增量游标（老服务端没有这个头，meta 保持 null）
                meta?.syncCursor = resp.header(SYNC_CURSOR_HEADER)

                val bodyString = resp.body?.string() ?: ""

                if (resp.code == 401) {
                    // 401 全局广播（AOPT-09b）：设备可能在 Web/Mac 端被吊销，
                    // MainActivity 订阅后弹「重新配对」引导；Room 里的 outbox 不清空，重配对后续传
                    unauthorizedEvents.tryEmit(Unit)
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
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
    }

    // MARK: - 1. 鉴权与设备配对

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

    /**
     * 拉取分类字典。服务端在传入 scope 时已按 scope 过滤
     * （backend/internal/handlers/categories.go），客户端不再二次 filter——
     * 重复过滤一旦后端语义变化就会静默丢数据。
     */
    suspend fun fetchCategories(scope: String? = null): List<CategoryOption> {
        val params = if (!scope.isNullOrBlank()) mapOf("scope" to scope) else null
        val resp: CategoriesResponse = executeEnvelopeRequest("/api/v1/categories", "GET", queryParams = params)
        return resp.items
    }

    suspend fun addCategoryOption(scope: String, name: String, sortOrder: Int? = null): CategoryOption {
        val req = CreateCategoryRequest(scope = scope, name = name, sortOrder = sortOrder)
        val body = json.encodeToString(req).toRequestBody("application/json".toMediaType())
        return executeEnvelopeRequest("/api/v1/categories", "POST", body = body)
    }

    // RC-142 分类字典减法：级联重命名（PUT）与重置预设（POST reset）已从全端规格中废除，
    // 客户端不再保留这两个无调用方的方法

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
        since: String? = null,
        meta: ResponseMeta? = null
    ): EntriesListResponse {
        val params = mutableMapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString()
        )
        if (!category.isNullOrBlank() && category != "all") params["category"] = category
        if (year != null) params["year"] = year.toString()
        if (!since.isNullOrBlank()) params["since"] = since
        return executeEnvelopeRequest("/api/v1/entries", "GET", queryParams = params, meta = meta)
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
        offset: Int? = null,
        meta: ResponseMeta? = null
    ): List<Transaction> {
        val params = mutableMapOf("limit" to limit.toString())
        if (year != null) params["year"] = year.toString()
        if (month != null) params["month"] = month.toString()
        if (offset != null && offset > 0) params["offset"] = offset.toString()
        if (!since.isNullOrBlank()) params["since"] = since
        return executeEnvelopeRequest("/api/v1/transactions", "GET", queryParams = params, meta = meta)
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

    /**
     * MOB-P0-03：文件流式直传（推荐路径）。
     * 照片不再整份进入堆内存，峰值内存与文件大小无关。
     */
    suspend fun uploadToR2Direct(uploadUrl: String, file: File, mimeType: String) =
        uploadToR2Direct(uploadUrl, fileRequestBody(file, mimeType))

    suspend fun uploadToR2Direct(uploadUrl: String, fileBytes: ByteArray, mimeType: String) =
        uploadToR2Direct(uploadUrl, fileBytes.toRequestBody(mimeType.toMediaTypeOrNull()))

    /** MOB-P0-03：核心直传入口 —— 请求体由调用方决定（内存字节 or 文件流） */
    suspend fun uploadToR2Direct(uploadUrl: String, body: RequestBody) = withContext(Dispatchers.IO) {
        val putRequest = Request.Builder()
            .url(uploadUrl)
            .put(body)
            .build()
        executeR2Put(putRequest)
    }

    private suspend fun executeR2Put(putRequest: Request) = withContext(Dispatchers.IO) {
        // 直传对象存储用于超大视频 / RAW 底片，不能用主客户端 90s 的 callTimeout
        val response = try {
            uploadClient.newCall(putRequest).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
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

    /**
     * MOB-P0-03：文件流式中转上传（推荐路径）。
     */
    suspend fun uploadUnifiedFallback(
        file: File,
        filename: String,
        mimeType: String,
        folder: String = "entries"
    ): Attachment = uploadUnifiedFallback(
        body = fileRequestBody(file, mimeType),
        filename = filename,
        mimeType = mimeType,
        folder = folder
    )

    suspend fun uploadUnifiedFallback(
        fileBytes: ByteArray,
        filename: String,
        mimeType: String,
        folder: String = "entries"
    ): Attachment = uploadUnifiedFallback(
        body = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull()),
        filename = filename,
        mimeType = mimeType,
        folder = folder
    )

    /** MOB-P0-03：核心中转上传入口 —— 请求体由调用方决定（内存字节 or 文件流） */
    suspend fun uploadUnifiedFallback(
        body: RequestBody,
        filename: String,
        mimeType: String,
        folder: String = "entries"
    ): Attachment = withContext(Dispatchers.IO) {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("folder", folder)
            .addFormDataPart("file", filename, body)
            .build()
        executeUnifiedUpload(multipartBody)
    }

    private suspend fun executeUnifiedUpload(multipartBody: MultipartBody): Attachment = withContext(Dispatchers.IO) {

        val request = buildRequest("/api/v1/files/upload", "POST", body = multipartBody)
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
        try {
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
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
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
        val request = buildRequest("/api/v1/system/backup/download/$filename", "GET")
        val response = try {
            downloadClient.newCall(request).execute()
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
        try {
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
        } catch (e: IOException) {
            throw APIException(-1, "网络连接异常: ${e.message}")
        }
    }
}

/**
 * 构建中枢 API 的完整 URL（顶层纯函数，便于单元测试）。
 *
 * 路径必须「逐段 addPathSegment」而不是整串 toHttpUrlOrNull()：
 * 后者对含中文/空格等未编码字符的 URL 直接返回 null（备份文件名等场景）。
 * 也绝不能用 encodedPath("") 清空已解析路径——OkHttp 要求它以 "/" 开头，
 * 传空串会抛 IllegalArgumentException（v1.5.0 的全量断连回归即源于此）。
 */
internal fun buildHubUrl(
    baseUrl: String,
    path: String,
    queryParams: Map<String, String>? = null
): HttpUrl {
    val base = baseUrl.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid base URL: $baseUrl")
    val builder = base.newBuilder()
    val normPath = if (path.startsWith("/")) path else "/$path"
    normPath.split('/').filter { it.isNotEmpty() }.forEach { segment ->
        builder.addPathSegment(segment)
    }
    queryParams?.forEach { (k, v) ->
        builder.addQueryParameter(k, v)
    }
    return builder.build()
}
