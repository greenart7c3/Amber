package com.greenart7c3.nostrsigner.database

import android.app.Application
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** Every account's Room databases, opened on first use and kept open until the account logs out. */
class AccountDatabases(
    // Created with the Application, before its context is attached: read it only when a database opens.
    private val application: Application,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) : AccountRepository {
    private val databases = ConcurrentHashMap<String, AppDatabase>()
    private val logDatabases = ConcurrentHashMap<String, LogDatabase>()
    private val historyDatabases = ConcurrentHashMap<String, HistoryDatabase>()
    private val cachingDaos = ConcurrentHashMap<String, CachingApplicationDao>()

    override val keystoreFailedAccounts = MutableStateFlow<List<String>>(emptyList())

    // computeIfAbsent (not check-then-put): AppDatabase.getDatabase builds a
    // fresh RoomDatabase every call, so a racing second caller would build a
    // duplicate that loses the put() and leaks its SQLiteConnection (caught by
    // StrictMode's CloseGuard on finalize).
    override fun getDatabase(npub: String): AppDatabase = databases.computeIfAbsent(npub) {
        AppDatabase.getDatabase(application.applicationContext, npub)
    }

    /**
     * Returns the per-account permission DAO with read-through caching. All hot-path
     * permission lookups should go through this; writes routed through it also
     * invalidate the cache.
     */
    override fun dao(npub: String): ApplicationDao {
        cachingDaos[npub]?.let { return it }
        return cachingDaos.computeIfAbsent(npub) {
            CachingApplicationDao(getDatabase(npub).dao())
        }
    }

    override fun getLogDatabase(npub: String): LogDatabase = logDatabases.computeIfAbsent(npub) {
        LogDatabase.getDatabase(application.applicationContext, npub, settingsStore).also { LogRetentionManager.watch(application, scope, npub, it) }
    }

    override fun getHistoryDatabase(npub: String): HistoryDatabase = historyDatabases.computeIfAbsent(npub) {
        HistoryDatabase.getDatabase(application.applicationContext, npub, settingsStore, this).also { LogRetentionManager.watch(application, scope, npub, it) }
    }

    /**
     * Closes and evicts every cached Room handle for [npub] so file locks and
     * worker threads are released. Called from the logout path; without it each
     * removed account leaks 3 open Room connections forever.
     */
    override fun closeDatabasesFor(npub: String) {
        databases.remove(npub)?.runCatching { close() }
        logDatabases.remove(npub)?.runCatching { close() }
        historyDatabases.remove(npub)?.runCatching { close() }
        cachingDaos.remove(npub)
        ApplicationNameCache.clearForAccount(npub)
    }

    override fun getSavedRelays(account: Account): Set<NormalizedRelayUrl> = buildSet {
        getDatabase(account.npub).dao().getAllRelayLists().forEach { addAll(it.relays) }
    }
}
