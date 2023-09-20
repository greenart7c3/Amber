package com.greenart7c3.nostrsigner.ui

import com.greenart7c3.nostrsigner.models.Account

sealed class AccountState {
    object LoggedOff : AccountState()
    class LoggedIn(val account: Account, val route: String?) : AccountState()
}
