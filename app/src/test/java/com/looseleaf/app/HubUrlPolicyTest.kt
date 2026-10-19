package com.looseleaf.app

import com.looseleaf.app.data.api.HubUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubUrlPolicyTest {

    @Test
    fun testHttpsAlwaysAllowed() {
        val verdict = HubUrlPolicy.validate("https://archive.example.com")
        assertTrue(verdict is HubUrlPolicy.Verdict.Allowed)
        assertEquals("https://archive.example.com", (verdict as HubUrlPolicy.Verdict.Allowed).url)
    }

    @Test
    fun testPublicHttpRejected() {
        // 公网明文会把 Device Token 暴露给中间人
        assertTrue(HubUrlPolicy.validate("http://looseleaf.example.com") is HubUrlPolicy.Verdict.Rejected)
        assertTrue(HubUrlPolicy.validate("http://8.8.8.8:8088") is HubUrlPolicy.Verdict.Rejected)
    }

    @Test
    fun testPrivateHttpAllowed() {
        assertTrue(HubUrlPolicy.validate("http://192.168.1.5:8088") is HubUrlPolicy.Verdict.Allowed)
        assertTrue(HubUrlPolicy.validate("http://10.0.2.2:8088") is HubUrlPolicy.Verdict.Allowed)
        assertTrue(HubUrlPolicy.validate("http://localhost:8088") is HubUrlPolicy.Verdict.Allowed)
        assertTrue(HubUrlPolicy.validate("http://macbook.local:8088") is HubUrlPolicy.Verdict.Allowed)
        // Tailscale 使用 100.64.0.0/10
        assertTrue(HubUrlPolicy.validate("http://100.112.108.52:8088") is HubUrlPolicy.Verdict.Allowed)
    }

    @Test
    fun testNonHttpSchemeRejected() {
        assertTrue(HubUrlPolicy.validate("ftp://192.168.1.5") is HubUrlPolicy.Verdict.Rejected)
        assertTrue(HubUrlPolicy.validate("javascript:alert(1)") is HubUrlPolicy.Verdict.Rejected)
        assertTrue(HubUrlPolicy.validate("") is HubUrlPolicy.Verdict.Rejected)
    }

    @Test
    fun testTrailingSlashNormalized() {
        val verdict = HubUrlPolicy.validate("https://archive.example.com/")
        assertEquals("https://archive.example.com", (verdict as HubUrlPolicy.Verdict.Allowed).url)
    }
}
