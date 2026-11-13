package com.greenart7c3.nostrsigner.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConnectionStoreTest {
    private lateinit var directory: Path
    private lateinit var secrets: FileSecretStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("amber-cli-store")
        secrets = FileSecretStore(directory.resolve("connection.key"))
    }

    private fun store() = ConnectionStore(directory, secrets)

    @After
    fun tearDown() {
        runCatching { directory.toFile().deleteRecursively() }
    }

    private fun connection(name: String = "amber-cli") = SavedConnection(
        clientPrivKey = "a".repeat(64),
        signerPubKey = "b".repeat(64),
        userPubKey = "c".repeat(64),
        relays = listOf("wss://relay.nip46.com/"),
        name = name,
        pairedAt = 1_700_000_000,
    )

    @Test
    fun savesReadsAndRemovesProfiles() {
        val store = store()
        assertNull(store.get("default"))

        store.put("default", connection())
        store.put("work", connection("work scripts"))

        // A second instance sees what the first wrote.
        val reopened = store()
        assertEquals(connection(), reopened.get("default"))
        assertEquals("work scripts", reopened.get("work")?.name)

        assertTrue(reopened.remove("work"))
        assertNull(store().get("work"))
        assertEquals(false, store().remove("work"))
    }

    @Test
    fun keepsTheFilePrivate() {
        val store = ConnectionStore(directory.resolve("nested"), FileSecretStore(directory.resolve("nested/connection.key")))
        store.put("default", connection())
        val file = store.path()
        assertTrue(Files.exists(file))
        val view = Files.getFileStore(file).supportsFileAttributeView("posix")
        if (view) {
            assertEquals(
                "rw-------",
                PosixFilePermissions.toString(Files.getPosixFilePermissions(file)),
            )
            assertEquals(
                "rwx------",
                PosixFilePermissions.toString(Files.getPosixFilePermissions(file.parent)),
            )
        }
    }

    @Test
    fun theFileIsEncrypted() {
        store().put("default", connection())
        val text = Files.readString(directory.resolve("connections.json"))
        assertTrue(text.startsWith("amber-cli-1:"))
        // Nothing of the connection is readable in the file itself.
        assertTrue(!text.contains("a".repeat(64)))
        assertTrue(!text.contains("wss://relay.nip46.com/"))
        assertEquals("default", store().read().profiles.keys.first())
    }

    @Test
    fun aMissingKeyIsAnError() {
        store().put("default", connection())
        val otherSecrets = FileSecretStore(directory.resolve("other.key"))
        val error = runCatching { ConnectionStore(directory, otherSecrets).read() }.exceptionOrNull()
        assertTrue(error is CliError)
        assertTrue((error as CliError).message.contains("key"))
    }

    @Test
    fun tamperingIsDetected() {
        store().put("default", connection())
        val file = directory.resolve("connections.json")
        val text = Files.readString(file)
        Files.writeString(file, text.dropLast(4))
        val error = runCatching { store().read() }.exceptionOrNull()
        assertTrue(error is CliError)
    }

    @Test
    fun aPlaintextFileIsRefused() {
        Files.writeString(directory.resolve("connections.json"), """{"profiles":{"default":{}}}""")
        val error = runCatching { store().read() }.exceptionOrNull()
        assertTrue(error is CliError)
        assertTrue((error as CliError).message.contains("not written by amber-cli"))
    }

    @Test
    fun aBrokenFileIsAnErrorNotAnEmptyStore() {
        Files.writeString(directory.resolve("connections.json"), "not json at all")
        val error = runCatching { store().get("default") }.exceptionOrNull()
        assertTrue(error is CliError)
        assertTrue((error as CliError).message.contains("connections.json"))
    }
}
