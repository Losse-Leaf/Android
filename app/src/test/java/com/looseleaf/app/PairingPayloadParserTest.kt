package com.looseleaf.app

import com.looseleaf.app.ui.pairing.PairingPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PairingPayloadParserTest {

    @Test
    fun testParseOAuthCallbackDeepLink() {
        val raw = "looseleaf://auth/callback?hub=https%3A%2F%2Fleaf.starryheavens.uk&code=auth_code_vesper90s"
        val parsed = PairingPayloadParser.parse(raw)
        assertNotNull(parsed)
        assertEquals("https://leaf.starryheavens.uk", parsed!!.hubUrl)
        assertEquals("auth_code_vesper90s", parsed.code)
        assertEquals("auth_code_vesper90s", parsed.ticket)
    }

    @Test
    fun testParseLegacyPairDeepLink() {
        val raw = "looseleaf://pair?hub=http://192.168.1.100:8088&ticket=pair_tk_a8b9c0"
        val parsed = PairingPayloadParser.parse(raw)
        assertNotNull(parsed)
        assertEquals("http://192.168.1.100:8088", parsed!!.hubUrl)
        assertEquals("pair_tk_a8b9c0", parsed.ticket)
    }

    @Test
    fun testParseWebUrl() {
        val raw = "http://10.0.0.5:8088/pair?ticket=pair_tk_998877"
        val parsed = PairingPayloadParser.parse(raw)
        assertNotNull(parsed)
        assertEquals("http://10.0.0.5:8088", parsed!!.hubUrl)
        assertEquals("pair_tk_998877", parsed.ticket)
    }

    @Test
    fun testParseJson() {
        val raw = """{"hub_url": "https://api.looseleaf.space", "code": "auth_code_cloud123"}"""
        val parsed = PairingPayloadParser.parse(raw)
        assertNotNull(parsed)
        assertEquals("https://api.looseleaf.space", parsed!!.hubUrl)
        assertEquals("auth_code_cloud123", parsed.code)
    }

    @Test
    fun testParsePercentEncodedHub() {
        val raw = "looseleaf://pair?hub=http%3A%2F%2F192.168.1.100%3A8088&ticket=pair_tk_enc"
        val parsed = PairingPayloadParser.parse(raw)
        assertNotNull(parsed)
        assertEquals("http://192.168.1.100:8088", parsed!!.hubUrl)
        assertEquals("pair_tk_enc", parsed.ticket)
    }
}
