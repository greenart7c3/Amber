package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BunkerPermissionLookupTest {
    private val key = "ab".repeat(32)

    private fun grant(id: Int, type: String, kind: Int? = null, relay: String = "", pkKey: String = key) = ApplicationPermissionsEntity(
        id = id,
        pkKey = pkKey,
        type = type,
        kind = kind,
        acceptable = true,
        rememberType = 4,
        acceptUntil = Long.MAX_VALUE,
        rejectUntil = 0,
        relay = relay,
    )

    private fun app(vararg grants: ApplicationPermissionsEntity) = ApplicationWithPermissions(mockk(relaxed = true), grants.toMutableList())

    private fun request(method: String, params: Array<String> = emptyArray(), eventKind: Int? = null) = AmberBunkerRequest(
        request = mockk<BunkerRequest> {
            every { this@mockk.method } returns method
            every { this@mockk.params } returns params
        },
        localKey = key,
        relays = emptyList(),
        currentAccount = "npub1",
        nostrConnectSecret = "",
        closeApplication = true,
        name = "",
        signedEvent = eventKind?.let { kind -> mockk<Event> { every { this@mockk.kind } returns kind } },
        encryptedData = null,
        encryptionType = EncryptionType.NIP44,
        isNostrConnectUri = false,
    )

    @Test
    fun `no saved application or another client's grant answers nothing`() {
        assertNull(bunkerPermission(null, key, request("ping")))
        assertNull(bunkerPermission(app(grant(1, "PING", pkKey = "cd".repeat(32))), key, request("ping")))
        assertEquals(1, bunkerPermission(app(grant(1, "PING")), key, request("ping"))?.id)
    }

    @Test
    fun `connect and get_public_key are always asked`() {
        assertNull(bunkerPermission(app(grant(1, "CONNECT")), key, request("connect")))
        assertNull(bunkerPermission(app(grant(1, "GET_PUBLIC_KEY")), key, request("get_public_key")))
    }

    @Test
    fun `zap decryption and psbt signing match only their own type`() {
        val application = app(grant(1, "NIP", kind = 57), grant(2, "DECRYPT_ZAP_EVENT"), grant(3, "SIGN_PSBT"))
        assertEquals(2, bunkerPermission(application, key, request("decrypt_zap_event"))?.id)
        assertEquals(3, bunkerPermission(application, key, request("sign_psbt"))?.id)
    }

    @Test
    fun `nip04 decryption falls back from its type to the whole-NIP grant`() {
        assertEquals(1, bunkerPermission(app(grant(1, "NIP", kind = 4)), key, request("nip04_decrypt"))?.id)
        assertEquals(2, bunkerPermission(app(grant(1, "NIP", kind = 4), grant(2, "NIP04_DECRYPT")), key, request("nip04_decrypt"))?.id)
        assertNull(bunkerPermission(app(grant(1, "NIP", kind = 44)), key, request("nip04_decrypt")))
    }

    @Test
    fun `a v3 grant for the request's kind wins over the all-kinds one`() {
        val application = app(grant(1, "NIP44_V3_ENCRYPT", kind = null), grant(2, "NIP44_V3_ENCRYPT", kind = 7))
        assertEquals(2, bunkerPermission(application, key, request("nip44v3_encrypt", arrayOf("pk", "7", "scope", "x")))?.id)
        assertEquals(1, bunkerPermission(application, key, request("nip44v3_encrypt", arrayOf("pk", "9", "scope", "x")))?.id)
    }

    @Test
    fun `relay auth prefers the relay's own grant over an all-relays one`() {
        val application = app(grant(1, "SIGN_EVENT", kind = 22242, relay = "*"), grant(2, "SIGN_EVENT", kind = 22242, relay = "relay.example"))
        assertEquals(2, bunkerPermission(application, key, request("sign_event", eventKind = 22242), "relay.example")?.id)
        assertEquals(1, bunkerPermission(application, key, request("sign_event", eventKind = 22242), "other.example")?.id)
    }

    @Test
    fun `an event is answered by its kind's grant`() {
        val application = app(grant(1, "SIGN_EVENT", kind = 1), grant(2, "SIGN_EVENT", kind = 7))
        assertEquals(2, bunkerPermission(application, key, request("sign_event", eventKind = 7))?.id)
        assertNull(bunkerPermission(application, key, request("sign_event", eventKind = 30023)))
    }
}
