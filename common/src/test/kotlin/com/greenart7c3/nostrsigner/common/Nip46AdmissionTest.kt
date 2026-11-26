package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Nip46AdmissionTest {
    private val connection = Nip46Connection(
        key = "client",
        localKey = "conn-key",
        secret = "client",
        useSecret = false,
        isConnected = true,
        isExpired = false,
    )

    @Test
    fun `a stored connection is admitted`() {
        assertEquals(Nip46Admission.Admit(connection, null), admitNip46Request(SignerType.SIGN_EVENT, connection, "conn-key"))
    }

    @Test
    fun `an unknown sender is refused without the account key`() {
        assertEquals(Nip46Admission.Unknown, admitNip46Request(SignerType.SIGN_EVENT, null, "conn-key"))
    }

    @Test
    fun `a connection whose user-given lifetime is over counts as none`() {
        val expired = connection.copy(isExpired = true)
        assertEquals(Nip46Admission.Unknown, admitNip46Request(SignerType.SIGN_EVENT, expired, "conn-key"))
    }

    @Test
    fun `a stored app writing to another connection's key is ignored`() {
        assertEquals(Nip46Admission.Ignore, admitNip46Request(SignerType.SIGN_EVENT, connection, "other-key"))
    }

    @Test
    fun `a legacy client writing to the account key is not routed through another connection`() {
        assertEquals(Nip46Admission.Admit(connection, null), admitNip46Request(SignerType.SIGN_EVENT, connection, ""))
    }

    @Test
    fun `a connected client re-sending connect is acknowledged`() {
        assertEquals(
            Nip46Admission.AckRepeatedConnect(connection),
            admitNip46Request(SignerType.CONNECT, connection, "other-key", connectSecret = "anything"),
        )
    }

    @Test
    fun `a connect is refused unless its secret names an unused bunker connection`() {
        val bunker = connection.copy(key = "secret-1", secret = "secret-1", useSecret = true, isConnected = false)

        assertEquals(Nip46Admission.RefuseConnect("invalid secret"), admitNip46Request(SignerType.CONNECT, null, "k", connectSecret = "secret-1"))
        assertEquals(
            Nip46Admission.RefuseConnect("already connected"),
            admitNip46Request(SignerType.CONNECT, null, "k", connectSecret = "secret-1", bySecret = bunker.copy(isConnected = true)),
        )
        assertEquals(
            Nip46Admission.Admit(null, bunker),
            admitNip46Request(SignerType.CONNECT, null, "k", connectSecret = "secret-1", bySecret = bunker),
        )
    }

    private val delivered = listOf(RelayUrlNormalizer.normalize("wss://delivered.example/"))
    private val saved = listOf(RelayUrlNormalizer.normalize("wss://saved.example/"))

    private fun Nip46Admission.replyFor(storedRelays: List<NormalizedRelayUrl>? = null, bySecretRelays: List<NormalizedRelayUrl>? = null) = reply("sender", "req-1", storedRelays, bySecretRelays, delivered)

    @Test
    fun `a repeated connect is acknowledged on its connection's relays, with retries`() {
        val ack = Nip46Admission.AckRepeatedConnect(connection).replyFor(storedRelays = saved) as Nip46AdmissionReply.Ack
        assertEquals(connection, ack.connection)
        assertEquals(saved, ack.relays)
        assertEquals("ack", ack.result)
        assertEquals(BUNKER_REPLY_ATTEMPTS, ack.attempts)
    }

    @Test
    fun `a repeated connect from a connection without relays is acknowledged where it arrived`() {
        assertEquals(delivered, (Nip46Admission.AckRepeatedConnect(connection).replyFor(storedRelays = emptyList()) as Nip46AdmissionReply.Ack).relays)
        assertEquals(delivered, (Nip46Admission.AckRepeatedConnect(connection).replyFor() as Nip46AdmissionReply.Ack).relays)
    }

    @Test
    fun `a refused connect is answered once on the relays of the connection its secret names`() {
        assertEquals(
            Nip46AdmissionReply.Refuse("already connected", saved, "Connection rejected: already connected"),
            Nip46Admission.RefuseConnect("already connected").replyFor(bySecretRelays = saved),
        )
        assertEquals(delivered, (Nip46Admission.RefuseConnect("invalid secret").replyFor() as Nip46AdmissionReply.Refuse).relays)
        assertEquals(UNVERIFIED_REPLY_ATTEMPTS, (Nip46Admission.RefuseConnect("x").replyFor() as Nip46AdmissionReply.Refuse).attempts)
    }

    @Test
    fun `an unknown sender gets no permission where it arrived, and a request to another key nothing`() {
        assertEquals(
            Nip46AdmissionReply.Refuse(NIP46_NO_PERMISSION, delivered, "No permission found for sender"),
            Nip46Admission.Unknown.replyFor(storedRelays = saved),
        )
        assertEquals(Nip46AdmissionReply.Drop("Request req-1 from sender reached another connection's key"), Nip46Admission.Ignore.replyFor())
        assertNull(Nip46Admission.Admit(connection, null).replyFor())
    }
}
