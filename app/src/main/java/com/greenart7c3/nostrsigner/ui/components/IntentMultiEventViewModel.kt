package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.Result
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.relayAuthRememberType
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The batch NIP-55 screen: the per-group choices (how long to remember, relay and
 * decryption scope) and confirming every request at once, each under its own account.
 * The requests themselves are passed in: more can join while the screen is open.
 */
@Suppress("LongParameterList") // Constructor injection: one parameter per dependency.
class IntentMultiEventViewModel(
    // The Application context: loads the other accounts a batch signs as.
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    // Confirming finishes even if the screen goes away.
    private val appScope: CoroutineScope,
    private val packageName: String?,
    private val account: Account,
) : ViewModel() {
    val choices = RequestGroupChoices()

    /** Approves every request except relays outside the auth whitelist. */
    fun initChoices(intents: List<IntentData>) {
        MultiEventScreenIntents.rememberType = RememberType.NEVER
        // checkedStates holds the per-request decision: true = Approve, false = Deny.
        // It is shared by every open Amber screen, so set only this screen's keys
        // (owner-scoped queueKeys) instead of clearing another screen's choices.
        intents.forEach { MultiEventScreenIntents.checkedStates[it.queueKey] = !isBlocked(it) }
    }

    fun setGroupApproved(intents: List<IntentData>, approve: Boolean) {
        MultiEventScreenIntents.checkedStates.putAll(intents.associate { it.queueKey to (approve && !isBlocked(it)) })
    }

    fun confirm(
        intents: List<IntentData>,
        activity: Activity?,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        onLoading: (Boolean) -> Unit,
    ) {
        onLoading(true)
        appScope.launch(Dispatchers.IO) {
            try {
                onRemoveIntentData(intents, IntentResultType.REMOVE)
                val localKey = packageName ?: return@launch
                val results = mutableListOf<Result>()
                var closeApp = true
                // Each request is signed under its own account.
                for ((npub, accountIntents) in intents.groupBy { batchAccountNpub(it.currentAccount, account.npub) }) {
                    val thisAccount = if (npub == account.npub) account else LocalPreferences.loadFromEncryptedStorage(appContext, npub)
                    if (thisAccount != null && !confirmForAccount(thisAccount, localKey, accountIntents, results)) {
                        closeApp = false
                    }
                }
                if (results.isNotEmpty()) sendResultIntent(activity, results)
                finishActivity(activity, closeApp)
            } finally {
                onLoading(false)
            }
        }
    }

    /** Saves the remembered choices and the history of [account]'s requests; returns whether its app closes Amber afterwards. */
    private suspend fun confirmForAccount(account: Account, localKey: String, intents: List<IntentData>, results: MutableList<Result>): Boolean {
        val dao = accountRepository.dao(account.npub)
        val application = dao.getByKey(localKey) ?: newApplication(localKey, account)
        var permissionsChanged = false
        val history = intents.map { intentData ->
            // A relay outside the auth whitelist is rejected without remembering it, as on the single screen.
            val blocked = isBlocked(intentData)
            val approved = !blocked && (MultiEventScreenIntents.checkedStates[intentData.queueKey] ?: true)
            val groupKey = requestGroupKey(intentData.type, intentData.event?.kind, intentData.encryptedData, intentData.nip44v3Kind)
            val rememberType = if (blocked) RememberType.NEVER else choices.rememberType(groupKey)
            if (rememberType != RememberType.NEVER) {
                rememberChoice(application, localKey, intentData, groupKey, approved, rememberType)
                permissionsChanged = true
            }
            batchResult(intentData, approved)?.let(results::add)
            batchHistoryEntry(localKey, intentData, approved)
        }
        if (permissionsChanged || application.application.key.isBlank()) {
            dao.insertApplicationWithPermissions(application)
        }
        accountRepository.getHistoryDatabase(account.npub).dao().addHistory(history, account.npub)
        return application.application.closeApplication
    }

    private fun rememberChoice(
        application: ApplicationWithPermissions,
        localKey: String,
        intentData: IntentData,
        groupKey: RequestGroupKey,
        approved: Boolean,
        rememberType: RememberType,
    ) {
        val type = intentData.type
        if (type == SignerType.SIGN_EVENT) {
            val event = intentData.event!!
            val allRelays = event.kind == RELAY_AUTH_KIND && choices.relayAuthScope(groupKey) == RelayAuthScope.ALL
            val signRelay = when {
                event.kind != RELAY_AUTH_KIND -> ""
                allRelays -> "*"
                else -> RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))
            }
            // A blank relay-auth relay can never match a per-relay lookup and reads as the
            // all-relays wildcard: relayAuthRememberType remembers "just this time" instead.
            AmberUtils.updatePermission(
                application,
                localKey,
                type,
                event.kind,
                approved,
                relayAuthRememberType(allRelays = allRelays, relayUrl = signRelay, rememberType = rememberType),
                relay = signRelay,
                encryptedData = intentData.encryptedData,
            )
        } else {
            val decryptTypeScope = choices.decryptScope(groupKey)
            val isV3 = type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT
            AmberUtils.updatePermission(
                application,
                localKey,
                type,
                if (isV3 && decryptTypeScope == DecryptTypeScope.SPECIFIC) intentData.nip44v3Kind else null,
                approved,
                rememberType,
                encryptedData = intentData.encryptedData,
                decryptTypeScope = decryptTypeScope,
            )
        }
    }

    private fun isBlocked(intentData: IntentData) = isBlockedByAuthWhitelist(intentData.event, settingsStore.settings.authWhitelist)

    companion object {
        @Suppress("LongParameterList") // Mirrors the constructor.
        fun factory(
            appContext: Context,
            settingsStore: SettingsStore,
            accountRepository: AccountRepository,
            appScope: CoroutineScope,
            packageName: String?,
            account: Account,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { IntentMultiEventViewModel(appContext, settingsStore, accountRepository, appScope, packageName, account) }
        }
    }
}

private const val RELAY_AUTH_KIND = 22242

/** What the user picked above each request group; read when confirming. */
@Stable
class RequestGroupChoices {
    private val rememberTypes = mutableStateMapOf<RequestGroupKey, RememberType>()
    private val relayAuthScopes = mutableStateMapOf<RequestGroupKey, RelayAuthScope>()
    private val decryptScopes = mutableStateMapOf<RequestGroupKey, DecryptTypeScope>()

    fun rememberType(key: RequestGroupKey): RememberType = rememberTypes[key] ?: RememberType.NEVER

    fun setRememberType(key: RequestGroupKey, value: RememberType) {
        rememberTypes[key] = value
    }

    fun relayAuthScope(key: RequestGroupKey): RelayAuthScope = relayAuthScopes[key] ?: RelayAuthScope.SPECIFIC

    fun setRelayAuthScope(key: RequestGroupKey, value: RelayAuthScope) {
        relayAuthScopes[key] = value
    }

    fun decryptScope(key: RequestGroupKey): DecryptTypeScope = decryptScopes[key] ?: defaultDecryptTypeScope(key.type)

    fun setDecryptScope(key: RequestGroupKey, value: DecryptTypeScope) {
        decryptScopes[key] = value
    }
}

/** What the caller gets back for one request: the signature or result, a rejection, or nothing (an encryption that failed). */
internal fun batchResult(intentData: IntentData, approved: Boolean): Result? {
    if (!approved) return Result(null, signature = null, result = null, id = intentData.id, rejected = true)
    val value = if (intentData.type == SignerType.SIGN_EVENT) {
        val event = intentData.event!!
        // An anonymous zap request is returned whole: its signer is a throwaway key.
        if (event is ZapRequestEvent && event.tags.any { tag -> tag.any { it == "anon" } }) event.toJson() else event.sig
    } else {
        intentData.encryptedData?.result ?: return null
    }
    return Result(null, signature = value, result = value, id = intentData.id, rejected = null)
}

internal fun batchHistoryEntry(localKey: String, intentData: IntentData, approved: Boolean): HistoryEntity {
    val type = intentData.type
    if (type == SignerType.SIGN_EVENT) {
        val event = intentData.event!!
        return HistoryEntity(0, localKey, type.toString(), event.kind, TimeUtils.now(), approved, content = event.toJson())
    }
    val isDecrypt = type == SignerType.NIP04_DECRYPT || type == SignerType.NIP44_DECRYPT || type == SignerType.DECRYPT_ZAP_EVENT
    return HistoryEntity(0, localKey, type.toString(), null, TimeUtils.now(), approved, content = if (isDecrypt) intentData.encryptedData?.result ?: "" else intentData.data)
}

private fun newApplication(localKey: String, account: Account) = ApplicationWithPermissions(
    application = ApplicationEntity(
        localKey,
        "",
        listOf(),
        "",
        "",
        "",
        account.hexKey,
        true,
        "",
        false,
        account.signPolicy,
        true,
        0L,
        lastUsed = TimeUtils.now(),
    ),
    permissions = mutableListOf(),
)

private fun finishActivity(activity: Activity?, closeApp: Boolean) {
    activity?.intent = null
    if (closeApp) {
        activity?.finishAndRemoveTask()
    }
}

private fun sendResultIntent(activity: Activity?, results: List<Result>) {
    val json = Permission.mapper.writeValueAsString(results)
    val intent = Intent()
    intent.putExtra("results", json)
    activity?.setResult(Activity.RESULT_OK, intent)
}
