package com.greenart7c3.nostrsigner.shared

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class BunkerSignerTest {
    @Test
    fun nip44RoundTrip() = runTest {
        val alice = BunkerSigner(KeyPair())
        val bob = BunkerSigner(KeyPair())

        val cipherText = alice.nip44Encrypt("hello bunker", bob.pubKey)
        val plainText = bob.nip44Decrypt(cipherText, alice.pubKey)

        assertEquals("hello bunker", plainText)
    }

    @Test
    fun nip04RoundTrip() = runTest {
        val alice = BunkerSigner(KeyPair())
        val bob = BunkerSigner(KeyPair())

        val cipherText = alice.nip04Encrypt("hello bunker nip04", bob.pubKey)
        val plainText = bob.nip04Decrypt(cipherText, alice.pubKey)

        assertEquals("hello bunker nip04", plainText)
    }
}
