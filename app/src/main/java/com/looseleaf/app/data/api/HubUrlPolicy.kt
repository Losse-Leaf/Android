package com.looseleaf.app.data.api

import java.net.URI

/**
 * 中枢地址准入策略 (Hub URL Policy)
 *
 * 背景：App 允许局域网自建中枢（`http://192.168.x.x:8088`），但**公网地址必须走 HTTPS**。
 * 否则扫码/深链可以把设备凭据（Device Token）在明文 HTTP 上送出。
 *
 * 这里只做「字面量判定」，不做 DNS 解析——避免把主机名解析成内网地址绕过策略，
 * 也避免在 UI 线程产生网络请求。
 */
object HubUrlPolicy {

    sealed class Verdict {
        data class Allowed(val url: String) : Verdict()
        data class Rejected(val reason: String) : Verdict()
    }

    /** 归一化：去空白、去末尾斜杠。保留原始 scheme，不做协议补全。 */
    fun normalize(raw: String): String = raw.trim().removeSuffix("/")

    fun validate(raw: String): Verdict {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return Verdict.Rejected("中枢地址为空")

        val uri = try {
            URI(normalized)
        } catch (_: Exception) {
            return Verdict.Rejected("中枢地址格式不合法")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return Verdict.Rejected("只支持 http/https 协议")
        }
        val host = uri.host?.lowercase()
        if (host.isNullOrBlank()) return Verdict.Rejected("中枢地址缺少主机名")

        if (scheme == "https") return Verdict.Allowed(normalized)

        // 明文 HTTP 仅允许本机 / 局域网 / mDNS
        return if (isLocalOrPrivateHost(host)) {
            Verdict.Allowed(normalized)
        } else {
            Verdict.Rejected("公网地址必须使用 HTTPS，请改用 https:// 或连接局域网中枢")
        }
    }

    fun isLocalOrPrivateHost(rawHost: String): Boolean {
        val host = rawHost.removePrefix("[").removeSuffix("]").lowercase()
        if (host == "localhost" || host == "::1" || host == "0.0.0.0") return true
        if (host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        // IPv6 链路本地 / 唯一本地地址
        if (host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) return true
        return isPrivateIpv4(host)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        val nums = parts.map { it.toIntOrNull() ?: return false }
        if (nums.any { it !in 0..255 }) return false
        return when {
            nums[0] == 10 -> true
            nums[0] == 127 -> true
            nums[0] == 172 && nums[1] in 16..31 -> true
            nums[0] == 192 && nums[1] == 168 -> true
            nums[0] == 169 && nums[1] == 254 -> true
            // 100.64.0.0/10：运营商级 NAT 地址段，Tailscale 等组网工具也用它
            nums[0] == 100 && nums[1] in 64..127 -> true
            else -> false
        }
    }
}
