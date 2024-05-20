package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.bechToBytes
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
open class ToastMsg()

@Immutable
class StringToastMsg(val title: String, val msg: String) : ToastMsg()

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

    private fun tryLoginExistingAccount(
        route: String?,
        npub: String?,
    ) {
        var currentUser = npub ?: LocalPreferences.currentAccount()
        if (currentUser != null && !LocalPreferences.containsAccount(currentUser)) {
            currentUser = LocalPreferences.currentAccount()
        }
        LocalPreferences.loadFromEncryptedStorage(currentUser)?.let {
            startUI(it, route)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun prepareLogoutOrSwitch() {
        when (val state = accountContent.value) {
            is AccountState.LoggedIn -> {
                GlobalScope.launch(Dispatchers.Main) {
                    state.account.saveable.removeObserver(saveListener)
                }
            }
            else -> {}
        }

        _accountContent.update { AccountState.LoggedOff }
    }

    fun logOff(npub: String) {
        prepareLogoutOrSwitch()
        LocalPreferences.updatePrefsForLogout(npub)
        tryLoginExistingAccount(null, null)
    }

    fun switchUser(
        npub: String,
        route: String?,
    ) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(npub)
        tryLoginExistingAccount(route, npub)
    }

    fun startUI(
        key: String,
        password: String,
        route: String?,
        useProxy: Boolean,
        proxyPort: Int,
    ) {
        val account =
            if (key.startsWith("ncryptsec")) {
                val newKey =
                    CryptoUtils.decryptNIP49(key, password)
                        ?: throw Exception("Could not decrypt key with provided password")
                Account(KeyPair(Hex.decode(newKey)), name = "", useProxy = useProxy, proxyPort = proxyPort, language = null)
            } else if (key.startsWith("nsec")) {
                Account(KeyPair(privKey = key.bechToBytes()), name = "", useProxy = useProxy, proxyPort = proxyPort, language = null)
            } else {
                Account(KeyPair(Hex.decode(key)), name = "", useProxy = useProxy, proxyPort = proxyPort, language = null)
            }

        LocalPreferences.updatePrefsForLogin(account)
        startUI(account, route)
    }

    fun newKey(
        useProxy: Boolean,
        proxyPort: Int,
    ) {
        val account = Account(KeyPair(), name = "", useProxy = useProxy, proxyPort = proxyPort, language = null)
        LocalPreferences.updatePrefsForLogin(account)
        startUI(account, null)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startUI(
        account: Account,
        route: String?,
    ) {
        _accountContent.update { AccountState.LoggedIn(account, route) }

        GlobalScope.launch(Dispatchers.Main) {
            account.saveable.observeForever(saveListener)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val saveListener: (com.greenart7c3.nostrsigner.models.AccountState) -> Unit = {
        GlobalScope.launch(Dispatchers.IO) {
            LocalPreferences.saveToEncryptedStorage(it.account)
        }
    }
}
