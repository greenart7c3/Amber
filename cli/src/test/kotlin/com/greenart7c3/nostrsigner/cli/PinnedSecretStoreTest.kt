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

    private fun freshStorePerRun(): SecretStore = PinnedSecretStore(directory, { environment[it] }) { keyring.takeIf { keyringRunning } }

    @Test
    fun aKeyFileStoreKeepsItsKeyWhenACredentialStoreStarts() {
        freshStorePerRun().write(key)
        assertTrue(Files.exists(directory.resolve("connection.key")))

        keyringRunning = true
        assertArrayEquals(key, freshStorePerRun().read())
        assertEquals(null, keyring.key)
    }

    @Test
    fun aCredentialStoreThatGoesAwayIsReportedNotTreatedAsMissing() {
        keyringRunning = true
        freshStorePerRun().write(key)
        assertArrayEquals(key, keyring.key)

        keyringRunning = false
        try {
            freshStorePerRun().read()
            fail("expected the unreachable credential store to be reported")
        } catch (e: CliError) {
            assertTrue(e.message, e.message.contains("OS credential store"))
            assertTrue(e.message, e.message.contains(SecretStore.KEY_STORE_ENV))
        }

        keyringRunning = true
        assertArrayEquals(key, freshStorePerRun().read())
    }

    @Test
    fun aStoreFromBeforeTheMarkerKeepsItsKeyFile() {
        FileSecretStore(directory.resolve("connection.key")).write(key)
        keyringRunning = true
        assertArrayEquals(key, freshStorePerRun().read())
        assertEquals("file", Files.readString(directory.resolve("key-store")).trim())
    }

    @Test
    fun theEnvironmentPicksTheBackendAndLaterRunsWithoutItStayOnIt() {
        keyringRunning = true
        environment[SecretStore.KEY_STORE_ENV] = "file"
        freshStorePerRun().write(key)
        assertTrue(Files.exists(directory.resolve("connection.key")))
        assertEquals(null, keyring.key)

        environment.clear()
        assertArrayEquals(key, freshStorePerRun().read())
    }

    @Test
    fun anUnknownBackendIsAUsageError() {
        environment[SecretStore.KEY_STORE_ENV] = "vault"
        try {
            freshStorePerRun().read()
            fail("expected a usage error")
        } catch (e: CliError) {
            assertEquals(Exit.USAGE, e.code)
        }
    }
}
