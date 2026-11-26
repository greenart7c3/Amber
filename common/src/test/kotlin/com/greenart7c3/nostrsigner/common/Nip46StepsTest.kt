package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class Nip46StepsTest {
    private fun request(method: String, params: String) = JacksonMapper.mapper.readValue("""{"id":"r1","method":"$method","params":$params}""", BunkerRequest::class.java)

    private fun authEvent(vararg relays: String): BunkerRequest {
        val tags = relays.joinToString(",") { """["relay","$it"]""" }
        val event = JacksonMapper.mapper.writeValueAsString("""{"kind":22242,"content":"","tags":[$tags,["challenge","c"]],"created_at":1}""")
        return request("sign_event", "[$event]")
    }

    private fun step(request: BunkerRequest, whitelist: List<String> = emptyList()) = nip46Step(request, nip46SignerType(request.method), whitelist, listOf("wss://a.example/", "wss://b.example/"))

    @Test
    fun `switch_relays replies with the default relays and logout with an ack`() {
        assertEquals(Nip46Step.SwitchRelays("""["wss://a.example/","wss://b.example/"]"""), step(request("switch_relays", "[]")))
        assertEquals(Nip46Step.Logout, step(request("logout", "[]")))
        assertEquals("ack", Nip46Step.Logout.RESULT)
    }

    @Test
    fun `a relay auth event naming more than one relay is refused`() {
        assertEquals(
            Nip46Step.Refuse(NIP46_MULTI_RELAY_AUTH, "Refusing a multi-relay auth event r1"),
            step(authEvent("wss://one.example", "wss://two.example")),
        )
    }

    @Test
    fun `a relay auth event for a relay outside a non-empty whitelist is refused and recorded`() {
        assertEquals(
            Nip46Step.Refuse(NIP46_USER_REJECTED, "Relay auth for other.example is not in the auth whitelist", recordRejected = true),
            step(authEvent("wss://other.example"), whitelist = listOf("good.example")),
        )
    }

    @Test
    fun `a relay auth event in the whitelist or with no whitelist at all is decided on the grants`() {
        assertEquals(Nip46Step.Decide, step(authEvent("wss://good.example"), whitelist = listOf("good.example")))
        assertEquals(Nip46Step.Decide, step(authEvent("wss://other.example")))
    }

    @Test
    fun `a NIP-44 v3 request without a kind is refused before anything is decrypted`() {
        assertEquals(
            Nip46Step.Refuse(NIP44V3_KIND_REQUIRED, "Rejecting invalid NIP-44 v3 request: $NIP44V3_KIND_REQUIRED"),
            step(request("nip44v3_decrypt", """["pk","","s","payload"]""")),
        )
        assertEquals(Nip46Step.Decide, step(request("nip44v3_decrypt", """["pk","1","s","payload"]""")))
    }

    @Test
    fun `anything else is decided on the grants`() {
        assertEquals(Nip46Step.Decide, step(request("ping", "[]")))
        assertEquals(Nip46Step.Decide, step(request("nip44_encrypt", """["pk","hi"]""")))
    }
}
