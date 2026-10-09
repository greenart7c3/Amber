package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.LocalSignerProtocol
import com.greenart7c3.nostrsigner.desktop.core.LocalSignerProtocol.RpcException
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSignerProtocolTest {
    private fun codeOf(block: () -> Unit): Int = try {
        block()
        -1
    } catch (e: RpcException) {
        e.code
    }

    @Test
    fun handshakeAdvertisesTheNip55MethodSet() {
        val node = JacksonMapper.mapper.readTree(LocalSignerProtocol.handshake())
        val methods = node.get("supported_methods").map { it.asText() }
        listOf(
            "get_public_key",
            "sign_event",
            "nip04_encrypt",
            "nip04_decrypt",
            "nip44_encrypt",
            "nip44_decrypt",
            "nip44v3_encrypt",
            "nip44v3_decrypt",
            "decrypt_zap_event",
        ).forEach { assertTrue("missing $it", it in methods) }
        // NIP-55 style: approval only through get_public_key.
        assertTrue("connect" !in methods && "list_public_keys" !in methods)
        assertTrue(node.get("name").asText().startsWith("Amber"))
    }

    @Test
    fun helloCarriesNameAndOptionalToken() {
        assertEquals(LocalSignerProtocol.ClientHello("nak", null), LocalSignerProtocol.parseHello("""{"client":"nak"}"""))
        assertEquals(LocalSignerProtocol.ClientHello("nak", "abc"), LocalSignerProtocol.parseHello("""{"client":"nak","secret":"abc"}"""))
        // A client that skips the handshake reply and sends a request straight away.
        assertNull(LocalSignerProtocol.parseHello("""{"id":"1","method":"get_public_key","params":[]}"""))
    }

    @Test
    fun malformedRequestsMapToTheSpecErrorCodes() {
        assertEquals(LocalSignerProtocol.INVALID_REQUEST, codeOf { LocalSignerProtocol.parseRequest("not json") })
        assertEquals(LocalSignerProtocol.INVALID_REQUEST, codeOf { LocalSignerProtocol.parseRequest("""{"method":"sign_event"}""") })
        assertEquals(LocalSignerProtocol.INVALID_PARAMS, codeOf { LocalSignerProtocol.parseRequest("""{"id":"1","method":"x","params":{}}""") })
        val unknown = LocalSignerProtocol.parseRequest("""{"id":"1","method":"sign_psbt","params":[]}""")
        assertEquals(LocalSignerProtocol.METHOD_NOT_SUPPORTED, codeOf { LocalSignerProtocol.toCall(unknown) })
        val missing = LocalSignerProtocol.parseRequest("""{"id":"1","method":"nip44_encrypt","params":["abc"]}""")
        assertEquals(LocalSignerProtocol.INVALID_PARAMS, codeOf { LocalSignerProtocol.toCall(missing) })
        // Numeric ids are accepted and echoed as text.
        assertEquals("7", LocalSignerProtocol.parseRequest("""{"id":7,"method":"ping"}""").id)
    }

    @Test
    fun trailingParamPicksTheAccount() {
        val pub = KeyPair().pubKey.toHexKey()
        val call = LocalSignerProtocol.toCall(
            LocalSignerProtocol.parseRequest("""{"id":"1","method":"nip44_encrypt","params":["$pub","hello","$pub"]}"""),
        )
        assertEquals(SignerType.NIP44_ENCRYPT, call.type)
        assertEquals(listOf(pub, "hello"), call.params)
        assertEquals(pub, call.account)
        // Hex and npub name the same account.
        assertEquals(LocalSignerProtocol.npubOf(pub), LocalSignerProtocol.npubOf(pub.hexToByteArray().toNpub()))
        assertNull(LocalSignerProtocol.npubOf("not-a-key"))
    }

    @Test
    fun signEventTakesTheEventAsAnObject() {
        val call = LocalSignerProtocol.toCall(
            LocalSignerProtocol.parseRequest(
                """{"id":"1","method":"sign_event","params":[{"kind":1,"content":"hi","tags":[],"created_at":1700000000}]}""",
            ),
        )
        val request = LocalSignerProtocol.bunkerRequest("internal-1", call)
        assertTrue(request is BunkerRequestSign)
        assertEquals(1, (request as BunkerRequestSign).event.kind)
        assertEquals("internal-1", request.id)
    }

    @Test
    fun responsesFollowTheSpecShape() {
        val ok = JacksonMapper.mapper.readTree(LocalSignerProtocol.success("a", LocalSignerProtocol.resultNode(SignerType.GET_PUBLIC_KEY, "ff")))
        assertEquals("ff", ok.get("result").asText())
        assertTrue(ok.get("error").isNull)
        val err = JacksonMapper.mapper.readTree(LocalSignerProtocol.failure("a", 5, "user rejected"))
        assertTrue(err.get("result").isNull)
        assertEquals(5, err.get("error").get("code").asInt())
        assertEquals(LocalSignerProtocol.USER_DECLINED, LocalSignerProtocol.codeFor(LocalSignerProtocol.USER_REJECTED))
        assertEquals(LocalSignerProtocol.INTERNAL_ERROR, LocalSignerProtocol.codeFor("signer locked"))
    }
}
