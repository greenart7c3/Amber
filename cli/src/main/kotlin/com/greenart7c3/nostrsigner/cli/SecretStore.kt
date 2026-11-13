package com.greenart7c3.nostrsigner.cli

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64

/**
 * Where the key that encrypts the connections file lives. The point is that it
 * sits in the OS credential store, which only this user's session can read, so
 * another app cannot copy the saved client key and hijack the connection.
 */
interface SecretStore {
    /** Where the key is kept, for the messages the user reads. */
    val description: String

    fun read(): ByteArray?

    fun write(key: ByteArray)

    fun delete()

    companion object {
        /** `file` or `keyring`: which backend a store uses, over what it recorded. */
        const val KEY_STORE_ENV = "AMBER_CLI_KEY_STORE"

        /**
         * The OS credential store when this machine has one, a private key file
         * next to the connections file otherwise (containers, CI, headless hosts
         * with no Secret Service running), chosen once per store: see [PinnedSecretStore].
         */
        fun default(
            directory: Path,
            env: (String) -> String? = { System.getenv(it) },
        ): SecretStore = PinnedSecretStore(directory, env) { KeyringSecretStore().takeIf { it.works() } }
    }
}

/**
 * Picks the backend for the store in [directory] once and keeps it. Whether an
 * OS credential store answers can change between runs (a Secret Service that
 * starts with a desktop login, a different D-Bus environment under cron), and
 * choosing afresh each time locked a store out of the key it was created with.
 *
 * The choice is [SecretStore.KEY_STORE_ENV] when set, else the one recorded in
 * the `key-store` file beside the connections the first time the key was saved
 * or read. A store from before that file uses the key file if there is one and
 * the credential store otherwise; a new one the credential store when
 * [keyring] finds a working one. A recorded credential store that is not
 * reachable fails with that reason, not as a missing key.
 */
class PinnedSecretStore(
    private val directory: Path,
    private val env: (String) -> String?,
    private val keyring: () -> SecretStore?,
) : SecretStore {
    private val marker = directory.resolve("key-store")
    private val keyFile = directory.resolve("connection.key")
    private val workingKeyring by lazy { keyring() }
    private val chosen by lazy { explicit() ?: recorded() ?: fromExistingStore() ?: if (workingKeyring != null) KEYRING else FILE }
    private val backend: SecretStore by lazy {
        if (chosen == FILE) {
            FileSecretStore(keyFile)
        } else {
            workingKeyring ?: throw CliError(
                "the key for ${directory.resolve("connections.json")} is in the OS credential store, " +
                    "which this session cannot reach (is a Secret Service running?). Run amber where it is " +
                    "available, or set ${SecretStore.KEY_STORE_ENV}=file to use a key file instead (and pair again).",
            )
        }
    }

    override val description: String
        get() = backend.description

    override fun read(): ByteArray? = backend.read()?.also { pin() }

    override fun write(key: ByteArray) {
        backend.write(key)
        pin()
    }

    override fun delete() {
        backend.delete()
        Files.deleteIfExists(marker)
    }

    private fun explicit(): String? {
        val value = env(SecretStore.KEY_STORE_ENV)?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        if (value != FILE && value != KEYRING) {
            throw CliError("${SecretStore.KEY_STORE_ENV} must be \"file\" or \"keyring\", not \"$value\"", Exit.USAGE)
        }
        return value
    }

    private fun recorded(): String? = runCatching { Files.readString(marker).trim() }.getOrNull()?.takeIf { it == FILE || it == KEYRING }

    // Before the marker existed only the file backend wrote connection.key.
    private fun fromExistingStore(): String? = when {
        Files.exists(keyFile) -> FILE
        Files.exists(directory.resolve("connections.json")) -> KEYRING
        else -> null
    }

    private fun pin() {
        if (recorded() == chosen) return
        Files.createDirectories(directory)
        Files.writeString(marker, chosen + "\n")
    }

    private companion object {
        const val FILE = "file"
        const val KEYRING = "keyring"
    }
}

/**
 * macOS Keychain, Windows Credential Manager or a freedesktop Secret Service
 * (GNOME Keyring, KWallet), the same store the desktop app keeps its keystore
 * password in.
 */
class KeyringSecretStore(
    private val service: String = "amber-cli",
    private val account: String = "connections-key",
) : SecretStore {
    override val description: String = "the OS credential store"

    private val keyring: Keyring? = runCatching { Keyring.create() }.getOrNull()

    /** Whether this backend can actually serve requests (a session without a Secret Service cannot). */
    fun works(): Boolean {
        val ring = keyring ?: return false
        return try {
            ring.getPassword(service, account)
            true
        } catch (_: PasswordAccessException) {
            // The backend answered: there is simply no key saved yet.
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun read(): ByteArray? {
        val ring = keyring ?: throw CliError("the OS credential store is not available")
        val stored = try {
            ring.getPassword(service, account)
        } catch (_: PasswordAccessException) {
            return null
        } catch (e: Exception) {
            throw CliError("could not read the connection key from the OS credential store: ${e.message}")
        }
        return Base64.getDecoder().decode(stored)
    }

    override fun write(key: ByteArray) {
        val ring = keyring ?: throw CliError("the OS credential store is not available")
        try {
            ring.setPassword(service, account, Base64.getEncoder().encodeToString(key))
        } catch (e: Exception) {
            throw CliError("could not save the connection key to the OS credential store: ${e.message}")
        }
    }

    override fun delete() {
        runCatching { keyring?.deletePassword(service, account) }
    }
}

/** Fallback for hosts with no OS credential store: a 0600 file only this user can read. */
class FileSecretStore(
    private val file: Path,
) : SecretStore {
    override val description: String = "the key file $file"

    override fun read(): ByteArray? {
        if (!Files.exists(file)) return null
        return try {
            Base64.getDecoder().decode(Files.readString(file).trim())
        } catch (e: Exception) {
            throw CliError("could not read the connection key $file: ${e.message}")
        }
    }

    override fun write(key: ByteArray) {
        Files.createDirectories(file.parent)
        runCatching { Files.setPosixFilePermissions(file.parent, PosixFilePermissions.fromString("rwx------")) }
        Files.writeString(file, Base64.getEncoder().encodeToString(key))
        runCatching { Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------")) }
    }

    override fun delete() {
        Files.deleteIfExists(file)
    }
}
