package com.greenart7c3.nostrsigner.cli

import com.github.javakeyring.PasswordAccessException
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyringTimeoutTest {
    @Test
    fun `a call that answers in time returns its value`() {
        assertEquals("key", withKeyringTimeout(5, "reading") { "key" })
    }

    @Test
    fun `a call that never answers fails with the timeout exit code instead of hanging`() {
        val locked = CountDownLatch(1)
        val startedAt = System.nanoTime()
        val error = assertThrows(CliError::class.java) {
            // Like a locked keyring waiting on its unlock prompt.
            withKeyringTimeout(1, "reading the connection key") { locked.await() }
        }
        assertEquals(Exit.TIMEOUT, error.code)
        assertTrue(error.message, error.message.contains("did not answer within 1s while reading the connection key"))
        assertTrue(error.message, error.message.contains("AMBER_CLI_KEY_STORE=file"))
        assertTrue((System.nanoTime() - startedAt) < 5_000_000_000L)
        locked.countDown()
    }

    @Test
    fun `what the call throws reaches the caller unchanged`() {
        assertThrows(PasswordAccessException::class.java) {
            withKeyringTimeout(5, "reading") { throw PasswordAccessException("no such entry") }
        }
        val refused = assertThrows(CliError::class.java) {
            withKeyringTimeout(5, "saving") { throw CliError("could not save", Exit.ERROR) }
        }
        assertEquals("could not save", refused.message)
    }
}
