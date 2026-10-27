package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.decodeQueryParamValue
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Test

class NostrConnectUtilsTest {

    // --- values that contain `=` (issue #526) ---

    @Test
    fun `base64 secret with padding is preserved`() {
        val (name, value) = NostrConnectUtils.splitParam("secret=XQUdha4rjh_gjAgHN8X5yg==")
        assertEquals("secret", name)
        assertEquals("XQUdha4rjh_gjAgHN8X5yg==", value)
    }

    @Test
    fun `value with multiple equals signs is preserved`() {
        val (name, value) = NostrConnectUtils.splitParam("secret=YQ==Zm9v")
        assertEquals("secret", name)
        assertEquals("YQ==Zm9v", value)
    }

    @Test
    fun `url encoded relay value is not corrupted`() {
        val (name, value) = NostrConnectUtils.splitParam("relay=wss%3A%2F%2Frelay.primal.net")
        assertEquals("relay", name)
        assertEquals("wss%3A%2F%2Frelay.primal.net", value)
    }

    @Test
    fun `perms value is preserved`() {
        val (name, value) = NostrConnectUtils.splitParam("perms=nip04_encrypt,nip44_decrypt,sign_event:1")
        assertEquals("perms", name)
        assertEquals("nip04_encrypt,nip44_decrypt,sign_event:1", value)
    }

    // --- degenerate inputs keep the previous behavior ---

    @Test
    fun `parameter without equals has empty value`() {
        val (name, value) = NostrConnectUtils.splitParam("flag")
        assertEquals("flag", name)
        assertEquals("", value)
    }

    @Test
    fun `parameter with empty value returns empty string`() {
        val (name, value) = NostrConnectUtils.splitParam("secret=")
        assertEquals("secret", name)
        assertEquals("", value)
    }

    @Test
    fun `empty parameter returns empty name and value`() {
        val (name, value) = NostrConnectUtils.splitParam("")
        assertEquals("", name)
        assertEquals("", value)
    }

    // --- form-encoded values, the production parse order: raw '&' split,
    // --- splitParam, then one form-decode per value ---

    @Test
    fun `app name encoded with plus for space shows its space`() {
        // URLEncoder.encode("My App") — what bunker clients actually send
        val raw = "name=" + URLEncoder.encode("My App", "utf-8")
        val (paramName, rawValue) = NostrConnectUtils.splitParam(raw)
        assertEquals("name", paramName)
        assertEquals("My App", decodeQueryParamValue(rawValue))
    }

    @Test
    fun `literal plus in a name arrives percent encoded and survives`() {
        val raw = "name=" + URLEncoder.encode("C++ Dev", "utf-8")
        val (_, rawValue) = NostrConnectUtils.splitParam(raw)
        assertEquals("C++ Dev", decodeQueryParamValue(rawValue))
    }

    @Test
    fun `metadata json containing an encoded ampersand stays one param`() {
        // %26 inside the value must not split the param when the raw query is
        // split before decoding
        val rawQuery = "relay=wss%3A%2F%2Frelay.primal.net&metadata=" +
            URLEncoder.encode("{\"name\":\"My App\",\"url\":\"https://x?a=1&b=2\"}", "utf-8")
        val params = rawQuery.split("&").map { raw ->
            val (paramName, rawValue) = NostrConnectUtils.splitParam(raw)
            paramName to decodeQueryParamValue(rawValue)
        }
        assertEquals("relay", params[0].first)
        assertEquals("wss://relay.primal.net", params[0].second)
        assertEquals("metadata", params[1].first)
        assertEquals("{\"name\":\"My App\",\"url\":\"https://x?a=1&b=2\"}", params[1].second)
    }
}
