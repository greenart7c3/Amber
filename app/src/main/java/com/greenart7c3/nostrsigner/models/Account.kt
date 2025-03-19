package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent

@Stable
class Account(
    val signer: NostrSignerInternal,
    var name: String,
    var useProxy: Boolean,
    var proxyPort: Int,
    var language: String?,
    var allowNewConnections: Boolean,
    var signPolicy: Int,
    var seedWords: Set<String>,
) {
    val saveable: AccountLiveData = AccountLiveData(this)
    val hexKey: HexKey = signer.keyPair.pubKey.toHexKey()
    val npub: String = signer.keyPair.pubKey.toNpub()

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
