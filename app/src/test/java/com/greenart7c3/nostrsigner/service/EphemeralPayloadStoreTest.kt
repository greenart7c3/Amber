package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.BuildConfig
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EphemeralPayloadStoreTest {
    private var nowMs = 1_000_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun store(
        maxSingle: Int = EphemeralPayloadStore.MAX_SINGLE_PAYLOAD_BYTES,
        maxPerCaller: Long = EphemeralPayloadStore.MAX_PER_CALLER_BYTES.toLong(),
        maxTotal: Long = EphemeralPayloadStore.MAX_TOTAL_BYTES.toLong(),
        maxEntriesPerCaller: Int = EphemeralPayloadStore.MAX_ENTRIES_PER_CALLER,
        ttlMs: Long = EphemeralPayloadStore.ENTRY_TTL_MS,
        awaitTimeoutMs: Long = 2_000L,
    ) = EphemeralPayloadStore(
        scope = scope,
        clock = { nowMs },
        maxSinglePayloadBytes = maxSingle,
        maxPerCallerBytes = maxPerCaller,
        maxTotalBytes = maxTotal,
        maxEntriesPerCaller = maxEntriesPerCaller,
        ttlMs = ttlMs,
        uploadAwaitTimeoutMs = awaitTimeoutMs,
    )

    @Test
    fun `uri helpers accept valid 32-hex tokens and reject traversal or extra segments`() {
        val appId = BuildConfig.APPLICATION_ID
        val token = "0123456789abcdef0123456789abcdef"
        val uri = EphemeralPayloadStore.buildPayloadUri(appId, token)

        assertTrue(EphemeralPayloadStore.isPayloadsCollectionUri("content://$appId.PAYLOADS", appId))
        assertTrue(EphemeralPayloadStore.isPayloadsCollectionUri("content://$appId.PAYLOADS/", appId))
        assertFalse(EphemeralPayloadStore.isPayloadsCollectionUri(uri, appId))

        assertEquals(token, EphemeralPayloadStore.extractToken(uri, appId))
        assertNull(EphemeralPayloadStore.extractToken("content://$appId.PAYLOADS/../secret", appId))
        assertNull(EphemeralPayloadStore.extractToken("content://$appId.PAYLOADS/$token/extra", appId))
        assertNull(EphemeralPayloadStore.extractToken("content://$appId.PAYLOADS/$token?foo=1", appId))
        assertNull(EphemeralPayloadStore.extractToken("content://$appId.PAYLOADS/0123456789ABCDEF0123456789ABCDEF", appId))
        assertNull(EphemeralPayloadStore.extractToken("content://other.app.PAYLOADS/$token", appId))
    }

    @Test
    fun `upload round trip reads stream and removes entry after consume`() {
        runBlocking {
            val s = store()
            val payload = ByteArray(256 * 1024) { (it % 251).toByte() }
            val token = s.allocateUpload(1001, "com.example.client")

            s.readStreamIntoUpload(token, 1001, "com.example.client", ByteArrayInputStream(payload))
            val consumed = s.consumeUpload(token, 1001, "com.example.client")

            assertArrayEquals(payload, consumed)
            assertThrows(FileNotFoundException::class.java) {
                runBlocking { s.consumeUpload(token, 1001, "com.example.client") }
            }
        }
    }

    @Test
    fun `consumeUpload awaits concurrent stream completion without polling`() {
        runBlocking {
            val s = store()
            val payload = "concurrent-large-payload".repeat(2_000).toByteArray()
            val token = s.allocateUpload(1001, "com.example.client")

            val consumer = async { s.consumeUpload(token, 1001, "com.example.client") }
            delay(50)
            s.completeUpload(token, 1001, "com.example.client", payload)

            assertArrayEquals(payload, consumer.await())
        }
    }

    @Test
    fun `oversized stream failure propagates IOException to awaiting consumer`() {
        runBlocking {
            supervisorScope {
                val s = store(maxSingle = 64)
                val token = s.allocateUpload(1001, "com.example.client")
                val consumer = async { s.consumeUpload(token, 1001, "com.example.client") }
                delay(30)

                assertThrows(IOException::class.java) {
                    s.readStreamIntoUpload(token, 1001, "com.example.client", ByteArrayInputStream(ByteArray(128)))
                }
                assertThrows(IOException::class.java) {
                    runBlocking { consumer.await() }
                }
            }
        }
    }

    @Test
    fun `wrong uid or wrong package is rejected with SecurityException`() {
        runBlocking {
            val s = store()
            val uploadToken = s.allocateUpload(1001, "com.example.client")
            s.completeUpload(uploadToken, 1001, "com.example.client", "secret".toByteArray())

            assertThrows(SecurityException::class.java) {
                runBlocking { s.consumeUpload(uploadToken, 1002, "com.example.client") }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { s.consumeUpload(uploadToken, 1001, "com.attacker.client") }
            }
            assertThrows(SecurityException::class.java) {
                s.delete(uploadToken, 1002, "com.example.client")
            }

            val responseToken = s.putResponse(1001, "com.example.client", "response".toByteArray())
            assertThrows(SecurityException::class.java) {
                s.consumeResponse(responseToken, 1002, "com.example.client")
            }
            assertThrows(SecurityException::class.java) {
                s.consumeResponse(responseToken, 1001, "com.attacker.client")
            }
            assertArrayEquals("response".toByteArray(), s.consumeResponse(responseToken, 1001, "com.example.client"))
        }
    }

    @Test
    fun `response entry is single-use and distinct tokens are unique`() {
        val s = store()
        val data = "hello".toByteArray()
        val t1 = s.putResponse(1001, "com.example.client", data.copyOf())
        val t2 = s.putResponse(1001, "com.example.client", data.copyOf())
        assertNotEquals(t1, t2)

        assertArrayEquals(data, s.consumeResponse(t1, 1001, "com.example.client"))
        assertThrows(FileNotFoundException::class.java) {
            s.consumeResponse(t1, 1001, "com.example.client")
        }
        assertArrayEquals(data, s.consumeResponse(t2, 1001, "com.example.client"))
    }

    @Test
    fun `expired entries are evicted, wiped, and release quotas`() {
        runBlocking {
            val s = store(maxPerCaller = 100L, ttlMs = 1_000L)
            val responseBytes = ByteArray(80) { 42 }
            val responseToken = s.putResponse(1001, "com.example.client", responseBytes)
            assertThrows(IOException::class.java) {
                s.putResponse(1001, "com.example.client", ByteArray(30))
            }

            nowMs += 1_000L
            val freshToken = s.putResponse(1001, "com.example.client", ByteArray(80) { 7 })
            assertNotNull(freshToken)
            assertTrue(responseBytes.all { it == 0.toByte() })
            assertThrows(FileNotFoundException::class.java) {
                s.consumeResponse(responseToken, 1001, "com.example.client")
            }
        }
    }

    @Test
    fun `single payload, per-caller, global, and entry-count limits are enforced`() {
        val s = store(maxSingle = 64, maxPerCaller = 100L, maxTotal = 140L, maxEntriesPerCaller = 2)

        assertThrows(IOException::class.java) {
            s.putResponse(1001, "com.a", ByteArray(65))
        }

        val u1 = s.allocateUpload(1001, "com.a")
        assertThrows(IOException::class.java) {
            s.readStreamIntoUpload(u1, 1001, "com.a", ByteArrayInputStream(ByteArray(65)))
        }

        val r1 = s.putResponse(1001, "com.a", ByteArray(60))
        assertThrows(IOException::class.java) {
            s.putResponse(1001, "com.a", ByteArray(50))
        }
        val r2 = s.putResponse(1001, "com.a", ByteArray(30))
        assertThrows(IOException::class.java) {
            s.allocateUpload(1001, "com.a")
        }

        assertThrows(IOException::class.java) {
            s.putResponse(1002, "com.b", ByteArray(60))
        }
        assertTrue(s.delete(r1, 1001, "com.a"))
        assertTrue(s.delete(r2, 1001, "com.a"))
        assertFalse(s.delete(r1, 1001, "com.a"))
        assertNotNull(s.putResponse(1002, "com.b", ByteArray(60)))
    }
}
