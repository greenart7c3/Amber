package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.models.SignerType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stored app may only talk to Amber through its own connection key; a
 * request it addresses to another connection's key is dropped.
 */
class ConnectionKeyBindingTest {
    @Test
    fun `a request on another connection's key is refused`() {
        assertTrue(connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "b", type = SignerType.SIGN_EVENT))
        assertTrue(connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "b", type = SignerType.NIP44_DECRYPT))
    }

    @Test
    fun `its own key, the account key and a connect are allowed`() {
        assertFalse(connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "a", type = SignerType.SIGN_EVENT))
        // Addressed to the account key: there is no per-connection key to bind.
        assertFalse(connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "", type = SignerType.SIGN_EVENT))
        // A connect is how a client attaches to a new connection key.
        assertFalse(connectsThroughOtherKey(appLocalKey = "a", routedPrivKey = "b", type = SignerType.CONNECT))
    }
}
