package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.signRememberType
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app a bunker request comes from: the client key, per signing account. */
data class RequestAppKey(val account: String, val key: String)

/** A request group within one app, so remember/scope choices never leak across apps. */
data class AppRequestGroupKey(val app: RequestAppKey, val group: RequestGroupKey)

internal val AmberBunkerRequest.appKey: RequestAppKey get() = RequestAppKey(currentAccount, localKey)

internal fun AmberBunkerRequest.requestGroupKey() = requestGroupKey(
    type = nip46SignerType(request.method),
    eventKind = (request as? BunkerRequestSign)?.event?.kind,
    encryptedData = encryptedData,
    nip44v3Kind = request.nip44v3Kind(),
)

internal fun RequestAppKey.itemKey() = "$account:$key"

internal fun AppRequestGroupKey.itemKey() = "${app.itemKey()}:${group.type.name}:${group.payload?.name ?: ""}:${group.kind ?: ""}"

private const val RELAY_AUTH_KIND = 22242

/** What the user picked above each request group, per app; read when confirming. */
@Stable
class BunkerGroupChoices {
    private val rememberTypes = mutableStateMapOf<AppRequestGroupKey, RememberType>()
    private val relayAuthScopes = mutableStateMapOf<AppRequestGroupKey, RelayAuthScope>()
    private val decryptScopes = mutableStateMapOf<AppRequestGroupKey, DecryptTypeScope>()

    fun rememberType(key: AppRequestGroupKey): RememberType = rememberTypes[key] ?: RememberType.NEVER

    fun setRememberType(key: AppRequestGroupKey, value: RememberType) {
        rememberTypes[key] = value
    }

    fun relayAuthScope(key: AppRequestGroupKey): RelayAuthScope = relayAuthScopes[key] ?: RelayAuthScope.SPECIFIC

    fun setRelayAuthScope(key: AppRequestGroupKey, value: RelayAuthScope) {
        relayAuthScopes[key] = value
    }

    fun decryptScope(key: AppRequestGroupKey): DecryptTypeScope = decryptScopes[key] ?: defaultDecryptTypeScope(key.group.type)

    fun setDecryptScope(key: AppRequestGroupKey, value: DecryptTypeScope) {
        decryptScopes[key] = value
    }
}

/** What [BunkerMultiEventViewModel.savedApp] found for a requesting app: null fields fall back to its request. */
data class SavedAppInfo(val name: String?, val icon: String?)

/**
 * The batch bunker screen: the per-app, per-group choices (how long to remember,
 * relay and decryption scope) and confirming every request at once, each under its
 * own account. The requests are passed in: more can join while the screen is open.
 */
@Suppress("LongParameterList") // Constructor injection: one parameter per dependency.
class BunkerMultiEventViewModel(
    // The Application context: loads the other accounts a batch signs as, cancels notifications.
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
    private val bunkerResponder: BunkerResponder,
    // Confirming publishes the replies even if the screen goes away.
    private val appScope: CoroutineScope,
    private val account: Account,
) : ViewModel() {
    val choices = BunkerGroupChoices()

    /** Approves every request except relays outside the auth whitelist. */
    fun initChoices(batchRequests: List<AmberBunkerRequest>) {
        MultiEventScreenIntents.rememberType = RememberType.NEVER
        // checkedStates holds the per-request decision: true = Approve, false = Deny.
        // It is shared by every open Amber screen, so set only this batch's keys
        // instead of clearing an intent batch's choices on another screen.
        batchRequests.forEach { MultiEventScreenIntents.checkedStates[it.queueKey] = !isBlocked(it) }
    }

    fun setGroupApproved(groupItems: List<AmberBunkerRequest>, approve: Boolean) {
        MultiEventScreenIntents.checkedStates.putAll(groupItems.associate { it.queueKey to (approve && !isBlocked(it)) })
    }

    /**
     * The saved name (sanitized, cached) and icon of the app behind [appKey], under its
     * own account or this screen's; null when that account cannot be loaded.
     */
    suspend fun savedApp(appKey: RequestAppKey): SavedAppInfo? = withContext(Dispatchers.IO) {
        val owner = if (appKey.account.isNotBlank()) AccountStorage.load(appContext, appKey.account) else account
        owner ?: return@withContext null
        val cacheKey = "${owner.npub.toShortenHex()}-${appKey.key}"
        val app = accountRepository.getDatabase(owner.npub).dao().getByKey(appKey.key)?.application
        val name = ApplicationNameCache[cacheKey]
            ?: app?.name?.let(::sanitizeAppName)?.ifBlank { null }?.also { ApplicationNameCache[cacheKey] = it }
        SavedAppInfo(name, app?.icon?.ifBlank { null })
    }

    fun confirm(batchRequests: List<AmberBunkerRequest>, activity: Activity?, onLoading: (Boolean) -> Unit) {
        onLoading(true)
        appScope.launch(Dispatchers.IO) {
            try {
                relayClient.checkForNewRelaysAndUpdateAllFilters()
                val closeApp = batchRequests.any { it.closeApplication }
                // Only this batch's requests: a connect queued meanwhile stays
                // pending, with its notification, for the connect screen.
                val notificationManager = appContext.getSystemService(NotificationManager::class.java)
                batchRequests.forEach {
                    BunkerRequestUtils.remove(it)
                    notificationManager.cancel(it.queueKey.hashCode())
                }
                finishActivity(activity, closeApp)
                for (request in batchRequests) {
                    confirmOne(request)
                }
            } finally {
                onLoading(false)
            }
        }
    }

    private suspend fun confirmOne(request: AmberBunkerRequest) {
        val thisAccount = if (request.currentAccount.isNotBlank()) {
            AccountStorage.load(appContext, request.currentAccount)
        } else {
            account
        } ?: return

        val savedApplication = accountRepository.daos(thisAccount.npub).applicationDao.getByKey(request.localKey)

        // A queued request whose app row is gone was revoked while it waited:
        // answer with an error instead of recreating the connection.
        if (savedApplication == null) {
            BunkerRequestUtils.remove(request)
            AmberUtils.sendBunkerError(
                bunkerResponder,
                account = thisAccount,
                bunkerRequest = request,
                relays = BunkerRequestUtils.responseRelays(request, null, settingsStore.settings.defaultRelays),
                closeApplication = request.closeApplication,
                onLoading = {},
            )
            return
        }

        // A relay outside the auth whitelist is rejected without remembering it,
        // as on the single screen.
        val blockedByWhitelist = isBlocked(request)
        val isApproved = !blockedByWhitelist && (MultiEventScreenIntents.checkedStates[request.queueKey] ?: true)
        val requestType = nip46SignerType(request.request.method)
        val groupKey = AppRequestGroupKey(request.appKey, request.requestGroupKey())
        val rememberType = if (blockedByWhitelist) RememberType.NEVER else choices.rememberType(groupKey)

        if (request.request is BunkerRequestSign) {
            confirmSign(request, thisAccount, savedApplication, groupKey, isApproved, rememberType)
        } else {
            confirmEncryptDecrypt(request, thisAccount, savedApplication, groupKey, requestType, isApproved, rememberType)
        }
    }

    private suspend fun confirmSign(
        request: AmberBunkerRequest,
        thisAccount: Account,
        savedApplication: ApplicationWithPermissions,
        groupKey: AppRequestGroupKey,
        isApproved: Boolean,
        rememberType: RememberType,
    ) {
        val localEvent = request.signedEvent!!
        val dao = accountRepository.daos(thisAccount.npub).permissionWriteDao

        if (rememberType != RememberType.NEVER) {
            val allRelays = localEvent.kind == RELAY_AUTH_KIND && choices.relayAuthScope(groupKey) == RelayAuthScope.ALL
            val signRelay = when {
                localEvent.kind != RELAY_AUTH_KIND -> ""
                allRelays -> "*"
                else -> RelayUrlUtils.extractHostAndPort(AmberEvent.relay(localEvent))
            }
            AmberUtils.acceptOrRejectPermission(
                application = savedApplication,
                key = request.localKey,
                signerType = SignerType.SIGN_EVENT,
                kind = localEvent.kind,
                value = isApproved,
                rememberType = signRememberType(localEvent.kind, allRelays, signRelay, rememberType),
                relay = signRelay,
                encryptedData = request.encryptedData,
                dao = dao,
            )
        }

        dao.insertApplicationWithPermissions(savedApplication)
        accountRepository.getHistoryDatabase(thisAccount.npub).dao().addHistory(
            listOf(
                HistoryEntity(
                    id = 0,
                    pkKey = request.localKey,
                    type = SignerType.SIGN_EVENT.toString(),
                    kind = localEvent.kind,
                    time = TimeUtils.now(),
                    accepted = isApproved,
                    content = localEvent.toJson(),
                ),
            ),
            thisAccount.npub,
        )

        BunkerRequestUtils.remove(request)
        if (isApproved) {
            bunkerResponder.sendBunkerResponse(
                thisAccount,
                request,
                BunkerResponse(request.request.id, localEvent.toJson(), null),
                savedApplication.application.relays,
                onLoading = {},
                onDone = {},
            )
        } else {
            AmberUtils.sendBunkerError(
                bunkerResponder,
                account = thisAccount,
                bunkerRequest = request,
                relays = savedApplication.application.relays,
                closeApplication = savedApplication.application.closeApplication,
                onLoading = {},
            )
        }
    }

    private suspend fun confirmEncryptDecrypt(
        request: AmberBunkerRequest,
        thisAccount: Account,
        savedApplication: ApplicationWithPermissions,
        groupKey: AppRequestGroupKey,
        requestType: SignerType,
        isApproved: Boolean,
        rememberType: RememberType,
    ) {
        val localKey = request.localKey
        val dao = accountRepository.daos(thisAccount.npub).permissionWriteDao

        if (rememberType != RememberType.NEVER) {
            val decryptTypeScope = choices.decryptScope(groupKey)
            val permissionKind = if (requestType == SignerType.NIP44_V3_ENCRYPT || requestType == SignerType.NIP44_V3_DECRYPT) {
                if (decryptTypeScope == DecryptTypeScope.SPECIFIC) request.request.nip44v3Kind() else null
            } else {
                null
            }
            AmberUtils.acceptOrRejectPermission(
                savedApplication,
                localKey,
                requestType,
                permissionKind,
                isApproved,
                rememberType,
                encryptedData = request.encryptedData,
                decryptTypeScope = decryptTypeScope,
                dao = dao,
            )
        }

        dao.insertApplicationWithPermissions(savedApplication)
        accountRepository.getHistoryDatabase(thisAccount.npub).dao().addHistory(
            listOf(
                HistoryEntity(
                    id = 0,
                    pkKey = localKey,
                    type = requestType.toString(),
                    kind = null,
                    time = TimeUtils.now(),
                    accepted = isApproved,
                    content = if (requestType == SignerType.NIP04_DECRYPT || requestType == SignerType.NIP44_DECRYPT || requestType == SignerType.DECRYPT_ZAP_EVENT) {
                        request.encryptedData?.result ?: ""
                    } else {
                        request.request.params.getOrElse(1) { "" }
                    },
                ),
            ),
            thisAccount.npub,
        )

        respondEncryptDecrypt(request, thisAccount, savedApplication, isApproved)
    }

    /** An encryption only has a result once it ran: no value means nothing to answer with. */
    private suspend fun respondEncryptDecrypt(
        request: AmberBunkerRequest,
        thisAccount: Account,
        savedApplication: ApplicationWithPermissions,
        isApproved: Boolean,
    ) {
        if (isApproved) {
            val signature = request.encryptedData?.result
            if (signature != null) {
                BunkerRequestUtils.remove(request)
                bunkerResponder.sendBunkerResponse(
                    thisAccount,
                    request,
                    BunkerResponse(request.request.id, signature, null),
                    savedApplication.application.relays,
                    onLoading = {},
                    onDone = {},
                )
            }
        } else {
            BunkerRequestUtils.remove(request)
            AmberUtils.sendBunkerError(
                bunkerResponder,
                account = thisAccount,
                bunkerRequest = request,
                relays = savedApplication.application.relays,
                closeApplication = savedApplication.application.closeApplication,
                onLoading = {},
            )
        }
    }

    private fun isBlocked(request: AmberBunkerRequest) = isBlockedByAuthWhitelist(request.signedEvent, settingsStore.settings.authWhitelist)

    companion object {
        @Suppress("LongParameterList") // Mirrors the constructor.
        fun factory(
            appContext: Context,
            settingsStore: SettingsStore,
            accountRepository: AccountRepository,
            relayClient: RelayClient,
            bunkerResponder: BunkerResponder,
            appScope: CoroutineScope,
            account: Account,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { BunkerMultiEventViewModel(appContext, settingsStore, accountRepository, relayClient, bunkerResponder, appScope, account) }
        }
    }
}

private fun finishActivity(activity: Activity?, closeApp: Boolean) {
    activity?.intent = null
    if (closeApp) {
        activity?.finishAndRemoveTask()
    }
}
