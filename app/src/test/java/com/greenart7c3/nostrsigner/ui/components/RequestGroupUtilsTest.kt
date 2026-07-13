package com.greenart7c3.nostrsigner.ui.components

import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestGroupUtilsTest {

    private fun amberEvent(kind: Int) = AmberEvent("id", "pubkey", 0L, kind, arrayOf(), "", "")

    private fun eventData(kind: Int) = EventEncryptedDataKind(amberEvent(kind), null, "result")

    @Test
    fun `sign event groups by event kind`() {
        val key1 = requestGroupKey(SignerType.SIGN_EVENT, 1, null, null)
        val key1b = requestGroupKey(SignerType.SIGN_EVENT, 1, null, null)
        val key22242 = requestGroupKey(SignerType.SIGN_EVENT, 22242, null, null)

        assertEquals(key1, key1b)
        assertEquals(1, key1.kind)
        assertEquals(22242, key22242.kind)
        assertEquals(SignerType.SIGN_EVENT, key1.type)
        assertEquals(null, key1.payload)
    }

    @Test
    fun `encrypt decrypt separates by payload shape`() {
        val eventKey = requestGroupKey(SignerType.NIP44_DECRYPT, null, eventData(4), null)
        val tagKey = requestGroupKey(SignerType.NIP44_DECRYPT, null, TagArrayEncryptedDataKind(arrayOf(), "r"), null)
        val textKey = requestGroupKey(SignerType.NIP44_DECRYPT, null, ClearTextEncryptedDataKind("t", "r"), null)
        val zapKey = requestGroupKey(SignerType.DECRYPT_ZAP_EVENT, null, PrivateZapEncryptedDataKind("r"), null)

        assertEquals(RequestPayloadShape.EVENT, eventKey.payload)
        assertEquals(4, eventKey.kind)
        assertEquals(RequestPayloadShape.TAG_ARRAY, tagKey.payload)
        assertEquals(null, tagKey.kind)
        assertEquals(RequestPayloadShape.CLEAR_TEXT, textKey.payload)
        assertEquals(RequestPayloadShape.PRIVATE_ZAP, zapKey.payload)
    }

    @Test
    fun `encrypt decrypt event payloads separate by embedded event kind`() {
        val kind1 = requestGroupKey(SignerType.NIP44_ENCRYPT, null, eventData(1), null)
        val kind1b = requestGroupKey(SignerType.NIP44_ENCRYPT, null, eventData(1), null)
        val kind7 = requestGroupKey(SignerType.NIP44_ENCRYPT, null, eventData(7), null)

        assertEquals(kind1, kind1b)
        assertEquals(1, kind1.kind)
        assertEquals(7, kind7.kind)
    }

    @Test
    fun `nip44 v3 groups by explicit kind`() {
        val enc9 = requestGroupKey(SignerType.NIP44_V3_ENCRYPT, null, null, 9)
        val dec9 = requestGroupKey(SignerType.NIP44_V3_DECRYPT, null, null, 9)
        val decNull = requestGroupKey(SignerType.NIP44_V3_DECRYPT, null, null, null)

        assertEquals(9, enc9.kind)
        assertEquals(9, dec9.kind)
        assertEquals(null, decNull.kind)
        assertEquals(null, enc9.payload)
    }

    @Test
    fun `other types group by type alone`() {
        val connect = requestGroupKey(SignerType.CONNECT, null, null, null)
        val pubKey = requestGroupKey(SignerType.GET_PUBLIC_KEY, null, null, null)

        assertEquals(RequestGroupKey(SignerType.CONNECT, null, null), connect)
        assertEquals(RequestGroupKey(SignerType.GET_PUBLIC_KEY, null, null), pubKey)
    }

    @Test
    fun `groupRequests sorts by type ordinal then payload then kind`() {
        val items = listOf(
            RequestGroupKey(SignerType.SIGN_EVENT, 22242, null),
            RequestGroupKey(SignerType.NIP44_DECRYPT, null, RequestPayloadShape.CLEAR_TEXT),
            RequestGroupKey(SignerType.CONNECT, null, null),
            RequestGroupKey(SignerType.SIGN_EVENT, 1, null),
            RequestGroupKey(SignerType.NIP44_DECRYPT, 4, RequestPayloadShape.EVENT),
        )

        val groups = groupRequests(items) { it }

        assertEquals(
            listOf(
                RequestGroupKey(SignerType.CONNECT, null, null),
                RequestGroupKey(SignerType.SIGN_EVENT, 1, null),
                RequestGroupKey(SignerType.SIGN_EVENT, 22242, null),
                RequestGroupKey(SignerType.NIP44_DECRYPT, 4, RequestPayloadShape.EVENT),
                RequestGroupKey(SignerType.NIP44_DECRYPT, null, RequestPayloadShape.CLEAR_TEXT),
            ),
            groups.map { it.first },
        )
    }

    @Test
    fun `scopeKind maps relay auth encryption and v3 groups`() {
        assertEquals(RequestGroupScopeKind.RELAY_AUTH, RequestGroupKey(SignerType.SIGN_EVENT, 22242, null).scopeKind())
        assertEquals(RequestGroupScopeKind.NONE, RequestGroupKey(SignerType.SIGN_EVENT, 1, null).scopeKind())
        assertEquals(RequestGroupScopeKind.ENCRYPTION_METHOD, RequestGroupKey(SignerType.NIP04_ENCRYPT, null, RequestPayloadShape.CLEAR_TEXT).scopeKind())
        assertEquals(RequestGroupScopeKind.ENCRYPTION_METHOD, RequestGroupKey(SignerType.NIP44_DECRYPT, 4, RequestPayloadShape.EVENT).scopeKind())
        assertEquals(RequestGroupScopeKind.NIP44_V3_KIND, RequestGroupKey(SignerType.NIP44_V3_ENCRYPT, 9, null).scopeKind())
        assertEquals(RequestGroupScopeKind.NIP44_V3_KIND, RequestGroupKey(SignerType.NIP44_V3_DECRYPT, null, null).scopeKind())
        assertEquals(RequestGroupScopeKind.NONE, RequestGroupKey(SignerType.DECRYPT_ZAP_EVENT, null, RequestPayloadShape.PRIVATE_ZAP).scopeKind())
        assertEquals(RequestGroupScopeKind.NONE, RequestGroupKey(SignerType.CONNECT, null, null).scopeKind())
    }

    @Test
    fun `defaultDecryptTypeScope is SPECIFIC for v3 and ALL otherwise`() {
        assertEquals(DecryptTypeScope.SPECIFIC, defaultDecryptTypeScope(SignerType.NIP44_V3_ENCRYPT))
        assertEquals(DecryptTypeScope.SPECIFIC, defaultDecryptTypeScope(SignerType.NIP44_V3_DECRYPT))
        assertEquals(DecryptTypeScope.ALL, defaultDecryptTypeScope(SignerType.NIP04_DECRYPT))
        assertEquals(DecryptTypeScope.ALL, defaultDecryptTypeScope(SignerType.NIP44_ENCRYPT))
        assertEquals(DecryptTypeScope.ALL, defaultDecryptTypeScope(SignerType.DECRYPT_ZAP_EVENT))
    }

    @Test
    fun `hasGroupOptions excludes connect and get public key`() {
        assertEquals(false, RequestGroupKey(SignerType.CONNECT, null, null).hasGroupOptions())
        assertEquals(false, RequestGroupKey(SignerType.GET_PUBLIC_KEY, null, null).hasGroupOptions())
        assertEquals(true, RequestGroupKey(SignerType.SIGN_EVENT, 1, null).hasGroupOptions())
        assertEquals(true, RequestGroupKey(SignerType.NIP44_DECRYPT, null, RequestPayloadShape.CLEAR_TEXT).hasGroupOptions())
    }

    @Test
    fun `groupRequests preserves input order within a group`() {
        data class Item(val id: String, val kind: Int)

        val items = listOf(
            Item("a", 1),
            Item("b", 7),
            Item("c", 1),
            Item("d", 1),
        )

        val groups = groupRequests(items) { RequestGroupKey(SignerType.SIGN_EVENT, it.kind, null) }

        assertEquals(listOf("a", "c", "d"), groups.first { it.first.kind == 1 }.second.map { it.id })
        assertEquals(listOf("b"), groups.first { it.first.kind == 7 }.second.map { it.id })
    }
}
