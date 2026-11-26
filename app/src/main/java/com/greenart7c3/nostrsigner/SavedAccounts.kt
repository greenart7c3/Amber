package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Immutable
data class AccountInfo(
    val npub: String,
    val hasPrivKey: Boolean,
)

/**
 * The accounts Amber holds (npubs only, no keys), which one is current and which are
 * favorites, kept in the app-wide preference file and cached for the process.
 * Renaming one of its preference keys loses the stored value.
 */
object SavedAccounts {
    private const val COMMA = ","

    private const val CURRENT_ACCOUNT = "currently_logged_in_account"
    private const val SAVED_ACCOUNTS = "all_saved_accounts"
    private const val FAVORITE_ACCOUNTS = "favorite_accounts"

    private var current: String? = null
    private var saved: List<String>? = null
    private var favorites: MutableStateFlow<Set<String>>? = null

    fun all(context: Context): List<AccountInfo> = list(context).map { npub -> AccountInfo(npub, true) }.toSet().toList()

    fun current(context: Context): String? {
        if (current == null) {
            AmberLog.d(Amber.TAG, "currentAccount is null fetching from shared preferences")
            current = appPreferences(context).getString(CURRENT_ACCOUNT, null)
            if (current == null) {
                AmberLog.d(Amber.TAG, "currentAccount is null fetching from all saved accounts")
                current = all(context).firstOrNull()?.npub
            }
        }
        return current
    }

    fun switchTo(context: Context, npub: String) {
        if (current != npub) {
            current = npub
            appPreferences(context).edit { putString(CURRENT_ACCOUNT, npub) }
        }
    }

    fun contains(context: Context, npub: String): Boolean = list(context).contains(npub)

    internal fun list(context: Context): List<String> {
        if (saved == null) {
            saved = appPreferences(context).getString(SAVED_ACCOUNTS, null)?.split(COMMA) ?: listOf()
        }
        return saved!!
    }

    internal fun replace(context: Context, accounts: List<String>) {
        if (saved != accounts) {
            saved = accounts
            appPreferences(context).edit { putString(SAVED_ACCOUNTS, accounts.joinToString(COMMA).ifBlank { null }) }
        }
    }

    internal fun select(context: Context, npub: String) {
        switchTo(context, npub)
        if (npub !in list(context)) replace(context, list(context) + npub)
    }

    internal fun remove(context: Context, npub: String) {
        val accounts = list(context)
        if (npub in accounts) replace(context, accounts - npub)
    }

    /**
     * The npubs the user marked as favorites, which the account pickers list first. Keyed by
     * npub, so renaming an account keeps it and logging it out clears it.
     */
    fun favorites(context: Context): StateFlow<Set<String>> = favoritesFlow(context)

    @Synchronized
    private fun favoritesFlow(context: Context): MutableStateFlow<Set<String>> = favorites ?: MutableStateFlow(
        appPreferences(context).getStringSet(FAVORITE_ACCOUNTS, null)?.toSet() ?: emptySet(),
    ).also { favorites = it }

    @Synchronized
    fun setFavorite(context: Context, npub: String, favorite: Boolean) {
        val flow = favoritesFlow(context)
        val updated = if (favorite) flow.value + npub else flow.value - npub
        if (updated == flow.value) return
        flow.value = updated
        appPreferences(context).edit {
            if (updated.isEmpty()) remove(FAVORITE_ACCOUNTS) else putStringSet(FAVORITE_ACCOUNTS, updated)
        }
    }

    internal fun invalidate() {
        current = null
        saved = null
    }
}
