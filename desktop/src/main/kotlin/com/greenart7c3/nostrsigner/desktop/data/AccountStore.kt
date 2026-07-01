package com.greenart7c3.nostrsigner.desktop.data

import com.greenart7c3.nostrsigner.shared.SecureCryptoHelper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Outcome of [AccountStore.migrateLegacyLayoutIfNeeded], surfaced so the UI can report a failure rather than silently losing an account. */
sealed class MigrationResult {
    data object NotNeeded : MigrationResult()

    data class Migrated(val pubKeyHex: String) : MigrationResult()

    /** A legacy `account.key` exists but couldn't be decrypted; left untouched on disk. */
    data object Failed : MigrationResult()
}

/** Loads/persists the desktop bunker's accounts, each encrypted at rest via [SecureCryptoHelper] under its own `~/.amber-bunker/accounts/<pubkeyHex>/`. */
object AccountStore {
    private const val KEY_FILE_NAME = "account.key"

    private fun keyFile(pubKeyHex: String) = File(AppDataDir.accountDir(pubKeyHex), KEY_FILE_NAME)

    suspend fun hasAnyAccount(): Boolean = listAccounts().isNotEmpty()

    /** All stored accounts' pubkeys (hex), sorted for a stable display order. */
    suspend fun listAccounts(): List<String> = withContext(Dispatchers.IO) {
        AppDataDir.accountsDir.listFiles { candidate -> candidate.isDirectory && File(candidate, KEY_FILE_NAME).exists() }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    /** The active account's pubkey, or null if unset or if it points at an account that no longer exists. */
    suspend fun activeAccount(): String? = withContext(Dispatchers.IO) {
        val file = AppDataDir.activeAccountFile
        if (!file.exists()) return@withContext null
        val pubKeyHex = file.readText().trim()
        pubKeyHex.takeIf { it.isNotBlank() && keyFile(it).exists() }
    }

    suspend fun setActive(pubKeyHex: String) = withContext(Dispatchers.IO) {
        val tmp = File(AppDataDir.directory, "active_account.tmp")
        tmp.writeText(pubKeyHex)
        restrictToOwnerOnly(tmp)
        moveFile(tmp, AppDataDir.activeAccountFile, replaceExisting = true)
    }

    /** Loads one account's key, or null if it isn't stored. */
    suspend fun load(pubKeyHex: String): KeyPair? = withContext(Dispatchers.IO) {
        val file = keyFile(pubKeyHex)
        if (!file.exists()) return@withContext null
        val privKeyHex = SecureCryptoHelper.decrypt(file.readText())
        KeyPair(privKey = privKeyHex.hexToByteArray())
    }

    /** Generates a brand-new account, persists it, and makes it active. */
    suspend fun generate(): KeyPair {
        val keyPair = save(KeyPair())
        setActive(keyPair.pubKey.toHexKey())
        return keyPair
    }

    /** Imports an existing private key as a (possibly new) account and makes it active; never overwrites an already-stored key for the same pubkey. */
    suspend fun import(privKeyHex: String): KeyPair {
        val keyPair = KeyPair(privKey = privKeyHex.hexToByteArray())
        val pubKeyHex = keyPair.pubKey.toHexKey()
        if (pubKeyHex !in listAccounts()) {
            save(keyPair)
        }
        setActive(pubKeyHex)
        return keyPair
    }

    /** Permanently deletes one account's key and local data. If it was active, clears the active pointer. */
    suspend fun logout(pubKeyHex: String) = withContext(Dispatchers.IO) {
        AppDataDir.accountDir(pubKeyHex).deleteRecursively()
        if (activeAccount() == null) {
            AppDataDir.activeAccountFile.delete()
        }
    }

    private suspend fun save(keyPair: KeyPair): KeyPair {
        val pubKeyHex = keyPair.pubKey.toHexKey()
        val privKeyHex = requireNotNull(keyPair.privKey) { "Generated key pair is missing a private key" }.toHexKey()
        val encrypted = SecureCryptoHelper.encrypt(privKeyHex)
        withContext(Dispatchers.IO) {
            val file = keyFile(pubKeyHex)
            file.writeText(encrypted)
            restrictToOwnerOnly(file)
        }
        return keyPair
    }

    /**
     * One-time, idempotent move from the pre-multi-account layout
     * (`~/.amber-bunker/account.key` + `~/.amber-bunker/bunker.db`) into
     * `~/.amber-bunker/accounts/<pubkeyHex>/`. Safe to call on every launch: the fast path is
     * a single [File.exists] check once already migrated (the legacy key is moved, not
     * copied, so it no longer exists afterward).
     *
     * The private key only ever moves via one same-filesystem rename and is never deleted
     * independent of that move succeeding; a decrypt failure aborts before touching any file.
     * The database move is retried independently so an interrupted migration (key moved, db
     * not yet) resumes correctly on the next call without re-touching the key.
     */
    suspend fun migrateLegacyLayoutIfNeeded(): MigrationResult = withContext(Dispatchers.IO) {
        val legacyKey = AppDataDir.file(KEY_FILE_NAME)
        if (!legacyKey.exists()) return@withContext MigrationResult.NotNeeded

        val pubKeyHex = runCatching {
            val privKeyHex = SecureCryptoHelper.decrypt(legacyKey.readText())
            KeyPair(privKey = privKeyHex.hexToByteArray()).pubKey.toHexKey()
        }.getOrNull() ?: return@withContext MigrationResult.Failed

        val targetDir = AppDataDir.accountDir(pubKeyHex)
        val targetKey = File(targetDir, KEY_FILE_NAME)
        if (!targetKey.exists()) {
            moveFile(legacyKey, targetKey)
        }

        val legacyDb = AppDataDir.file("bunker.db")
        val targetDb = File(targetDir, "bunker.db")
        if (legacyDb.exists() && !targetDb.exists()) {
            moveFile(legacyDb, targetDb)
        }

        if (activeAccount() == null) {
            setActive(pubKeyHex)
        }

        MigrationResult.Migrated(pubKeyHex)
    }

    private fun moveFile(source: File, target: File, replaceExisting: Boolean = false) {
        val options = buildList {
            add(StandardCopyOption.ATOMIC_MOVE)
            if (replaceExisting) add(StandardCopyOption.REPLACE_EXISTING)
        }.toTypedArray()
        runCatching {
            Files.move(source.toPath(), target.toPath(), *options)
        }.getOrElse {
            Files.move(source.toPath(), target.toPath(), *options.filterNot { it == StandardCopyOption.ATOMIC_MOVE }.toTypedArray())
        }
        restrictToOwnerOnly(target)
    }
}
