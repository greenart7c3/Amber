package com.greenart7c3.nostrsigner.ui

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
open class ToastMsg

@Immutable
class StringToastMsg(val title: String, val msg: String) : ToastMsg()

@Immutable
class ConfirmationToastMsg(val title: String, val msg: String, val onOk: () -> Unit) : ToastMsg()

@Immutable
class AcceptRejectToastMsg(val title: String, val msg: String, val onAccept: () -> Unit, val onReject: () -> Unit) : ToastMsg()

@Immutable
class ResourceToastMsg(
    val titleResId: Int,
    val resourceId: Int,
    val params: Array<out String>? = null,
) : ToastMsg()

@Stable
class AccountStateViewModel(npub: String?) : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
    val accountContent = _accountContent.asStateFlow()
    val toasts = MutableSharedFlow<ToastMsg?>(0, 3, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        tryLoginExistingAccount(null, npub)
    }

    fun clearToasts() {
        viewModelScope.launch { toasts.emit(null) }
    }

    fun toast(
        title: String,
        message: String,
    ) {
        viewModelScope.launch { toasts.emit(StringToastMsg(title, message)) }
    }

    fun toast(
        title: String,
        message: String,
        onOk: () -> Unit,
    ) {
        viewModelScope.launch { toasts.emit(ConfirmationToastMsg(title, message, onOk)) }
    }

    fun toast(
        title: String,
        message: String,
        onAccept: () -> Unit,
        onReject: () -> Unit,
    ) {
        viewModelScope.launch { toasts.emit(AcceptRejectToastMsg(title, message, onAccept, onReject)) }
    }

    private fun tryLoginExistingAccount(
        route: String?,
        npub: String?,
        forceLogout: Boolean = false,
    ) {
        var currentUser = npub ?: LocalPreferences.currentAccount(Amber.instance)
        val allAccounts = LocalPreferences.allSavedAccounts(Amber.instance)
        if (currentUser != null && !LocalPreferences.containsAccount(Amber.instance, currentUser) && allAccounts.any { it.npub == currentUser }) {
            currentUser = LocalPreferences.currentAccount(Amber.instance)
        }
        if (forceLogout) {
            currentUser = null
        }
        LocalPreferences.loadFromEncryptedStorageSync(Amber.instance, currentUser)?.let {
            startUI(it, route)
        }
        if (currentUser == null) {
            _accountContent.update { AccountState.LoggedOff }
        }
    }

    private fun prepareLogoutOrSwitch() {
        when (val state = accountContent.value) {
            is AccountState.LoggedIn -> {
                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    state.account.saveable.removeObserver(saveListener)
                }
            }
            else -> {}
        }

        _accountContent.update { AccountState.LoggedOff }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        val shouldLogout = LocalPreferences.updatePrefsForLogout(npub, Amber.instance)
        tryLoginExistingAccount(null, null, forceLogout = shouldLogout)
    }

    fun switchUser(
        npub: String,
        route: String?,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(Amber.instance, npub)
        tryLoginExistingAccount(route, npub)
    }

    fun isValidKey(key: String, password: String): Pair<Boolean, String> {
        try {
            val signer =
                if (key.startsWith("ncryptsec")) {
                    val newKey = Nip49().decrypt(key, password)
                    NostrSignerInternal(KeyPair(Hex.decode(newKey)))
                } else if (key.startsWith("nsec")) {
                    NostrSignerInternal(KeyPair(privKey = key.bechToBytes()))
                } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                    val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
                    NostrSignerInternal(keyPair)
                } else {
                    NostrSignerInternal(KeyPair(Hex.decode(key)))
                }
            return Pair(signer.keyPair.privKey != null, "")
        } catch (e: Exception) {
            return Pair(false, e.message ?: "Unknown error")
        }
    }

    fun startUI(
        key: String,
        password: String,
        route: String?,
        useProxy: Boolean,
        proxyPort: Int,
        signPolicy: Int,
    ) {
        val keyPair =
            if (key.startsWith("ncryptsec")) {
                val newKey = Nip49().decrypt(key, password)
                KeyPair(Hex.decode(newKey))
            } else if (key.startsWith("nsec")) {
                KeyPair(privKey = key.bechToBytes())
            } else if (key.contains(" ") && Nip06().isValidMnemonic(key)) {
                KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
            } else {
                KeyPair(Hex.decode(key))
            }

        val account = Account(
            hexKey = keyPair.pubKey.toHexKey(),
            npub = keyPair.pubKey.toNpub(),
            name = MutableStateFlow(""),
            picture = MutableStateFlow(""),
            signPolicy = signPolicy,
            didBackup = true,
        )

        if (LocalPreferences.allSavedAccounts(Amber.instance).isEmpty()) {
            Amber.instance.settings = Amber.instance.settings.copy(
                useProxy = useProxy,
                proxyPort = proxyPort,
            )
            LocalPreferences.saveSettingsToEncryptedStorage(
                Amber.instance.settings,
            )
        }
        LocalPreferences.updatePrefsForLogin(Amber.instance, account, keyPair.pubKey.toHexKey(), keyPair.privKey?.toHexKey(), null)
        startUI(account, route)
    }

    fun newKey(
        useProxy: Boolean,
        proxyPort: Int,
        signPolicy: Int,
        seedWords: Set<String>,
        name: String,
    ) {
        val key = seedWords.joinToString(separator = " ") { it }
        val keyPair = KeyPair(privKey = Nip06().privateKeyFromMnemonic(key))
        val hexKey = keyPair.pubKey.toHexKey()
        val npub = keyPair.pubKey.toNpub()
        val privKey = keyPair.privKey!!.toHexKey()
        val account = Account(
            hexKey = hexKey,
            npub = npub,
            name = MutableStateFlow(name),
            picture = MutableStateFlow(""),
            signPolicy = signPolicy,
            didBackup = false,
        )
        if (LocalPreferences.allSavedAccounts(Amber.instance).isEmpty()) {
            Amber.instance.settings = Amber.instance.settings.copy(
                useProxy = useProxy,
                proxyPort = proxyPort,
            )
            LocalPreferences.saveSettingsToEncryptedStorage(
                Amber.instance.settings,
            )
            Amber.instance.applicationIOScope.launch {
                Amber.instance.reconnect()
            }
        }
        LocalPreferences.updatePrefsForLogin(Amber.instance, account, hexKey, privKey, key)
        startUI(account, null)
    }

    fun startUI(
        account: Account,
        route: String?,
    ) {
        _accountContent.update { AccountState.LoggedIn(account, route) }

        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
            account.saveable.observeForever(saveListener)
        }
    }

    private val saveListener: (com.greenart7c3.nostrsigner.models.AccountState) -> Unit = {
        Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
            Log.d(Amber.TAG, "Account saved")
            LocalPreferences.saveToEncryptedStorage(Amber.instance, it.account, null, null, null)
        }
    }
}
