package com.greenart7c3.nostrsigner.cli

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.exists

/**
 * A saved NIP-46 connection. [clientPrivKey] is a throwaway key this tool made
 * up itself: Amber binds the connection to its public key. It is not an
 * account key and nothing of the user's lives here.
 *
 * An empty [userPubKey] means pairing was started but never confirmed, with
 * [pendingSecret] the secret the shown nostrconnect URI carries, so running
 * `connect` again re-shows the same URI instead of inventing a new connection.
 */
data class SavedConnection(
    val clientPrivKey: String = "",
    val signerPubKey: String = "",
    val userPubKey: String = "",
    val relays: List<String> = emptyList(),
    val name: String = "amber-cli",
    val pairedAt: Long = 0,
    val pendingSecret: String = "",
) {
    @get:JsonIgnore
    val isPaired: Boolean
        get() = userPubKey.isNotEmpty() && signerPubKey.isNotEmpty()
}

/** The connections file: profiles, so one machine can hold several connections. */
data class ConnectionFile(
    val version: Int = 1,
    val profiles: Map<String, SavedConnection> = emptyMap(),
)

/**
 * `$XDG_CONFIG_HOME/amber-cli/connections.json` (`%APPDATA%` on Windows, and
 * `AMBER_CLI_HOME` wins over both), directory 0700 and file 0600, written
 * atomically so a crash never leaves a half-written file behind.
 *
 * The file is AES-256-GCM encrypted with a key kept in [secrets] (the OS
 * credential store), so another app on the same account cannot read the saved
 * client key and hijack the connection just by opening the file. The header
 * names the format and is authenticated as additional data, so a re-encrypted
 * or swapped file is refused instead of trusted.
 */
class ConnectionStore(
    private val directory: Path,
    private val secrets: SecretStore = SecretStore.default(directory),
) {
    private val file: Path = directory.resolve("connections.json")

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /** The parsed file; a missing one is an empty store. */
    fun read(): ConnectionFile {
        if (!file.exists()) return ConnectionFile()
        val stored = try {
            Files.readString(file).trim()
        } catch (e: Exception) {
            throw CliError("could not read $file: ${e.message}")
        }
        if (!stored.startsWith(HEADER)) {
            throw CliError("$file was not written by amber-cli or is corrupted. Delete it and pair again.")
        }
        return try {
            mapper.readValue<ConnectionFile>(decrypt(stored.removePrefix(HEADER)))
        } catch (e: CliError) {
            throw e
        } catch (e: Exception) {
            throw CliError("could not read $file: ${e.message}. Delete it and pair again.")
        }
    }

    fun get(profile: String): SavedConnection? = read().profiles[profile]

    fun put(
        profile: String,
        connection: SavedConnection,
    ) {
        val current = read()
        write(current.copy(profiles = current.profiles + (profile to connection)))
    }

    /** Removes [profile]; returns whether it existed. */
    fun remove(profile: String): Boolean {
        val current = read()
        if (profile !in current.profiles) return false
        write(current.copy(profiles = current.profiles - profile))
        return true
    }

    fun path(): Path = file

    private fun write(contents: ConnectionFile) {
        createPrivateDirectory()
        val plaintext = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(contents)
        val temporary = Files.createTempFile(directory, ".connections", ".tmp")
        try {
            setPrivateFilePermissions(temporary)
            Files.writeString(temporary, encrypt(plaintext) + "\n")
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun createPrivateDirectory() {
        Files.createDirectories(directory)
        runCatching {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        }
    }

    private fun setPrivateFilePermissions(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        }
    }

    /** Encrypts [plaintext] with the key in [secrets], creating and saving one on first use. */
    private fun encrypt(plaintext: String): String {
        val key = secrets.read() ?: randomKey().also { secrets.write(it) }
        if (key.size != KEY_BYTES) {
            throw CliError("the connection key in ${secrets.description} is damaged; delete $file and pair again")
        }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(HEADER.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return HEADER + Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    private fun decrypt(encoded: String): String {
        val key = secrets.read()
            ?: throw CliError(
                "the key for $file is missing from ${secrets.description}. " +
                    "Delete $file and pair again.",
            )
        if (key.size != KEY_BYTES) {
            throw CliError("the connection key in ${secrets.description} is damaged; delete $file and pair again")
        }
        val blob = try {
            Base64.getDecoder().decode(encoded)
        } catch (e: Exception) {
            throw CliError("$file is corrupted: ${e.message}. Delete it and pair again.")
        }
        if (blob.size <= IV_BYTES) {
            throw CliError("$file is corrupted (too short). Delete it and pair again.")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES),
        )
        cipher.updateAAD(HEADER.toByteArray(Charsets.UTF_8))
        val plaintext = try {
            cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        } catch (_: Exception) {
            throw CliError(
                "$file cannot be decrypted with the key in ${secrets.description} " +
                    "(the key changed or the file was tampered with). Delete it and pair again.",
            )
        }
        return plaintext.toString(Charsets.UTF_8)
    }

    companion object {
        /** The format marker, authenticated with the ciphertext. */
        private const val HEADER = "amber-cli-1:"
        private const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        private val random = SecureRandom()

        private fun randomKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

        /** Where the saved connections live; `AMBER_CLI_HOME` overrides it (tests, isolated agents). */
        fun defaultDirectory(): Path {
            val override = System.getenv("AMBER_CLI_HOME")
            if (!override.isNullOrBlank()) return Paths.get(override).toAbsolutePath()
            val windows = System.getProperty("os.name").lowercase().contains("win")
            if (windows) {
                val appData = System.getenv("APPDATA")
                if (!appData.isNullOrBlank()) return Paths.get(appData, "amber-cli")
            } else {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                if (!xdg.isNullOrBlank()) return Paths.get(xdg, "amber-cli")
                return Paths.get(System.getProperty("user.home"), ".config", "amber-cli")
            }
            return Paths.get(System.getProperty("user.home"), ".amber-cli")
        }
    }
}
