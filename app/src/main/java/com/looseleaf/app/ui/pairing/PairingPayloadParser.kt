package com.looseleaf.app.ui.pairing

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ParsedPairingPayload(
    val hubUrl: String,
    val ticket: String
)

object PairingPayloadParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 智能从二维码内容或配对链接中提取中枢 Hub URL 与 Ticket
     * 支持格式：
     * 1. 散页官方协议: looseleaf://pair?hub=http://192.168.1.5:8088&ticket=pair_tk_123456
     * 2. Web 展厅深链: http://192.168.1.5:8088/pair?ticket=pair_tk_123456
     * 3. 结构化 JSON: {"hub_url": "...", "ticket": "..."}
     * 4. 纯键值文本: hub=http://...&ticket=...
     */
    fun parse(raw: String): ParsedPairingPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // 1. JSON 格式解析
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val element = json.parseToJsonElement(trimmed).jsonObject
                val ticket = element["ticket"]?.jsonPrimitive?.content
                    ?: element["ticket_str"]?.jsonPrimitive?.content
                val hub = element["hub_url"]?.jsonPrimitive?.content
                    ?: element["hub"]?.jsonPrimitive?.content
                if (!ticket.isNullOrBlank() && !hub.isNullOrBlank()) {
                    return ParsedPairingPayload(cleanHubUrl(hub), ticket.trim())
                }
            } catch (_: Exception) {}
        }

        // 2. 纯字符串参数截取（规避 JVM 单元测试时 android.net.Uri 桩代码异常）
        val ticketParam = extractParam(trimmed, "ticket") ?: extractParam(trimmed, "tk")
        val hubParam = extractParam(trimmed, "hub") ?: extractParam(trimmed, "hub_url")

        if (!ticketParam.isNullOrBlank()) {
            if (!hubParam.isNullOrBlank()) {
                return ParsedPairingPayload(cleanHubUrl(hubParam), ticketParam.trim())
            }

            // 若链接本身就是 http/https 地址，直接截取其 origin
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                val origin = extractOrigin(trimmed)
                if (!origin.isNullOrBlank()) {
                    return ParsedPairingPayload(cleanHubUrl(origin), ticketParam.trim())
                }
            }
        }

        // 3. 正则强力容错匹配
        val ticketRegex = Regex("""(?:ticket|tk)=([a-zA-Z0-9_-]+)""")
        val hubRegex = Regex("""(?:hub|hub_url)=(https?://[^\s&]+)""")

        val ticketMatch = ticketRegex.find(trimmed)?.groupValues?.get(1)
        val hubMatch = hubRegex.find(trimmed)?.groupValues?.get(1)

        if (ticketMatch != null && hubMatch != null) {
            return ParsedPairingPayload(cleanHubUrl(hubMatch), ticketMatch.trim())
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
                return parts[1].trim()
            }
        }
        return null
    }

    private fun extractOrigin(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd == -1) return null
        val pathStart = url.indexOf('/', schemeEnd + 3)
        return if (pathStart != -1) url.substring(0, pathStart) else url
    }

    private fun cleanHubUrl(raw: String): String {
        return raw.trim().removeSuffix("/")
    }
}
