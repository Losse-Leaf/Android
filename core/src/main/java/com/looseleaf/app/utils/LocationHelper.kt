package com.looseleaf.app.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 散页地理定位与属地行政区划推断中枢 (LocationHelper)
 * 严格遵循 docs/backend.md §一.5 与主理人设计规范：
 * - 默认主动请求并解析用户所在物理位置
 * - 自动逆地理编码反查标准地级行政区划与周边地标候选
 * - 地点提供点选芯片，避免强制手工输入
 */
object LocationHelper {

    private const val LOCATION_TIMEOUT_MS = 8_000L
    private const val GEOCODE_TIMEOUT_MS = 5_000L
    private const val LAST_KNOWN_MAX_AGE_MS = 5 * 60_000L

    private val PURE_NUMBER_OR_HOUSE_REGEX = Regex("""^[\d\s\-—#号楼室单元层/.]+$""")

    data class ResolvedLocation(
        val locationName: String,
        val latitude: Double,
        val longitude: Double
    )

    /**
     * 检查是否已授予精细或粗略定位权限
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * 获取当前物理位置并进行逆地理编码与城市推断
     */
    suspend fun getCurrentLocation(context: Context): ResolvedLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val lastKnown = getBestLastKnownLocation(locationManager)
        val isStale = lastKnown == null ||
            System.currentTimeMillis() - lastKnown.time > LAST_KNOWN_MAX_AGE_MS

        val location = if (isStale) {
            // 单次定位可能永远等不到回调（室内 / GPS 未锁定），必须设超时
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                requestSingleLocationUpdate(locationManager)
            } ?: lastKnown
        } else {
            lastKnown
        } ?: return@withContext null

        resolveLocationDetails(context, location)
    }

    private fun getBestLastKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val candidates = mutableListOf<Location>()
        for (provider in providers) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue
                val loc = locationManager.getLastKnownLocation(provider) ?: continue
                if (loc.isMockLocation()) continue
                candidates.add(loc)
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        // 同一时间片内取精度更高的那个；不同时间取更新的
        return candidates.maxWithOrNull(compareBy<Location>({ it.time }, { -it.accuracy }))
    }

    private fun Location.isMockLocation(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun requestSingleLocationUpdate(locationManager: LocationManager): Location? =
        suspendCancellableCoroutine { cont ->
            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }

                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        try {
                            locationManager.removeUpdates(this)
                        } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(loc)
                    }
                    override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                    override fun onProviderEnabled(p: String) {}
                    override fun onProviderDisabled(p: String) {}
                }

                locationManager.requestSingleUpdate(provider, listener, null)
                cont.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (_: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    /**
     * 将经纬度坐标（如照片 EXIF GPS）异步逆地理编码为易读地点名称。
     * 先尝试系统 Geocoder；若设备无 GMS/网络逆地理服务或返回为空，自动回退至 OpenStreetMap Nominatim。
     * 无效坐标（如 0.0, 0.0）或均无结果时返回 null。
     */
    suspend fun resolveCoordinates(
        context: Context,
        latitude: Double,
        longitude: Double
    ): ResolvedLocation? = withContext(Dispatchers.IO) {
        if (!isValidCoordinates(latitude, longitude)) return@withContext null

        val fromSystemGeocoder = if (Geocoder.isPresent()) {
            val addresses = withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                fetchAddresses(context, latitude, longitude)
            }.orEmpty()
            formatLocationName(addresses)
        } else {
            ""
        }

        val locationName = if (fromSystemGeocoder.isNotBlank()) {
            fromSystemGeocoder
        } else {
            fetchNominatimLocationName(latitude, longitude)
        }
        if (locationName.isBlank()) return@withContext null

        ResolvedLocation(
            locationName = locationName,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun fetchNominatimLocationName(latitude: Double, longitude: Double): String {
        return runCatching {
            val endpoint = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&accept-language=zh-CN&zoom=16",
                latitude,
                longitude
            )
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "LooseLeaf-Android/1.0")
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching ""
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseNominatimLocationName(body)
            } finally {
                conn.disconnect()
            }
        }.getOrDefault("")
    }

    internal fun parseNominatimLocationName(jsonBody: String): String {
        if (jsonBody.isBlank()) return ""
        return runCatching {
            val root = Json.parseToJsonElement(jsonBody).jsonObject
            val address = root["address"]?.jsonObject ?: return@runCatching ""

            fun firstAddr(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { k ->
                    address[k]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                }

            val adminArea = firstAddr("state", "province", "region")
            val locality = firstAddr("city", "municipality", "town", "state_district")
            val subLocality = firstAddr("city_district", "district", "county", "borough", "suburb")
            val thoroughfare = firstAddr("road", "pedestrian", "street")
            val featureName = root["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                ?: firstAddr("amenity", "tourism", "leisure", "building", "shop", "neighbourhood", "quarter")

            formatLocationNameFromParts(
                adminArea = adminArea,
                locality = locality,
                subLocality = subLocality,
                thoroughfare = thoroughfare,
                featureName = featureName
            )
        }.getOrDefault("")
    }

    fun isValidCoordinates(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        if (kotlin.math.abs(latitude) < 1e-6 && kotlin.math.abs(longitude) < 1e-6) return false
        return true
    }

    private suspend fun fetchAddresses(
        context: Context,
        latitude: Double,
        longitude: Double
    ): List<Address> {
        val geocoder = Geocoder(context, Locale.CHINA)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        3,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(emptyList())
                            }
                        }
                    )
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(emptyList())
                }
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 3).orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 将 Location 经纬度逆编码为候选地点名称
     */
    fun resolveLocationDetails(context: Context, location: Location): ResolvedLocation {
        val lat = location.latitude
        val lng = location.longitude
        val addresses = try {
            if (Geocoder.isPresent() && isValidCoordinates(lat, lng)) {
                val geocoder = Geocoder(context, Locale.CHINA)
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 3).orEmpty()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }

        val primaryLocation = formatLocationName(addresses)

        return ResolvedLocation(
            locationName = primaryLocation,
            latitude = lat,
            longitude = lng
        )
    }

    private fun formatLocationName(addresses: List<Address>): String {
        val primary = addresses.firstOrNull() ?: return ""
        return formatLocationNameFromParts(
            adminArea = primary.adminArea,
            locality = primary.locality,
            subLocality = primary.subLocality,
            thoroughfare = primary.thoroughfare,
            featureName = primary.featureName
        )
    }

    fun formatLocationNameFromParts(
        adminArea: String?,
        locality: String?,
        subLocality: String?,
        thoroughfare: String?,
        featureName: String?
    ): String {
        val cleanAdmin = adminArea?.trim()?.takeIf { it.isNotEmpty() }
        val cleanLocality = locality?.trim()?.takeIf { it.isNotEmpty() } ?: cleanAdmin
        val cleanSubLocality = subLocality?.trim()?.takeIf { it.isNotEmpty() && it != cleanLocality }
        val cleanThoroughfare = thoroughfare?.trim()?.takeIf {
            isMeaningfulPlacePart(it) && it != cleanLocality && it != cleanSubLocality
        }
        val cleanFeature = featureName?.trim()?.takeIf {
            isMeaningfulPlacePart(it) &&
                it != cleanLocality &&
                it != cleanSubLocality &&
                it != cleanAdmin
        }

        val detail = cleanFeature ?: cleanThoroughfare ?: cleanSubLocality
        return when {
            cleanLocality != null && detail != null -> {
                if (detail.startsWith(cleanLocality)) detail else "$cleanLocality · $detail"
            }
            detail != null -> detail
            cleanLocality != null -> cleanLocality
            else -> ""
        }
    }

    private fun isMeaningfulPlacePart(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        if (trimmed.equals("Unnamed Road", ignoreCase = true)) return false
        if (PURE_NUMBER_OR_HOUSE_REGEX.matches(trimmed)) return false
        return true
    }
}
