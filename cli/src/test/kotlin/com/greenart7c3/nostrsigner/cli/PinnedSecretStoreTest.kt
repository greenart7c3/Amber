package com.greenart7c3.nostrsigner.cli

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The backend a store's key lives in must not change with what the session
 * happens to offer on a given run. The OS credential store is played by an
 * in-memory store that can come and go: tests never touch the real keyring.
 */
class PinnedSecretStoreTest {
    private class FakeKeyring : SecretStore {
        var key: ByteArray? = null
        override val description = "the fake credential store"

        override fun read(): ByteArray? = key

        override fun write(key: ByteArray) {
            this.key = key
        }

        override fun delete() {
            key = null
        }
    }

    private lateinit var directory: Path
    private val keyring = FakeKeyring()
    private var keyringRunning = false
    private val environment = mutableMapOf<String, String>()
    private val key = ByteArray(32) { it.toByte() }

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("amber-cli-keystore")
    }

    @After
    fun tearDown() {
        directory.toFile().deleteRecursively()
    }

    /** A fresh store object per call, as every CLI run makes one. */
    private fun store(): SecretStore = PinnedSecretStore(directory, { environment[it] }) { keyring.takeIf { keyringRunning } }

    @Test
    fun aKeyFileStoreKeepsItsKeyWhenACredentialStoreStarts() {
        store().write(key)
        assertTrue(Files.exists(directory.resolve("connection.key")))

        keyringRunning = true
        assertArrayEquals(key, store().read())
        assertEquals(null, keyring.key)
    }

    @Test
    fun aCredentialStoreThatGoesAwayIsReportedNotTreatedAsMissing() {
        keyringRunning = true
        store().write(key)
        assertArrayEquals(key, keyring.key)

        keyringRunning = false
        try {
            store().read()
            fail("expected the unreachable credential store to be reported")
        } catch (e: CliError) {
            assertTrue(e.message, e.message.contains("OS credential store"))
            assertTrue(e.message, e.message.contains(SecretStore.KEY_STORE_ENV))
        }

        keyringRunning = true
        assertArrayEquals(key, store().read())
    }

    @Test
    fun aStoreFromBeforeTheMarkerKeepsItsKeyFile() {
        FileSecretStore(directory.resolve("connection.key")).write(key)
        keyringRunning = true
        assertArrayEquals(key, store().read())
        assertEquals("file", Files.readString(directory.resolve("key-store")).trim())
    }

    @Test
    fun theEnvironmentPicksTheBackend() {
        keyringRunning = true
        environment[SecretStore.KEY_STORE_ENV] = "file"
        store().write(key)
        assertTrue(Files.exists(directory.resolve("connection.key")))
        assertEquals(null, keyring.key)

        // The explicit choice is recorded, so later runs without it stay on the file.
        environment.clear()
        assertArrayEquals(key, store().read())
    }

    @Test
    fun anUnknownBackendIsAUsageError() {
        environment[SecretStore.KEY_STORE_ENV] = "vault"
        try {
            store().read()
            fail("expected a usage error")
        } catch (e: CliError) {
            assertEquals(Exit.USAGE, e.code)
        }
    }
}
