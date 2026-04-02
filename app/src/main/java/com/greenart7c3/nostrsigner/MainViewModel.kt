package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.BunkerProxyClient
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
class MainViewModel(val context: Context) : ViewModel() {
    private val _intents = MutableStateFlow<ImmutableList<IntentData>>(persistentListOf())
    val intents = _intents.asStateFlow()
    var navController: NavHostController? = null

    fun addAll(list: List<IntentData>) {
        val existingIds = intents.value.mapTo(mutableSetOf()) { it.id }
        val newList = list.filter { existingIds.add(it.id) }
        _intents.value = (_intents.value + newList).toPersistentList()
    }

    fun removeAll(intents: List<IntentData>) {
        _intents.value = (_intents.value - intents.toSet()).toPersistentList()
    }

    fun clear() {
        _intents.value = persistentListOf()
    }

    fun getAccount(userFromIntent: String?): String? {
        val currentAccount = LocalPreferences.currentAccount(context)
        try {
            if (!userFromIntent.isNullOrBlank()) {
                if (userFromIntent.startsWith("npub")) {
                    if (LocalPreferences.containsAccount(context, userFromIntent)) {
                        Log.d(Amber.TAG, "getAccount: $userFromIntent")
                        return userFromIntent
                    }
                } else {
                    val localNpub = Hex.decode(userFromIntent).toNpub()
                    if (LocalPreferences.containsAccount(context, localNpub)) {
                        Log.d(Amber.TAG, "getAccount: $localNpub")
                        return localNpub
                    }
                }
            }

            val pubKeys =
                intents.value.mapNotNull {
                    it.event?.pubKey
                }.filter { it.isNotBlank() } + BunkerRequestUtils.getBunkerRequests().mapNotNull {
                    when (val parsed = Nip19Parser.uriToRoute(it.currentAccount)?.entity) {
                        is NPub -> parsed.hex
                        else -> null
                    }
                }

            if (pubKeys.isEmpty()) {
                if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                    Log.d(Amber.TAG, "getAccount: $currentAccount")
                    return currentAccount
                }

                val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
                if (acc != null) {
                    Log.d(Amber.TAG, "getAccount: ${acc.npub}")
                    return acc.npub
                } else {
                    Log.d(Amber.TAG, "getAccount: null")
                    return null
                }
            }

            val npub = Hex.decode(pubKeys.first()).toNpub()
            Log.d(Amber.TAG, "getAccount: $npub")
            return npub
        } catch (e: Exception) {
            Log.e(Amber.TAG, "Error getting account", e)
            if (currentAccount != null && LocalPreferences.containsAccount(context, currentAccount)) {
                Log.d(Amber.TAG, "getAccount: $currentAccount")
                return currentAccount
            }

            val acc = LocalPreferences.allSavedAccounts(context).firstOrNull()
            if (acc != null) {
                Log.d(Amber.TAG, "getAccount: ${acc.npub}")
                return acc.npub
            } else {
                Log.d(Amber.TAG, "getAccount: null")
                return null
            }
        }
    }

    fun showBunkerRequests(accountStateViewModel: AccountStateViewModel? = null) {
        val requests =
            BunkerRequestUtils.getBunkerRequests().map {
                it.copy()
            }

        viewModelScope.launch(Dispatchers.IO) {
            if (requests.isNotEmpty()) {
                val npub = getAccount(null)
                val currentAccount = LocalPreferences.currentAccount(context)
                if (currentAccount != null && npub != null && currentAccount != npub && npub.isNotBlank()) {
                    if (npub.startsWith("npub")) {
                        Log.d(Amber.TAG, "Switching account to $npub")
                        if (LocalPreferences.containsAccount(context, npub)) {
                            accountStateViewModel?.switchUser(npub, Route.IncomingRequest.route)
                        }
                    } else {
                        val localNpub = Hex.decode(npub).toNpub()
                        Log.d(Amber.TAG, "Switching account to $localNpub")
                        if (LocalPreferences.containsAccount(context, localNpub)) {
                            accountStateViewModel?.switchUser(localNpub, Route.IncomingRequest.route)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleProxyIntent(
        account: com.greenart7c3.nostrsigner.models.Account,
        intentData: IntentData,
        packageName: String?,
    ) {
        val result = when (intentData.type) {
            SignerType.GET_PUBLIC_KEY -> account.hexKey
            SignerType.PING -> "pong"
            SignerType.SIGN_EVENT -> BunkerProxyClient.sendRequest(
                account,
                "sign_event",
                listOf(intentData.data),
            )
            SignerType.SIGN_MESSAGE -> BunkerProxyClient.sendRequest(
                account,
                "sign_message",
                listOf(intentData.data),
            )
            SignerType.NIP04_ENCRYPT -> BunkerProxyClient.sendRequest(
                account,
                "nip04_encrypt",
                listOf(intentData.pubKey, intentData.data),
            )
            SignerType.NIP04_DECRYPT -> BunkerProxyClient.sendRequest(
                account,
                "nip04_decrypt",
                listOf(intentData.pubKey, intentData.data),
            )
            SignerType.NIP44_ENCRYPT -> BunkerProxyClient.sendRequest(
                account,
                "nip44_encrypt",
                listOf(intentData.pubKey, intentData.data),
            )
            SignerType.NIP44_DECRYPT -> BunkerProxyClient.sendRequest(
                account,
                "nip44_decrypt",
                listOf(intentData.pubKey, intentData.data),
            )
            else -> null
        }

        val activity = Amber.instance.getMainActivity()
        if (result == null) {
            activity?.setResult(RESULT_OK, Intent().also { it.putExtra("rejected", true) })
            activity?.finishAndRemoveTask()
            return
        }

        val (eventJson, sigValue) = if (intentData.type == SignerType.SIGN_EVENT) {
            try {
                val signedEvent = JacksonMapper.mapper.readValue(result, Event::class.java)
                val sig = if (signedEvent.kind == LnZapRequestEvent.KIND &&
                    signedEvent.tags.any { tag -> tag.any { t -> t == "anon" } }
                ) {
                    result
                } else {
                    signedEvent.sig
                }
                Pair(result, sig)
            } catch (e: Exception) {
                Log.e(Amber.TAG, "Failed to parse signed event from bunker proxy", e)
                activity?.setResult(RESULT_OK, Intent().also { it.putExtra("rejected", true) })
                activity?.finishAndRemoveTask()
                return
            }
        } else {
            Pair(result, result)
        }

        Amber.instance.getHistoryDatabase(account.npub).dao().addHistory(
            HistoryEntity(
                0,
                packageName ?: intentData.pubKey,
                intentData.type.toString(),
                intentData.event?.kind,
                TimeUtils.now(),
                true,
                content = if (intentData.type == SignerType.SIGN_EVENT) eventJson else intentData.data,
            ),
            account.npub,
        )

        if (packageName != null) {
            val resultIntent = Intent()
            resultIntent.putExtra("signature", sigValue)
            resultIntent.putExtra("result", sigValue)
            resultIntent.putExtra("id", intentData.id)
            resultIntent.putExtra("event", eventJson)
            if (intentData.type == SignerType.GET_PUBLIC_KEY) {
                resultIntent.putExtra("package", BuildConfig.APPLICATION_ID)
            }
            activity?.setResult(RESULT_OK, resultIntent)
        }
        activity?.finishAndRemoveTask()
    }

    fun onNewIntent(
        intent: Intent,
        callingPackage: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = LocalPreferences.loadFromEncryptedStorage(context)
            account?.let { acc ->
                val intentData = IntentUtils.getIntentData(context, intent, callingPackage, intent.getStringExtra("route"), acc)
                if (intentData != null) {
                    val targetNpub = intent.getStringExtra("current_user")?.let {
                        IntentUtils.parsePubKey(it)
                    }
                    val targetAccount = if (targetNpub != null) {
                        LocalPreferences.loadFromEncryptedStorageSync(context, targetNpub) ?: acc
                    } else {
                        acc
                    }
                    if (targetAccount.bunkerProxy != null) {
                        handleProxyIntent(targetAccount, intentData, callingPackage)
                        return@launch
                    }
                    addAll(listOf(intentData))

                    intent.getStringExtra("route")?.let {
                        viewModelScope.launch(Dispatchers.Main) {
                            var error = true
                            var count = 0
                            while (error && count < 10) {
                                delay(100)
                                count++
                                try {
                                    navController?.navigate(Route.IncomingRequest.route) {
                                        popUpTo(0)
                                    }
                                    error = false
                                } catch (e: Exception) {
                                    Log.e(Amber.TAG, "Error showing bunker requests", e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
