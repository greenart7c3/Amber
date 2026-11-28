package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.navigation.externalRoute
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Stable
@SuppressLint("StaticFieldLeak")
class MainViewModel(val context: Context) : ViewModel() {
    /** The allowed "route" extra of the last intent, until [RouteNavigationEffect] opens it. */
    val pendingRoute = MutableStateFlow<String?>(null)

    /**
     * Intent IDs added by this ViewModel instance, so onDestroy removes only the intents
     * this activity owns and never those a newer SignerActivity instance already added.
     */
    val addedIntentIds = mutableSetOf<String>()

    /** Tags the requests this screen queues; see [IntentData.owner]. */
    val ownerId: String = UUID.randomUUID().toString()

    /** The queued requests this screen owns. */
    fun ownIntents(): List<IntentData> = IntentUtils.intents.value.filter { it.owner == ownerId }

    /**
     * The account to show: the saved one [userFromIntent] names, else the one the first
     * queued request (intent or bunker) signs as, else the current saved account or the
     * first saved one. Falls back to the last two when a key does not decode.
     */
    fun getAccount(userFromIntent: String?): String? {
        val account = try {
            savedAccount(userFromIntent) ?: requestedAccount() ?: fallbackAccount()
        } catch (e: Exception) {
            AmberLog.e(Amber.TAG, "Error getting account", e)
            fallbackAccount()
        }
        AmberLog.d(Amber.TAG, "getAccount: $account")
        return account
    }

    private fun npubOf(npubOrHexKey: String): String = if (npubOrHexKey.startsWith("npub")) npubOrHexKey else Hex.decode(npubOrHexKey).toNpub()

    private fun savedAccount(userFromIntent: String?): String? {
        if (userFromIntent.isNullOrBlank()) return null
        return npubOf(userFromIntent).takeIf { SavedAccounts.contains(context, it) }
    }

    private fun requestedAccount(): String? {
        val intentKeys = ownIntents().mapNotNull { it.event?.pubKey }.filter { it.isNotBlank() }
        val bunkerKeys = BunkerRequestUtils.getBunkerRequests().mapNotNull {
            (Nip19Parser.uriToRoute(it.currentAccount)?.entity as? NPub)?.hex
        }
        return (intentKeys + bunkerKeys).firstOrNull()?.let { Hex.decode(it).toNpub() }
    }

    private fun fallbackAccount(): String? {
        val current = SavedAccounts.current(context)
        if (current != null && SavedAccounts.contains(context, current)) return current
        return SavedAccounts.all(context).firstOrNull()?.npub
    }

    fun showBunkerRequests(accountStateViewModel: AccountStateViewModel? = null) {
        val requests =
            BunkerRequestUtils.getBunkerRequests().map {
                it.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            if (requests.isNotEmpty()) {
                switchToRequestedAccount(getAccount(null), accountStateViewModel)
            }
        }
    }

    /**
     * Switches to the saved account [requested] (an npub or a hex key) names when it is
     * not the current one, opening its incoming requests. A blank or unknown key changes
     * nothing. Shared by the activities and [showBunkerRequests].
     */
    fun switchToRequestedAccount(requested: String?, accountStateViewModel: AccountStateViewModel?) {
        val currentAccount = SavedAccounts.current(context) ?: return
        if (requested.isNullOrBlank() || requested == currentAccount) return
        val npub = npubOf(requested)
        AmberLog.d(Amber.TAG, "Switching account to $npub")
        if (SavedAccounts.contains(context, npub)) {
            accountStateViewModel?.switchUser(npub, Route.IncomingRequest.route)
        }
    }

    /**
     * Queues [intent]'s request, and its allowed "route" extra in [pendingRoute] when
     * [handleRoute] is set. An activity recreated from saved state passes false, since its
     * back stack is restored and the route was already followed.
     */
    fun onNewIntent(
        intent: Intent,
        callingPackage: String?,
        referrer: Uri? = null,
        handleRoute: Boolean = true,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = AccountStorage.load(context)
            account?.let { acc ->
                val route = externalRoute(intent.getStringExtra("route"))
                val intentData = IntentUtils.getIntentData(context, intent, callingPackage, route, acc, referrer)
                if (intentData != null) {
                    IntentUtils.addAll(listOf(intentData.copy(owner = ownerId)))
                    addedIntentIds.add(intentData.id)
                    IntentUtils.persistNativeAppMetadata(context, acc, callingPackage)
                }
                if (handleRoute && route != null) {
                    pendingRoute.value = route
                }
            }
        }
    }
}

/**
 * The screens to open, in order, for an allowed external [route]: a screen reached from a
 * list opens on top of that list, so Back goes there.
 */
fun routeBackStack(route: String): List<String> = when (route) {
    Route.UpdateSettings.route -> listOf(Route.Settings.route, Route.UpdateSettings.route)
    Route.NewApplication.route -> listOf(Route.Applications.route, Route.NewApplication.route)
    else -> listOf(route)
}
