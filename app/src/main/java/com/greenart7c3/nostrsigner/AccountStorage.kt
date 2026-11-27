package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.database.LogDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.cache.LargeCache
import java.io.File
import java.security.InvalidKeyException
import java.security.KeyStoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * The accounts with their keys: the private key and seed words encrypted in the
 * account's DataStore, the rest in its preference file, and every decrypted
 * account cached for the process. Login, logout and the account names live here
 * because they change that cache.
 */
object AccountStorage {
    private val accountCache = LargeCache<String, Account>()

    private lateinit var accountRepository: AccountRepository
    private lateinit var appScope: CoroutineScope

    /** Called from Amber's constructor, before any ContentProvider or component runs. */
    fun install(accountRepository: AccountRepository, appScope: CoroutineScope) {
        this.accountRepository = accountRepository
        this.appScope = appScope
    }

    suspend fun save(
        context: Context,
        account: Account,
        pubKey: String?,
        privKey: String?,
        seedWords: String?,
    ) {
        privKey?.let {
            DataStoreAccess.saveEncryptedKey(context, account.npub, DataStoreAccess.NOSTR_PRIVKEY, it)
        }
        seedWords?.let {
            DataStoreAccess.saveEncryptedKey(context, account.npub, DataStoreAccess.SEED_WORDS, it)
        }
        accountPreferences(context, account.npub).edit {
            pubKey?.let { putString(AccountPrefKeys.NOSTR_PUBKEY.key, it) }
            putString(AccountPrefKeys.ACCOUNT_NAME.key, account.name.value)
            putString(AccountPrefKeys.PROFILE_URL.key, account.picture.value)
            putInt(AccountPrefKeys.SIGN_POLICY.key, account.signPolicy)
            putBoolean(AccountPrefKeys.DID_BACKUP.key, account.didBackup)
        }
        accountCache.put(account.npub, account)
    }

    /** Saves [account] with its keys and makes it the current account. */
    suspend fun login(
        context: Context,
        account: Account,
        pubKey: String?,
        privKey: String?,
        seedWords: String?,
    ) {
        save(context, account, pubKey, privKey, seedWords)
        SavedAccounts.select(context, account.npub)
    }

    /**
     * Clears the preferences for a given npub, deletes the preferences XML file,
     * and switches the user to the first account in the list if it exists.
     * Returns whether no account is left.
     *
     * We need to use `commit()` to write changes to disk and release the file
     * lock so that it can be deleted. If we use `apply()` there is a race
     * condition and the file will probably not be deleted
     *
     * The cached account's private-key bytes are zeroed in place before it leaves the
     * cache (GHSA-8844-q5vh-9j8f, I3), so the key does not linger in the heap until GC.
     * The Room handles are closed before any file is deleted, releasing their file locks
     * and worker threads. Logout deletes all local information for the account, as the
     * logout dialog says: the databases go with their -wal/-shm/-journal files, so
     * re-importing the key starts with no connections, grants, history or logs.
     */
    @SuppressLint("ApplySharedPref")
    suspend fun logout(npub: String, context: Context): Boolean {
        accountCache.get(npub)?.signer?.keyPair?.privKey?.fill(0)
        accountCache.remove(npub)
        accountRepository.closeDatabasesFor(npub)
        deleteAccountDatabases(context, npub)
        accountPreferences(context, npub).edit(commit = true) { clear() }
        SavedAccounts.remove(context, npub)
        SavedAccounts.setFavorite(context, npub, false)
        deleteUserPreferenceFile(context, npub)
        deleteUserDataStoreFile(context, npub)

        if (SavedAccounts.list(context).isEmpty()) {
            appPreferences(context).edit(commit = true) { clear() }
            return true
        } else if (SavedAccounts.current(context) == npub) {
            SavedAccounts.switchTo(context, SavedAccounts.list(context).elementAt(0))
        }
        return false
    }

    private fun deleteAccountDatabases(context: Context, npub: String) {
        listOf(AppDatabase.fileName(npub), LogDatabase.fileName(npub), HistoryDatabase.fileName(npub)).forEach { name ->
            if (!context.deleteDatabase(name) && context.getDatabasePath(name).exists()) {
                AmberLog.e(Amber.TAG, "Could not delete database $name at logout")
            }
        }
    }

    private fun deleteUserPreferenceFile(context: Context, npub: String) {
        val prefsDir = File("${context.filesDir.parent}/shared_prefs/")
        prefsDir.list()?.forEach {
            if (it.contains(npub)) {
                try {
                    val result = File(prefsDir, it).delete()
                    AmberLog.d(Amber.TAG, "deleted $it: $result")
                } catch (e: Exception) {
                    AmberLog.d(Amber.TAG, "failed to delete $it", e)
                }
            }
        }
    }

    private suspend fun deleteUserDataStoreFile(context: Context, npub: String) {
        DataStoreAccess.clearCacheForNpub(context, npub)
    }

    /** The decrypted accounts so far, without decrypting any. */
    fun cached(): List<Account> = accountCache.values().toList()

    /**
     * Decrypts and caches every saved account.
     *
     * The account switcher in the approval screens (LoginWithPubKey,
     * BunkerConnectRequestScreen) reads [cached] synchronously inside
     * remember {}, so the cache must be fully populated before the first
     * approval screen composes. AccountStateViewModel calls this at app start —
     * the only deterministic startup point that covers both flavors
     * (ConnectivityService runs reloadApp() asynchronously and does not exist
     * in the offline flavor). [loadSync] stays lazy for the SignerProvider /
     * NIP-46 paths that only ever need one known npub (GHSA-8844-q5vh-9j8f, I3).
     */
    suspend fun all(context: Context): List<Account> = SavedAccounts.all(context).mapNotNull { load(context, it.npub) }

    /**
     * [load] for synchronous callers (the ContentProvider). It decrypts only the requested
     * account (GHSA-8844-q5vh-9j8f, I3): warming the whole cache here decrypted every
     * account's private key on the first IPC or relay lookup. The account switcher's
     * [cached] list is filled by [all] at app start instead.
     */
    @Suppress("RunBlocking")
    fun loadSync(context: Context, npub: String? = null): Account? {
        val targetNpub = npub ?: SavedAccounts.current(context) ?: return null
        accountCache.get(targetNpub)?.let { return it }
        if (!SavedAccounts.contains(context, targetNpub)) return null
        return runBlocking { load(context, targetNpub) }
    }

    fun setName(
        context: Context,
        npub: String,
        value: String,
    ) {
        accountPreferences(context, npub).edit { putString(AccountPrefKeys.ACCOUNT_NAME.key, value) }
        accountCache.get(npub)?.name?.tryEmit(value)
    }

    fun getName(context: Context, npub: String): String = accountPreferences(context, npub).getString(AccountPrefKeys.ACCOUNT_NAME.key, "") ?: ""

    internal fun invalidateCaches() {
        SavedAccounts.invalidate()
        accountCache.clear()
    }

    /** The account [npub] (the current one when null), decrypted once and then cached; null if not saved. */
    suspend fun load(context: Context, npub: String? = null): Account? {
        val target = npub ?: SavedAccounts.current(context) ?: return null
        accountCache.get(target)?.let { return it }
        if (!SavedAccounts.contains(context, target)) return null
        return decrypt(context, target)?.also { account ->
            accountCache.put(target, account)
            if (target in accountRepository.keystoreFailedAccounts.value) {
                accountRepository.keystoreFailedAccounts.value -= target
            }
        }
    }

    private suspend fun decrypt(context: Context, npub: String): Account? {
        val privKey = decryptPrivateKey(context, npub) ?: return null
        val prefs = accountPreferences(context, npub)
        val saveIsIncomplete = prefs.getString(AccountPrefKeys.NOSTR_PUBKEY.key, null) == null
        if (saveIsIncomplete) return null
        return Account(
            signer = NostrSignerInternal(KeyPair(privKey.hexToByteArray())),
            name = MutableStateFlow(prefs.getString(AccountPrefKeys.ACCOUNT_NAME.key, "") ?: ""),
            picture = MutableStateFlow(prefs.getString(AccountPrefKeys.PROFILE_URL.key, "") ?: ""),
            signPolicy = prefs.getInt(AccountPrefKeys.SIGN_POLICY.key, 1),
            didBackup = prefs.getBoolean(AccountPrefKeys.DID_BACKUP.key, true),
            scope = appScope,
            appContext = context.applicationContext,
        )
    }

    private suspend fun decryptPrivateKey(context: Context, npub: String): String? = try {
        DataStoreAccess.getEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY)
    } catch (e: InvalidKeyException) {
        keyFailure(context, npub, "AndroidKeyStore key for $npub is broken (device KeyMint may not support key upgrade). Account skipped.", e)
    } catch (e: KeyStoreException) {
        keyFailure(context, npub, "KeyStore operation failed for $npub. Account skipped.", e)
    }

    private fun keyFailure(context: Context, npub: String, message: String, e: Exception): String? {
        if (KeystorePolicy.isLockedOut(context)) {
            AmberLog.d(Amber.TAG, "Device locked: account $npub loads after unlock")
            return null
        }
        AmberLog.e(Amber.TAG, message, e)
        accountRepository.keystoreFailedAccounts.value = (accountRepository.keystoreFailedAccounts.value + npub).distinct()
        return null
    }
}
