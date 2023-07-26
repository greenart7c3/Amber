package com.greenart7c3.nostrsigner.ui

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.KeyPair
import com.greenart7c3.nostrsigner.service.bechToBytes
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
class AccountStateViewModel : ViewModel() {
    private val _accountContent = MutableStateFlow<AccountState>(AccountState.LoggedOff)
    val accountContent = _accountContent.asStateFlow()

    init {
        tryLoginExistingAccount()
    }

    private fun tryLoginExistingAccount() {
        LocalPreferences.loadFromEncryptedStorage()?.let {
            startUI(it)
        }
    }

    fun startUI(key: String) {
        if (!key.startsWith("nsec")) {
            throw Exception("Not nsec key")
        }

        val account = Account(KeyPair(privKey = key.bechToBytes()))
        LocalPreferences.updatePrefsForLogin(account)
        startUI(account)
    }

    fun newKey() {
        val account = Account(KeyPair())
        LocalPreferences.updatePrefsForLogin(account)
        startUI(account)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startUI(account: Account) {
        if (account.keyPair.privKey != null) {
            _accountContent.update { AccountState.LoggedIn(account) }
        }

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
