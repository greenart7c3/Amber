package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.ReturnType
import com.vitorpamplona.quartz.nip01Core.core.Event
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentPermissionLookupTest {
    private val key = "com.example.app"

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

    private fun request(type: SignerType, eventKind: Int? = null, nip44v3Kind: Int? = null) = IntentData(
        data = "",
        name = "",
        type = type,
        pubKey = "",
        id = "1",
        callBackUrl = null,
        compression = CompressionType.NONE,
        returnType = ReturnType.SIGNATURE,
        permissions = null,
        currentAccount = "",
        route = null,
        event = eventKind?.let { kind -> mockk<Event> { every { this@mockk.kind } returns kind } },
        encryptedData = null,
        nip44v3Kind = nip44v3Kind,
    )

    @Test
    fun `no saved application or another app's grant answers nothing`() {
        assertNull(intentPermission(null, key, request(SignerType.SIGN_PSBT)))
        assertNull(intentPermission(app(grant(1, "SIGN_PSBT", pkKey = "other.app")), key, request(SignerType.SIGN_PSBT)))
        assertEquals(1, intentPermission(app(grant(1, "SIGN_PSBT")), key, request(SignerType.SIGN_PSBT))?.id)
    }

    @Test
    fun `a v3 grant for the request's kind wins over the all-kinds one`() {
        val application = app(grant(1, "NIP44_V3_ENCRYPT", kind = null), grant(2, "NIP44_V3_ENCRYPT", kind = 7))

        assertEquals(2, intentPermission(application, key, request(SignerType.NIP44_V3_ENCRYPT, nip44v3Kind = 7))?.id)
        assertEquals(1, intentPermission(application, key, request(SignerType.NIP44_V3_ENCRYPT, nip44v3Kind = 9))?.id)
    }

    @Test
    fun `a v2 grant never answers a v3 request`() {
        assertNull(intentPermission(app(grant(1, "NIP44_ENCRYPT")), key, request(SignerType.NIP44_V3_ENCRYPT, nip44v3Kind = 7)))
    }

    @Test
    fun `encryption falls back from the request type to a whole-NIP grant`() {
        assertEquals(1, intentPermission(app(grant(2, "NIP", kind = 44), grant(1, "NIP44_ENCRYPT")), key, request(SignerType.NIP44_ENCRYPT))?.id)
        assertEquals(2, intentPermission(app(grant(2, "NIP", kind = 44)), key, request(SignerType.NIP44_ENCRYPT))?.id)
        assertNull(intentPermission(app(grant(2, "NIP", kind = 4)), key, request(SignerType.NIP44_ENCRYPT)))
    }

    @Test
    fun `relay auth prefers the relay's own grant and only then an all-relays one`() {
        val application = app(grant(1, "SIGN_EVENT", 22242, relay = "*"), grant(2, "SIGN_EVENT", 22242, relay = "relay.one"))

        assertEquals(2, intentPermission(application, key, request(SignerType.SIGN_EVENT, eventKind = 22242), "relay.one")?.id)
        assertEquals(1, intentPermission(application, key, request(SignerType.SIGN_EVENT, eventKind = 22242), "relay.two")?.id)
        assertNull(intentPermission(app(grant(2, "SIGN_EVENT", 22242, relay = "relay.one")), key, request(SignerType.SIGN_EVENT, eventKind = 22242), "relay.two"))
    }

    @Test
    fun `an event is answered by its kind or by its NIP`() {
        assertEquals(1, intentPermission(app(grant(1, "SIGN_EVENT", kind = 1)), key, request(SignerType.SIGN_EVENT, eventKind = 1))?.id)
        // Kind 1 belongs to NIP-01.
        assertEquals(2, intentPermission(app(grant(2, "NIP", kind = 1)), key, request(SignerType.SIGN_EVENT, eventKind = 1))?.id)
        assertNull(intentPermission(app(grant(1, "SIGN_EVENT", kind = 7)), key, request(SignerType.SIGN_EVENT, eventKind = 1)))
    }
}
