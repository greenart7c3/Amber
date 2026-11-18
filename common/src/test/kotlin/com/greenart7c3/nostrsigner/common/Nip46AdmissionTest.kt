package com.greenart7c3.nostrsigner.common

import org.junit.Assert.assertEquals
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

    // The user gave the connection a lifetime: once over, it answers as an unknown app.
    @Test
    fun `an expired connection counts as none`() {
        val expired = connection.copy(isExpired = true)
        assertEquals(Nip46Admission.Unknown, admitNip46Request(SignerType.SIGN_EVENT, expired, "conn-key"))
    }

    @Test
    fun `a stored app writing to another connection's key is ignored`() {
        assertEquals(Nip46Admission.Ignore, admitNip46Request(SignerType.SIGN_EVENT, connection, "other-key"))
        // Addressed to the account key (a legacy client): not routed through another connection.
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
}
