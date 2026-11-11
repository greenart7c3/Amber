package com.greenart7c3.nostrsigner

import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.IllegalBlockSizeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retrying a cipher operation that keystore2 pruned while it was open
 * (GHSA regression from `IllegalBlockSizeException` / `INVALID_OPERATION_HANDLE`).
 *
 * The platform `android.security.KeyStoreException` has no public constructor
 * and is missing from the stub `android.jar`, so the injected classifier stands
 * in for it; the cause chain is walked exactly as it is on a device.
 */
class SecureCryptoHelperRetryTest {
    /** Stands in for the keystore's `INVALID_OPERATION_HANDLE` cause. */
    private class PrunedOperation : Exception()

    private val isPrunedCause: (Throwable) -> Boolean = { it is PrunedOperation }

    private fun prunedOperation(): IllegalBlockSizeException = IllegalBlockSizeException().apply {
        initCause(PrunedOperation())
    }

    private fun retry(block: () -> String): String = SecureCryptoHelper.retryPrunedOperation(
        isPruned = { it.isPrunedKeystoreOperation(isExpiredKeystoreError = isPrunedCause) },
        block = block,
    )

    @Test
    fun `a pruned operation is retried until it succeeds`() {
        var attempts = 0
        val result = retry {
            attempts++
            if (attempts < 2) throw prunedOperation()
            "plaintext"
        }

        assertEquals("plaintext", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `a pruned operation that keeps failing is rethrown after three attempts`() {
        var attempts = 0
        val failure = runCatching {
            retry {
                attempts++
                throw prunedOperation()
            }
        }.exceptionOrNull()

        assertEquals(3, attempts)
        assertTrue(failure is IllegalBlockSizeException)
        assertTrue(failure?.cause is PrunedOperation)
    }

    @Test
    fun `a wrong key tag is not retried`() {
        var attempts = 0
        val failure = runCatching {
            retry {
                attempts++
                throw AEADBadTagException("tag mismatch")
            }
        }.exceptionOrNull()

        assertEquals(1, attempts)
        assertTrue(failure is AEADBadTagException)
    }

    @Test
    fun `a block size failure without a keystore cause is not retried`() {
        var attempts = 0
        val failure = runCatching {
            // The default classifier only recognises the keystore's own cause,
            // which this exception does not carry.
            SecureCryptoHelper.retryPrunedOperation<Unit> {
                attempts++
                throw IllegalBlockSizeException("block size mismatch")
            }
        }.exceptionOrNull()

        assertEquals(1, attempts)
        assertTrue(failure is IllegalBlockSizeException)
        assertNull(failure?.cause)
    }

    @Test
    fun `a wrapped failure without a keystore cause is not retried`() {
        var attempts = 0
        val failure = runCatching {
            retry {
                attempts++
                throw ProviderException("keystore failed")
            }
        }.exceptionOrNull()

        assertEquals(1, attempts)
        assertTrue(failure is ProviderException)
    }
}
