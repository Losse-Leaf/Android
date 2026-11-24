package com.looseleaf.app.ui.pairing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedPairingPayload(
    val hubUrl: String,
    val ticket: String
) {
    val code: String
        get() = ticket
}

object PairingPayloadParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 解析 OAuth2 回调深链（及兼容历史链接格式），提取服务器地址与一次性授权码。
     * 支持格式：
     * 1. 标准 OAuth2 回调深链: looseleaf://auth/callback?hub=https://leaf.starryheavens.uk&code=auth_code_xxx
     * 2. 兼容历史深链: looseleaf://pair?hub=https://leaf.starryheavens.uk&ticket=pair_tk_xxx
     * 3. 兼容 Web 链接 / JSON / 键值文本
     */
    fun parse(raw: String): ParsedPairingPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // 1. JSON 格式解析
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val element = json.parseToJsonElement(trimmed).jsonObject
                val code = element["code"]?.jsonPrimitive?.content
                    ?: element["ticket"]?.jsonPrimitive?.content
                    ?: element["ticket_str"]?.jsonPrimitive?.content
                val hub = element["hub_url"]?.jsonPrimitive?.content
                    ?: element["hub"]?.jsonPrimitive?.content
                if (!code.isNullOrBlank() && !hub.isNullOrBlank()) {
                    return ParsedPairingPayload(cleanHubUrl(hub), code.trim())
                }
            } catch (_: Exception) {}
        }

        // 2. 纯字符串参数截取（规避 JVM 单元测试时 android.net.Uri 桩代码异常）
        val codeParam = extractParam(trimmed, "code")
            ?: extractParam(trimmed, "ticket")
            ?: extractParam(trimmed, "tk")
        val hubParam = extractParam(trimmed, "hub") ?: extractParam(trimmed, "hub_url")

        if (!codeParam.isNullOrBlank()) {
            if (!hubParam.isNullOrBlank()) {
                return ParsedPairingPayload(cleanHubUrl(hubParam), codeParam.trim())
            }

            // 若链接本身就是 http/https 地址，直接截取其 origin
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val origin = extractOrigin(trimmed)
                if (!origin.isNullOrBlank()) {
                    return ParsedPairingPayload(cleanHubUrl(origin), codeParam.trim())
                }
            }
        }

        // 3. 正则容错匹配
        val codeRegex = Regex("""(?:code|ticket|tk)=([a-zA-Z0-9_-]+)""")
        val hubRegex = Regex("""(?:hub|hub_url)=(https?://[^\s&]+)""")

        val codeMatch = codeRegex.find(trimmed)?.groupValues?.get(1)
        val hubMatch = hubRegex.find(trimmed)?.groupValues?.get(1)

        if (codeMatch != null && hubMatch != null) {
            return ParsedPairingPayload(cleanHubUrl(hubMatch), codeMatch.trim())
        }

        return null
    }

    private fun extractParam(url: String, key: String): String? {
        val qIdx = url.indexOf('?')
        val queryStr = if (qIdx != -1) url.substring(qIdx + 1) else url
        val pairs = queryStr.split('&')
        for (p in pairs) {
            val parts = p.split('=', limit = 2)
            if (parts.size == 2 && parts[0].trim().equals(key, ignoreCase = true)) {
                return decode(parts[1].trim())
            }
        }
        return null
    }

    /** 回调 URL 参数可能是百分号编码的（https%3A%2F%2F…），必须先解码再校验协议 */
    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun extractOrigin(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return null
        val pathStart = url.indexOf('/', schemeEnd + 3)
        return if (pathStart != -1) url.substring(0, pathStart) else url
    }

    private fun cleanHubUrl(raw: String): String {
        return decode(raw).trim().removeSuffix("/")
    }
}
