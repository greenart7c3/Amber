package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// `secret_keeper.xml` holds the account list and the settings,
// `secret_keeper_<npub>.xml` the keys and preferences of one account.
private const val LEGACY_FILE_PREFIX = "secret_keeper"
private const val SETTINGS_FILE_NAME = "$LEGACY_FILE_PREFIX.xml"

/**
 * Brings the storage Amber used up to 3.3.0 back into the app: the account
 * list, every setting and every private key lived in the
 * `EncryptedSharedPreferences` files `secret_keeper.xml` and
 * `secret_keeper_<npub>.xml`. 3.4.0 moved to plain `prefs*` files plus an
 * encrypted DataStore per account, with a migration that 4.1.0 deleted while
 * the old files stayed on the device.
 *
 * Without this, an install that jumps from 3.3.0 or older to a current build
 * finds an empty `prefs`, reports that nobody is logged in and shows the
 * first-run onboarding as if the user had never had an account.
 *
 * The legacy files are only ever read here, and deleted once the account they
 * belong to provably opens again, so an interrupted or failed run is retried on
 * the next launch instead of losing the keys.
 */
object LegacyStorageMigration {
    private const val TAG = "LegacyStorageMigration"

    private const val SAVED_ACCOUNTS = "all_saved_accounts"
    private const val CURRENT_ACCOUNT = "currently_logged_in_account"
    private const val PIN = "pin"
    private const val USE_PROXY = "use_proxy"
    private const val PROXY_PORT = "proxy_port"

    /** Moved to the encrypted DataStore, so they must never reach a plain preference file. */
    internal val SECRET_KEYS = setOf("nostr_privkey", "seed_words")

    // Handled one by one instead of the generic settings copy: the account list
    // is merged with the one this install may have built already (in a different
    // order), the current account is only filled in when there is none, and the
    // PIN goes straight to the encrypted store rather than through `prefs`.
    private val HANDLED_SETTINGS_KEYS = setOf(SAVED_ACCOUNTS, CURRENT_ACCOUNT, PIN)

    /**
     * One directory read, no Keystore access: every current install returns
     * false and pays nothing else.
     */
    fun needed(context: Context): Boolean = legacyDirectory(context).list()?.any {
        it.startsWith(LEGACY_FILE_PREFIX) && it.endsWith(".xml")
    } == true

    /**
     * Runs off the main thread. Failures are logged and swallowed: the legacy
     * files stay in place, the app continues with what it has, and the next
     * launch tries again.
     */
    suspend fun run(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                migrate(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AmberLog.e(TAG, "Could not migrate the storage Amber used before 3.4.0, keeping it for the next launch", e)
            }
        }
    }

    private suspend fun migrate(context: Context) {
        val legacySettings = openLegacyPreferences(context, null) ?: return
        val candidates = legacyAccounts(context, legacySettings)
        if (candidates.isEmpty()) {
            AmberLog.w(TAG, "The legacy preferences hold no account file left to restore")
        }

        // The caches may hold the empty list that sent the user to onboarding.
        LocalPreferences.invalidateAccountCaches()
        val hadCurrentAccount = !LocalPreferences.currentAccount(context).isNullOrBlank()

        val restored = candidates.filter { npub ->
            try {
                migrateAccount(context, npub)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(TAG, "Could not migrate the legacy preferences of $npub", e)
                false
            }
        }

        if (restored.isNotEmpty()) {
            // Union, never replace: the user may have created or imported an
            // account in this build after landing on the onboarding screen.
            LocalPreferences.replaceSavedAccounts(
                context,
                mergeAccountLists(LocalPreferences.savedAccountList(context), restored),
            )

            if (!hadCurrentAccount) {
                val legacyCurrent = legacySettings.getString(CURRENT_ACCOUNT, null)
                (legacyCurrent?.takeIf { it in restored } ?: restored.firstOrNull())
                    ?.let { LocalPreferences.switchToAccount(context, it) }
            }
        }

        // Only where this install has no value: the user's choices here win.
        copyPreferences(
            from = legacySettings,
            to = LocalPreferences.appPreferences(context),
            skipped = HANDLED_SETTINGS_KEYS,
            onlyMissing = true,
        )
        restorePin(context, legacySettings)
        restoreTorSettings(context)

        // An account only counts as restored when it opens again; everything else
        // keeps its file (and the settings file) for another attempt.
        val verified = restored.filter { npub ->
            try {
                LocalPreferences.loadFromEncryptedStorage(context, npub) != null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(TAG, "The migrated account $npub does not open yet", e)
                false
            }
        }
        if (verified.size < restored.size) {
            AmberLog.w(TAG, "Only ${verified.size} of ${restored.size} legacy accounts could be opened")
        }

        deleteLegacyFiles(context, legacyFilesToDelete(candidates, verified))
    }

    @Suppress("DEPRECATION")
    private fun openLegacyPreferences(context: Context, npub: String?): SharedPreferences? {
        // Opening a missing EncryptedSharedPreferences creates it, so a stale
        // empty file would be left behind for every account that is gone.
        if (npub != null && !File(legacyDirectory(context), "${LEGACY_FILE_PREFIX}_$npub.xml").exists()) return null

        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            if (npub == null) LEGACY_FILE_PREFIX else "${LEGACY_FILE_PREFIX}_$npub",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * The npubs that can still be restored: the settings file's list in its own
     * order, then every account file on disk it does not mention (a truncated
     * settings file must not strand an account). An account listed without a
     * file has nothing left to restore and is left out.
     */
    private fun legacyAccounts(context: Context, legacySettings: SharedPreferences): List<String> {
        val listed = legacySettings.getString(SAVED_ACCOUNTS, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val onDisk = legacyDirectory(context).list().orEmpty()
            .filter { it.startsWith("${LEGACY_FILE_PREFIX}_") && it.endsWith(".xml") }
            .map { it.removePrefix("${LEGACY_FILE_PREFIX}_").removeSuffix(".xml") }
        return (listed.filter { it in onDisk } + onDisk).distinct()
    }

    /**
     * Copies the private key and the seed words into the encrypted DataStore and
     * everything else into `prefs_<npub>`. Returns false when nothing could be
     * read out of the file, so its file is kept for another attempt.
     */
    private suspend fun migrateAccount(context: Context, npub: String): Boolean {
        val legacy = openLegacyPreferences(context, npub) ?: return false
        val privateKey = legacy.getString("nostr_privkey", null)
        if (privateKey.isNullOrBlank()) return false

        // getEncryptedRaw is the non-throwing existence check: getEncryptedKey
        // fails with FailedMigrationException on an empty store, and a key the
        // user re-imported in this build must not be overwritten with the old one.
        if (DataStoreAccess.getEncryptedRaw(context, npub, DataStoreAccess.NOSTR_PRIVKEY) == null) {
            DataStoreAccess.saveEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY, privateKey)
        }
        val seedWords = legacy.getString("seed_words", null)
        if (!seedWords.isNullOrBlank() && DataStoreAccess.getEncryptedRaw(context, npub, DataStoreAccess.SEED_WORDS) == null) {
            DataStoreAccess.saveEncryptedKey(context, npub, DataStoreAccess.SEED_WORDS, seedWords)
        }

        copyPreferences(
            from = legacy,
            to = LocalPreferences.accountPreferences(context, npub),
            skipped = SECRET_KEYS,
            onlyMissing = true,
        )
        return true
    }

    /**
     * The PIN goes into the encrypted store directly: the settings file is only
     * encrypted by its own format, and copying it to `prefs` (as the 3.4.0
     * migration did, leaving [LocalPreferences.loadPinFromEncryptedStorage] to
     * move it later) would leave it in a plain file until the next unlock.
     */
    private suspend fun restorePin(context: Context, legacySettings: SharedPreferences) {
        val pin = legacySettings.getString(PIN, null)
        if (!pin.isNullOrBlank() && DataStoreAccess.getPinRaw(context) == null) {
            DataStoreAccess.savePin(context, pin)
        }
    }

    /**
     * 3.3.0 kept the Tor switch in the settings file, and some versions after it
     * in the account files; both are read from `prefs` now. Restoring it keeps a
     * user who routed every connection through their Orbot off the clearnet.
     */
    private fun restoreTorSettings(context: Context) {
        val settings = LocalPreferences.appPreferences(context)
        if (settings.contains(USE_PROXY)) return

        val accounts = LocalPreferences.allSavedAccounts(context)
        val useProxy = accounts.any { LocalPreferences.accountPreferences(context, it.npub).getBoolean(USE_PROXY, false) }
        val proxyPort = accounts.firstNotNullOfOrNull {
            val accountPrefs = LocalPreferences.accountPreferences(context, it.npub)
            if (accountPrefs.getBoolean(USE_PROXY, false)) accountPrefs.getInt(PROXY_PORT, 9050) else null
        } ?: 9050

        settings.edit {
            putBoolean(USE_PROXY, useProxy)
            putInt(PROXY_PORT, proxyPort)
        }
    }

    private fun deleteLegacyFiles(context: Context, names: List<String>) {
        names.forEach { name ->
            val file = File(legacyDirectory(context), name)
            if (file.delete()) {
                AmberLog.d(TAG, "Deleted migrated legacy preferences $name")
            } else if (file.exists()) {
                AmberLog.w(TAG, "Could not delete migrated legacy preferences $name")
            }
        }
    }

    private fun legacyDirectory(context: Context): File = File("${context.filesDir.parent}/shared_prefs/")
}

/**
 * The saved account list with [legacy] appended, keeping the order and dropping
 * duplicates, so an account created in this build stays and the migrated ones
 * are added after it.
 */
internal fun mergeAccountLists(current: List<String>, legacy: List<String>): List<String> = (current + legacy).distinct()

/**
 * The legacy files the migration may delete: the account file of every npub in
 * [restored], and the settings file only once every account in [accounts] was
 * restored, because it also holds the account list and the settings.
 */
internal fun legacyFilesToDelete(accounts: Collection<String>, restored: Collection<String>): List<String> {
    val accountFiles = restored.map { "${LEGACY_FILE_PREFIX}_$it.xml" }
    return if (accounts.all { it in restored }) accountFiles + SETTINGS_FILE_NAME else accountFiles
}

/**
 * Copies every entry of [from] into [to]: [skipped] keys never leave [from], and
 * with [onlyMissing] a value [to] already has is kept. [from] and [to] are
 * parameters instead of a [Context] so the copy stays testable without the
 * AndroidKeyStore.
 */
internal fun copyPreferences(
    from: SharedPreferences,
    to: SharedPreferences,
    skipped: Set<String> = emptySet(),
    onlyMissing: Boolean = false,
) {
    val existing = if (onlyMissing) to.all.keys else emptySet()
    val editor = to.edit()
    var changed = false
    from.all.forEach { (key, value) ->
        if (key in skipped || key in existing) return@forEach
        if (editor.putValue(key, value)) changed = true
    }
    if (changed) editor.apply()
}

/**
 * Copies one preference value, returning false for types `SharedPreferences`
 * cannot hold (nothing else is ever stored, so this only guards against a file
 * written by an older version).
 */
private fun SharedPreferences.Editor.putValue(key: String, value: Any?): Boolean = when (value) {
    is String -> {
        putString(key, value)
        true
    }
    is Boolean -> {
        putBoolean(key, value)
        true
    }
    is Int -> {
        putInt(key, value)
        true
    }
    is Long -> {
        putLong(key, value)
        true
    }
    is Float -> {
        putFloat(key, value)
        true
    }
    is Set<*> -> if (value.all { it is String }) {
        @Suppress("UNCHECKED_CAST")
        putStringSet(key, value as Set<String>)
        true
    } else {
        false
    }
    else -> false
}
