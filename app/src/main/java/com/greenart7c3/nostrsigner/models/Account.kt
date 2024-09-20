package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.signers.NostrSignerInternal

@Stable
class Account(
    val keyPair: KeyPair,
    val signer: NostrSignerInternal = NostrSignerInternal(keyPair),
    var name: String,
    var useProxy: Boolean,
    var proxyPort: Int,
    var language: String?,
    var allowNewConnections: Boolean,
    var signPolicy: Int,
    var seedWords: Set<String>,
) {
    val saveable: AccountLiveData = AccountLiveData(this)

    fun createAuthEvent(
        relayUrl: String,
        challenge: String,
        onReady: (RelayAuthEvent) -> Unit,
    ) {
        RelayAuthEvent.create(relayUrl, challenge, signer, onReady = onReady)
    }
}

class AccountLiveData(account: Account) : LiveData<AccountState>(AccountState(account))

@Immutable
class AccountState(val account: Account)
