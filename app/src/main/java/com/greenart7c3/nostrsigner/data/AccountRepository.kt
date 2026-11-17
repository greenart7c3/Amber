package com.greenart7c3.nostrsigner.data

import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.database.LogDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow

/** The per-account databases, opened lazily and cached per npub. */
interface AccountRepository {
    fun getDatabase(npub: String): AppDatabase

    /** The permission DAO with read-through caching; hot-path lookups and writes go through it. */
    fun dao(npub: String): ApplicationDao

    fun getLogDatabase(npub: String): LogDatabase

    fun getHistoryDatabase(npub: String): HistoryDatabase

    /** Closes and evicts every cached database of [npub]; called on logout. */
    fun closeDatabasesFor(npub: String)

    /** The relays every connected application of [account] uses. */
    fun getSavedRelays(account: Account): Set<NormalizedRelayUrl>

    /** npubs whose AndroidKeyStore key failed to decrypt (device KeyMint bug). */
    val keystoreFailedAccounts: MutableStateFlow<List<String>>
}
