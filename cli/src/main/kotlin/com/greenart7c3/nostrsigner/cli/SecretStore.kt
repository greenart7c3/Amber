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
        /**
         * The OS credential store when this machine has one, a private key file
         * next to the connections file otherwise (containers, CI, headless hosts
         * with no Secret Service running).
         */
        fun default(directory: Path): SecretStore {
            val keyring = KeyringSecretStore()
            if (keyring.works()) return keyring
            return FileSecretStore(directory.resolve("connection.key"))
        }
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
