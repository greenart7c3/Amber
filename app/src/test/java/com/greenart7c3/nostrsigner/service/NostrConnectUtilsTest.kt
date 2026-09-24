package com.greenart7c3.nostrsigner.service

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
}
