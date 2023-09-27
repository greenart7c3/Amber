package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.greenart7c3.nostrsigner.service.KeyPair

@Stable
class Account(
    val keyPair: KeyPair,
    var name: String,
    var savedApps: MutableMap<String, Boolean>
) {
    val saveable: AccountLiveData = AccountLiveData(this)
}

class AccountLiveData(account: Account) : LiveData<AccountState>(AccountState(account))

@Immutable
class AccountState(val account: Account)
