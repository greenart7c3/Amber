package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class Account(
    val signer: NostrSignerInternal,
    val name: MutableStateFlow<String>,
    val picture: MutableStateFlow<String>,
    var language: String?,
    var signPolicy: Int,
    var seedWords: Set<String>,
    var didBackup: Boolean,
) {
    val saveable: AccountLiveData = AccountLiveData(this)
    val hexKey: HexKey = signer.keyPair.pubKey.toHexKey()
    val npub: String = signer.keyPair.pubKey.toNpub()

    suspend fun createAuthEvent(
        relayUrl: NormalizedRelayUrl,
        challenge: String,
    ): RelayAuthEvent {
        return RelayAuthEvent.create(relayUrl, challenge, signer)
    }
}

class AccountLiveData(account: Account) : LiveData<AccountState>(AccountState(account))

@Immutable
class AccountState(val account: Account)
