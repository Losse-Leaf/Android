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
import kotlinx.coroutines.suspendCancellableCoroutine
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

    data class ResolvedLocation(
        val cityCode: String?,
        val cityName: String?,
        val locationName: String,
        val candidatePlaces: List<String>,
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
     * 常用生活空间常去候选点（无网络或首次使用时的优雅纸墨预置）
     */
    val commonScenePresets = listOf(
        "家", "工作室", "咖啡馆", "书房", "街巷", "公园", "旅途", "茶室"
    )

    /**
     * 获取当前物理位置并进行逆地理编码与城市推断
     */
    suspend fun getCurrentLocation(context: Context): ResolvedLocation? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext null

        val location = getBestLastKnownLocation(locationManager)
            ?: requestSingleLocationUpdate(locationManager)
            ?: return@withContext null

        resolveLocationDetails(context, location)
    }

    private fun getBestLastKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider) ?: continue
                    if (bestLocation == null || loc.time > bestLocation.time) {
                        bestLocation = loc
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return bestLocation
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
     * 将 Location 经纬度逆编码为城市代码与候选地标
     */
    fun resolveLocationDetails(context: Context, location: Location): ResolvedLocation {
        val lat = location.latitude
        val lng = location.longitude
        var cityName: String? = null
        var cityCode: String? = null
        val candidates = mutableListOf<String>()

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.CHINA)
                val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+ 支持同步或异步，在 IO 线程同步调用较安全
                    geocoder.getFromLocation(lat, lng, 3)
                } else {
                    geocoder.getFromLocation(lat, lng, 3)
                }

                if (!addresses.isNullOrEmpty()) {
                    val primary = addresses.first()
                    val locality = primary.locality?.takeIf { it.isNotBlank() }
                        ?: primary.adminArea?.takeIf { it.isNotBlank() }
                    val subLocality = primary.subLocality?.takeIf { it.isNotBlank() }
                    val feature = primary.featureName?.takeIf { it.isNotBlank() }
                    val thoroughfare = primary.thoroughfare?.takeIf { it.isNotBlank() }

                    if (!locality.isNullOrBlank()) {
                        cityName = locality
                        cityCode = CityDictionary.codeForLocation(locality)
                    }

                    // 组织候选地标（优先具体地标、街道、区县）
                    if (!feature.isNullOrBlank() && feature != locality && feature != subLocality) {
                        candidates.add(feature)
                    }
                    if (!thoroughfare.isNullOrBlank() && thoroughfare != feature) {
                        candidates.add(thoroughfare)
                    }
                    if (!subLocality.isNullOrBlank() && !feature.isNullOrBlank() && feature != subLocality) {
                        candidates.add("$subLocality · $feature")
                    }
                    if (!subLocality.isNullOrBlank()) {
                        candidates.add(subLocality)
                    }
                    if (!locality.isNullOrBlank()) {
                        candidates.add(locality)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 离线或逆地理失败兜底：使用 CityDictionary 经纬度最近城市
        if (cityCode.isNullOrBlank()) {
            val nearest = CityDictionary.nearestCityForCoordinates(lat, lng)
            if (nearest != null) {
                cityCode = nearest.first
                cityName = nearest.second
                if (!candidates.contains(nearest.second)) {
                    candidates.add(nearest.second)
                }
            }
        }

        val primaryLocation = candidates.firstOrNull() ?: cityName ?: ""

        return ResolvedLocation(
            cityCode = cityCode,
            cityName = cityName,
            locationName = primaryLocation,
            candidatePlaces = candidates.distinct(),
            latitude = lat,
            longitude = lng
        )
    }
}
