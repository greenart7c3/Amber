package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.KeyPair
import com.vitorpamplona.quartz.encoders.bechToBytes
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class AccountStateViewModel(npub: String?) : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
    val accountContent = _accountContent.asStateFlow()

    init {
        tryLoginExistingAccount(null, npub)
    }

    private fun tryLoginExistingAccount(route: String?, npub: String?) {
        val currentUser = npub ?: LocalPreferences.currentAccount()
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

    fun switchUser(npub: String, route: String?) {
        prepareLogoutOrSwitch()
        LocalPreferences.switchToAccount(npub)
        tryLoginExistingAccount(route, npub)
    }

    fun startUI(key: String, route: String?) {
        val account = if (key.startsWith("nsec")) {
            Account(KeyPair(privKey = key.bechToBytes()), "", mutableMapOf())
        } else {
            Account(KeyPair(Hex.decode(key)), "", mutableMapOf())
        }

        LocalPreferences.updatePrefsForLogin(account)
        startUI(account, route)
    }

    fun newKey() {
        val account = Account(KeyPair(), "", mutableMapOf())
        LocalPreferences.updatePrefsForLogin(account)
        startUI(account, null)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startUI(account: Account, route: String?) {
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
