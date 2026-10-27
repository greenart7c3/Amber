package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The account a batched request is signed and recorded under. */
internal fun batchAccountNpub(currentAccount: String, activeNpub: String): String = currentAccount.ifBlank { activeNpub }

/** Every account a batch confirm acts as, in the order first seen. */
internal fun batchAccountNpubs(currentAccounts: List<String>, activeNpub: String): List<String> = currentAccounts.map { batchAccountNpub(it, activeNpub) }.distinct()

/**
 * The accounts a batch confirm signs as. Confirm signs and records each
 * request under its own account, so a single-account batch shows it once at
 * the top ([SigningAsHeader]) and a mixed one shows on each card the account
 * that card is signed as ([forCard]).
 */
class BatchAccounts(
    private val activeNpub: String,
    private val npubs: List<String>,
    private val accounts: Map<String, Account>,
) {
    val isMixed: Boolean get() = npubs.size > 1

    @Composable
    fun SigningAsHeader() {
        if (!isMixed) npubs.firstOrNull()?.let { accounts[it] }?.let { SigningAs(it) }
    }

    /** The account to show on a card, or null when the header shows it. */
    fun forCard(currentAccount: String): Account? = if (isMixed) accounts[batchAccountNpub(currentAccount, activeNpub)] else null
}

@Composable
fun rememberBatchAccounts(currentAccounts: List<String>, active: Account): BatchAccounts {
    val context = LocalContext.current
    val npubs = remember(currentAccounts, active) { batchAccountNpubs(currentAccounts, active.npub) }
    val accounts by produceState(mapOf(active.npub to active), npubs) {
        value = npubs.mapNotNull { npub ->
            val account = if (npub == active.npub) active else withContext(Dispatchers.IO) { LocalPreferences.loadFromEncryptedStorage(context, npub) }
            account?.let { npub to it }
        }.toMap()
    }
    return BatchAccounts(active.npub, npubs, accounts)
}
