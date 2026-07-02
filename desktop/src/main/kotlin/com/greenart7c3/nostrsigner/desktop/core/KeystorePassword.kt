package com.greenart7c3.nostrsigner.desktop.core

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/** Where the keystore password can be kept. */
interface PasswordStore {
    /** Shown in the Settings screen so users know where their secret lives. */
    val description: String

    /** Returns the stored password, or null when absent/unavailable. */
    fun load(): String?

    /** Persists the password; returns false when the backend is unavailable. */
    fun store(secret: String): Boolean

    fun delete()
}

/**
 * OS-native credential storage: macOS Keychain, Windows Credential Manager,
 * or the freedesktop Secret Service (GNOME Keyring / KWallet) on Linux.
 * Unavailable backends (e.g. Linux without a D-Bus secret daemon) surface as
 * load() == null / store() == false so callers fall back to the file store.
 */
class OsCredentialStore(
    private val service: String = SERVICE,
    private val account: String = ACCOUNT,
) : PasswordStore {
    private val keyring: Keyring? by lazy {
        try {
            Keyring.create()
        } catch (e: Throwable) {
            AmberLogger.d("OsCredentialStore", "No OS credential store available: ${e.message}")
            null
        }
    }

    override val description: String = when {
        System.getProperty("os.name").lowercase().contains("mac") -> "macOS Keychain"
        System.getProperty("os.name").lowercase().contains("win") -> "Windows Credential Manager"
        else -> "Secret Service (GNOME Keyring / KWallet)"
    }

    override fun load(): String? = try {
        keyring?.getPassword(service, account)
    } catch (e: PasswordAccessException) {
        // Thrown both for "no entry" and "backend broken"; either way there is
        // nothing usable here.
        null
    } catch (e: Throwable) {
        AmberLogger.e("OsCredentialStore", "Failed to read from the credential store", e)
        null
    }

    override fun store(secret: String): Boolean = try {
        keyring?.setPassword(service, account, secret) != null
    } catch (e: Throwable) {
        AmberLogger.e("OsCredentialStore", "Failed to write to the credential store", e)
        false
    }

    override fun delete() {
        try {
            keyring?.deletePassword(service, account)
        } catch (_: Throwable) {
        }
    }

    companion object {
        const val SERVICE = "com.greenart7c3.nostrsigner"
        const val ACCOUNT = "keystore-password"
    }
}

/** Legacy/fallback storage: an owner-only file next to the keystore. */
class FilePasswordStore(private val file: File) : PasswordStore {
    override val description: String = "local file (${file.name})"

    override fun load(): String? = if (file.exists()) file.readText().trim().ifBlank { null } else null

    override fun store(secret: String): Boolean {
        file.writeText(secret)
        AppDirs.restrictToOwner(file)
        return true
    }

    override fun delete() {
        file.delete()
    }
}

/**
 * Resolves the keystore password, preferring the OS credential store and
 * migrating any legacy password file into it. The file remains the fallback
 * on systems without a secret daemon.
 */
object KeystorePassword {
    data class Resolved(val password: String, val source: PasswordStore)

    fun generate(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * @param keystoreExists whether an encrypted keystore already exists on disk
     * @param opens returns true when the candidate password unlocks that keystore
     */
    fun resolve(
        osStore: PasswordStore,
        fileStore: PasswordStore,
        keystoreExists: Boolean,
        opens: (String) -> Boolean,
    ): Resolved {
        if (!keystoreExists) {
            val fresh = generate()
            if (osStore.store(fresh)) {
                // Never leave a stale file copy behind once the OS store owns it.
                fileStore.delete()
                return Resolved(fresh, osStore)
            }
            fileStore.store(fresh)
            return Resolved(fresh, fileStore)
        }

        val osValue = osStore.load()
        if (osValue != null && opens(osValue)) {
            // The OS store is authoritative; drop any file copy so the data
            // directory alone is no longer enough to unlock the keys.
            fileStore.delete()
            return Resolved(osValue, osStore)
        }

        val fileValue = fileStore.load()
        if (fileValue != null && opens(fileValue)) {
            // Migrate the legacy file into the OS store when possible.
            return if (osStore.store(fileValue)) {
                fileStore.delete()
                Resolved(fileValue, osStore)
            } else {
                Resolved(fileValue, fileStore)
            }
        }

        throw IllegalStateException(
            "Unable to unlock the key store: no working password in the " +
                "OS credential store or the password file. If you copied the " +
                "data directory from another machine, also transfer the " +
                "'${OsCredentialStore.SERVICE}' entry from its credential store.",
        )
    }
}
