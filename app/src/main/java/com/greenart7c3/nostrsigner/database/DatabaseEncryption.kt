package com.greenart7c3.nostrsigner.database

import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.SecureCryptoHelper
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * At-rest encryption for the per-account Room databases (apps/permissions, logs, history),
 * opt-in via the Security settings screen.
 *
 * The SQLCipher passphrase is 32 random bytes (hex-encoded) generated once and stored in the
 * app-level SharedPreferences sealed with the AndroidKeyStore-backed [SecureCryptoHelper] —
 * the same scheme that protects the nostr private keys.
 *
 * The enabled flag is only flipped after every database file has been converted, so a crash
 * mid-toggle leaves some files ahead of the setting; [prepare] detects the mismatch from the
 * file header on the next open and converts that file before Room touches it.
 */
object DatabaseEncryption {
    private const val DB_KEY_LENGTH_BYTES = 32
    private val databasePrefixes = listOf("amber_db_", "log_db_", "history_db_")
    private val auxiliarySuffixes = listOf("-wal", "-shm", "-journal", ".new", ".old")

    // prepare() holds the read lock; toggle/repair conversions hold the write lock so a
    // database is never opened while its file is being swapped.
    private val migrationLock = ReentrantReadWriteLock()

    @Volatile
    private var cachedPassphrase: String? = null

    @Volatile
    private var libraryLoaded = false

    enum class FileState {
        // also empty/truncated files: there is nothing to convert, SQLite creates them fresh
        MISSING,
        PLAINTEXT,
        ENCRYPTED,
    }

    // Every plaintext SQLite file starts with these 16 bytes; SQLCipher encrypts the whole
    // file including the header, so a random-looking header means an encrypted database.
    private val sqliteMagic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun detectFileState(file: File): FileState {
        if (!file.exists()) return FileState.MISSING
        val header = ByteArray(sqliteMagic.size)
        val read = file.inputStream().use { it.read(header) }
        if (read < header.size) return FileState.MISSING
        return if (header.contentEquals(sqliteMagic)) FileState.PLAINTEXT else FileState.ENCRYPTED
    }

    /**
     * Called by the three Room database builders before opening [dbName]. Repairs any
     * interrupted conversion, then returns the SQLCipher factory when encryption is enabled
     * or null for a plain database.
     */
    fun prepare(context: Context, dbName: String): SupportOpenHelperFactory? {
        val dbFile = context.getDatabasePath(dbName)
        migrationLock.read {
            recoverInterruptedSwap(dbFile)
            val enabled = LocalPreferences.isDatabaseEncryptionEnabled(context)
            if (!needsConversion(dbFile, enabled)) {
                return if (enabled) createFactory(context) else null
            }
        }
        // Mismatch between the setting and the file (crash mid-toggle): convert this file
        // before Room opens it. Re-check under the write lock — the full toggle migration
        // may have run in between.
        migrationLock.write {
            recoverInterruptedSwap(dbFile)
            val enabled = LocalPreferences.isDatabaseEncryptionEnabled(context)
            if (needsConversion(dbFile, enabled)) {
                AmberLog.d(Amber.TAG, "Repairing encryption state of $dbName (enabled=$enabled)")
                migrateFile(dbFile, encrypt = enabled, passphrase = getOrCreatePassphrase(context))
            }
            return if (enabled) createFactory(context) else null
        }
    }

    /**
     * Encrypts or decrypts every account database in place. The setting is only persisted
     * after all files were converted, so this is safe to retry and a crash mid-way is
     * repaired lazily by [prepare]. Throws if the passphrase cannot be created/unsealed or
     * a file conversion fails; the setting is left unchanged in that case.
     */
    suspend fun setEncryptionEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        if (LocalPreferences.isDatabaseEncryptionEnabled(context) == enabled) return@withContext
        // Resolve the passphrase before touching any file so a broken keystore aborts cleanly.
        val passphrase = getOrCreatePassphrase(context)

        // Evict cached Room handles so their open connections don't write to the old files.
        // Kept outside the write lock: a concurrent builder inside ConcurrentHashMap
        // .computeIfAbsent blocks on migrationLock in prepare(), and remove() on the same
        // map bin would deadlock against it.
        Amber.instance.closeAllDatabases()

        migrationLock.write {
            val databasesDir = context.getDatabasePath("amber_db_dummy").parentFile
                ?: throw IOException("No databases directory")
            // Finish any interrupted swap first so the listing below sees the real files.
            databasesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".new") || file.name.endsWith(".old")) {
                    recoverInterruptedSwap(File(file.path.substringBeforeLast('.')))
                }
            }
            val databaseFiles = databasesDir.listFiles()?.filter { file ->
                file.isFile &&
                    databasePrefixes.any { file.name.startsWith(it) } &&
                    auxiliarySuffixes.none { file.name.endsWith(it) }
            } ?: emptyList()

            databaseFiles.forEach { file ->
                if (needsConversion(file, enabled)) {
                    AmberLog.d(Amber.TAG, "Converting ${file.name} (encrypt=$enabled)")
                    migrateFile(file, encrypt = enabled, passphrase = passphrase)
                }
            }

            LocalPreferences.updateEncryptDatabase(context, enabled)
        }

        // Evict again: an instance built while the migration ran may hold the old factory.
        Amber.instance.closeAllDatabases()
    }

    private fun needsConversion(dbFile: File, enabled: Boolean): Boolean = when (detectFileState(dbFile)) {
        FileState.MISSING -> false
        FileState.PLAINTEXT -> enabled
        FileState.ENCRYPTED -> !enabled
    }

    /**
     * Converts one database file in place with SQLCipher's sqlcipher_export(), writing to
     * `<file>.new` and swapping so a crash at any point leaves a recoverable state
     * (see [recoverInterruptedSwap]).
     */
    private fun migrateFile(dbFile: File, encrypt: Boolean, passphrase: String) {
        ensureLibraryLoaded()
        val sourceKey = if (encrypt) "" else passphrase
        val targetKey = if (encrypt) passphrase else ""
        val newFile = File(dbFile.path + ".new")
        val oldFile = File(dbFile.path + ".old")
        // Stale -journal/-wal next to a leftover .new would poison the fresh export.
        deleteWithAuxiliaryFiles(newFile)

        // No rawExecSQL anywhere in this sequence: its native implementation
        // (executeNonQueryRaw) discards the sqlite3_step error code, so a failed ATTACH or
        // export is a silent no-op. execSQL throws on step errors (and its STATEMENT_ATTACH
        // branch applies the library's attached-database mitigation); rawQuery+moveToFirst
        // executes row-returning statements with error propagation.
        val db = SQLiteDatabase.openDatabase(dbFile.path, sourceKey, null, SQLiteDatabase.OPEN_READWRITE, null)
        val version: Int
        val sourceObjects: Long
        try {
            // Fold the WAL into the main file so the export sees every committed row.
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            version = db.version
            sourceObjects = countSchemaObjects(db)
            // Literal SQL (Zetetic's documented pattern), and no DETACH or
            // `target`-qualified statements afterwards: an attached schema is per-connection
            // state, but SQLiteDatabase runs statements through a connection pool, so a
            // later `PRAGMA target...` can compile on a connection where the schema was
            // never attached ("unknown database target"). Closing the handle detaches
            // everything; the schema version is written on a direct open below.
            db.execSQL("ATTACH DATABASE '${escapeLiteral(newFile.path)}' AS target KEY '${escapeLiteral(targetKey)}'")
            db.rawQuery("SELECT sqlcipher_export('target')", null).use { it.moveToFirst() }
        } finally {
            db.close()
        }

        // A source with an empty schema exports no pages and SQLite creates attached
        // database files lazily, so .new may not exist here — CREATE_IF_NECESSARY then
        // produces a valid empty database with the target key, which is the correct
        // conversion of an empty source.
        val converted = SQLiteDatabase.openDatabase(newFile.path, targetKey, null, SQLiteDatabase.CREATE_IF_NECESSARY, null)
        try {
            // A non-empty source must never swap to an empty target — abort (data intact).
            val convertedObjects = countSchemaObjects(converted)
            if (sourceObjects > 0 && convertedObjects == 0L) {
                throw IOException("Export of ${dbFile.name} produced an empty database ($sourceObjects objects expected)")
            }
            // Carry the Room schema version over (or Room would re-run every migration) on
            // a plain, unqualified PRAGMA. Opening with the target key also verifies the
            // converted file is readable before it is swapped into place.
            converted.version = version
        } finally {
            converted.close()
        }
        deleteAuxiliaryFiles(newFile)

        deleteAuxiliaryFiles(dbFile)
        oldFile.delete()
        if (!dbFile.renameTo(oldFile)) {
            newFile.delete()
            throw IOException("Could not move ${dbFile.name} aside")
        }
        if (!newFile.renameTo(dbFile)) {
            oldFile.renameTo(dbFile)
            throw IOException("Could not move converted ${dbFile.name} into place")
        }
        oldFile.delete()
        AmberLog.d(Amber.TAG, "Converted ${dbFile.name} (encrypt=$encrypt, $sourceObjects objects, ${dbFile.length()} bytes)")
    }

    private fun countSchemaObjects(db: SQLiteDatabase): Long = db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }

    // Paths and keys are app-controlled (bech32 npubs, hex passphrase); escaping is
    // belt-and-braces for embedding them as SQL string literals.
    private fun escapeLiteral(value: String): String = value.replace("'", "''")

    private fun deleteAuxiliaryFiles(dbFile: File) {
        File(dbFile.path + "-journal").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    private fun deleteWithAuxiliaryFiles(dbFile: File) {
        dbFile.delete()
        deleteAuxiliaryFiles(dbFile)
    }

    /**
     * Converges the crash windows of [migrateFile]: a leftover `.old` without the main file
     * is the pre-swap original and is restored; leftover `.new`/`.old` next to an intact
     * main file are incomplete/already-swapped copies and are dropped.
     */
    private fun recoverInterruptedSwap(dbFile: File) {
        val newFile = File(dbFile.path + ".new")
        val oldFile = File(dbFile.path + ".old")
        if (!dbFile.exists() && oldFile.exists()) {
            deleteWithAuxiliaryFiles(newFile)
            oldFile.renameTo(dbFile)
        } else {
            deleteWithAuxiliaryFiles(oldFile)
            deleteWithAuxiliaryFiles(newFile)
        }
    }

    private fun createFactory(context: Context): SupportOpenHelperFactory {
        ensureLibraryLoaded()
        return SupportOpenHelperFactory(getOrCreatePassphrase(context).toByteArray(Charsets.US_ASCII))
    }

    // Never swallow keystore exceptions here: enabling must abort before touching files and
    // an existing encrypted database must fail loudly instead of appearing empty
    // (mirrors the broken-KeyMint handling for the sealed nostr keys in LocalPreferences).
    private fun getOrCreatePassphrase(context: Context): String {
        cachedPassphrase?.let { return it }
        synchronized(this) {
            cachedPassphrase?.let { return it }
            val sealed = LocalPreferences.getSealedDatabaseKey(context)
            val passphrase = if (sealed != null) {
                // runBlocking bridges the suspend crypto helper into the synchronous
                // database-open path, like SignerProvider already does for signing.
                runBlocking { SecureCryptoHelper.decrypt(sealed) }
            } else {
                val bytes = ByteArray(DB_KEY_LENGTH_BYTES)
                SecureRandom().nextBytes(bytes)
                // Hex keeps the passphrase safe to embed in ATTACH ... KEY statements.
                val hex = bytes.joinToString("") { "%02x".format(it) }
                LocalPreferences.saveSealedDatabaseKey(context, runBlocking { SecureCryptoHelper.encrypt(hex) })
                hex
            }
            cachedPassphrase = passphrase
            return passphrase
        }
    }

    private fun ensureLibraryLoaded() {
        if (!libraryLoaded) {
            synchronized(this) {
                if (!libraryLoaded) {
                    System.loadLibrary("sqlcipher")
                    libraryLoaded = true
                }
            }
        }
    }
}
