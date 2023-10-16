package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.greenart7c3.nostrsigner.service.KeyPair

class History(
    val appName: String,
    val type: String,
    val time: Long
)

@Stable
class Account(
    val keyPair: KeyPair,
    var name: String,
    var savedApps: MutableMap<String, Boolean>,
    var history: MutableList<History> = mutableListOf()
) {
    val saveable: AccountLiveData = AccountLiveData(this)
}

class AccountLiveData(account: Account) : LiveData<AccountState>(AccountState(account))

@Immutable
class AccountState(val account: Account)
