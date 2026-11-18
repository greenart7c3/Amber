package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.bunkerPermission
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What every request type's composable needs about a single bunker request and its saved app. */
@Immutable
data class BunkerSingleRequest(
    val bunkerRequest: AmberBunkerRequest,
    val account: Account,
    val application: ApplicationWithPermissions?,
) {
    val key: String get() = bunkerRequest.localKey
    val type: SignerType get() = nip46SignerType(bunkerRequest.request.method)

    val appName: String get() = sanitizeAppName(application?.application?.name ?: bunkerRequest.name).ifBlank { key.toShortenHex() }

    // Prefer the persisted application icon, falling back to the icon carried by
    // the connect request's client metadata (nostr-protocol/nips#2381).
    val appIcon: String get() = (application?.application?.icon ?: "").ifBlank { bunkerRequest.clientMetadata?.image ?: "" }

    val closeApplication: Boolean get() = application?.application?.closeApplication ?: bunkerRequest.closeApplication

    /** The remembered answer to this request: true accept, false reject, null ask. */
    fun decision(relayUrl: String? = null): Boolean? = bunkerPermission(application, key, bunkerRequest, relayUrl)?.activeDecision(TimeUtils.now())

    /** NIP-44 v3 also honours the app's sign policy. */
    fun nip44v3Decision(): Boolean? = IntentUtils.isRemembered(application?.application?.signPolicy, bunkerPermission(application, key, bunkerRequest))
}

/** The saved app of [request] (null when it has none), as loaded for that request. */
data class LoadedApplication(val request: AmberBunkerRequest, val application: ApplicationWithPermissions?)

/** The choices of an approved connect, kept while the user decides whether it replaces a saved connection. */
data class ConnectApproval(
    val permissions: List<Permission>?,
    val signPolicy: Int,
    val closeApplication: Boolean?,
    val rememberType: RememberType,
    val deleteAfter: Long,
    val account: Account,
)

/** An approved connect of [request] waiting for the user to replace [existingKey]'s connection or create a new one. */
data class ReplaceConnectionPrompt(
    val request: BunkerSingleRequest,
    val approval: ConnectApproval,
    val existingKey: String,
    val existingName: String,
    val canCreateNew: Boolean,
)

/**
 * The single bunker request screen: loads the saved app the request comes from and
 * approves a connect, asking first when it would replace a saved connection. The
 * request itself is passed to each call, never kept: a client chooses the JSON-RPC
 * id, so a later request can reuse this ViewModel's key, and must be answered from
 * its own load (a grant revoked in between must not still apply).
 */
class BunkerSingleEventViewModel(
    private val accountRepository: AccountRepository,
    private val bunkerResponder: BunkerResponder,
    // The connect reply is published even if the screen goes away.
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _loaded = MutableStateFlow<LoadedApplication?>(null)

    /** The saved app last loaded, with the request it was loaded for. */
    val loaded: StateFlow<LoadedApplication?> = _loaded.asStateFlow()

    private val _replacePrompt = MutableStateFlow<ReplaceConnectionPrompt?>(null)
    val replacePrompt: StateFlow<ReplaceConnectionPrompt?> = _replacePrompt.asStateFlow()

    fun load(bunkerRequest: AmberBunkerRequest, npub: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = accountRepository.getDatabase(npub).dao()
            val secret = (bunkerRequest.request as? BunkerRequestConnect)?.secret
            val application = if (!secret.isNullOrBlank()) dao.getBySecret(secret) else dao.getByKey(bunkerRequest.localKey)
            _loaded.value = LoadedApplication(bunkerRequest, application)
        }
    }

    fun approveConnect(request: BunkerSingleRequest, approval: ConnectApproval, onLoading: (Boolean) -> Unit) {
        appScope.launch {
            val existing = connectionToReplace(accountRepository.getDatabase(approval.account.npub).dao(), request.bunkerRequest, request.key)
            if (existing == null) {
                sendConnect(request, approval, oldKey = "", onLoading)
            } else {
                val app = existing.app.application
                _replacePrompt.value = ReplaceConnectionPrompt(
                    request = request,
                    approval = approval,
                    existingKey = app.key,
                    existingName = sanitizeAppName(app.name).ifBlank { app.key.toShortenHex() },
                    canCreateNew = existing.canCreateNew,
                )
            }
        }
    }

    fun replaceConnection(onLoading: (Boolean) -> Unit) {
        _replacePrompt.value?.let { sendConnect(it.request, it.approval, oldKey = it.existingKey, onLoading) }
        _replacePrompt.value = null
    }

    fun createNewConnection(onLoading: (Boolean) -> Unit) {
        _replacePrompt.value?.let { sendConnect(it.request, it.approval, oldKey = "", onLoading) }
        _replacePrompt.value = null
    }

    fun dismissReplacePrompt() {
        _replacePrompt.value = null
    }

    private fun sendConnect(request: BunkerSingleRequest, approval: ConnectApproval, oldKey: String, onLoading: (Boolean) -> Unit) {
        bunkerResponder.sendResult(
            oldKey = oldKey,
            account = approval.account,
            key = request.key,
            response = request.bunkerRequest.nostrConnectSecret.ifBlank { "ack" },
            bunkerRequest = request.bunkerRequest,
            kind = null,
            onLoading = onLoading,
            permissions = approval.permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
            appName = request.appName,
            signPolicy = approval.signPolicy,
            shouldCloseApplication = approval.closeApplication,
            rememberType = approval.rememberType,
            deleteAfter = approval.deleteAfter,
        )
    }

    companion object {
        fun factory(
            accountRepository: AccountRepository,
            bunkerResponder: BunkerResponder,
            appScope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { BunkerSingleEventViewModel(accountRepository, bunkerResponder, appScope) }
        }
    }
}

/** An existing connection a connect approval would replace or merge into. */
internal data class ExistingConnection(
    val app: ApplicationWithPermissions,
    // False when the request reuses the saved app's key: the key is the row's
    // identity, so a second connection can't be created alongside it.
    val canCreateNew: Boolean,
)

/**
 * The saved connection the user must confirm replacing before [bunkerRequest]
 * is approved, or null when the approval creates or updates nothing the user
 * hasn't seen. A nostrconnect URI names the client key itself, and sendResult
 * would otherwise silently reuse an app saved under that key, keeping its
 * grants while taking the URI's relays, name and icon. A bunker:// reconnect
 * under the same key is the normal flow and is not prompted.
 */
internal suspend fun connectionToReplace(
    dao: ApplicationDao,
    bunkerRequest: AmberBunkerRequest,
    key: String,
): ExistingConnection? {
    if (bunkerRequest.isNostrConnectUri) {
        dao.getByKey(key)?.let { return ExistingConnection(it, canCreateNew = false) }
    }
    val sameName = bunkerRequest.name.takeIf { it.isNotBlank() }?.let { dao.getByName(it) } ?: return null
    return ExistingConnection(sameName, canCreateNew = true)
}
