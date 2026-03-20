package com.greenart7c3.nostrsigner.models

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest

enum class EncryptionType {
    NIP44,
    NIP04,
}

data class AmberBunkerRequest(
    val request: BunkerRequest,
    val localKey: String,
    val relays: List<NormalizedRelayUrl>,
    val currentAccount: String,
    val nostrConnectSecret: String,
    val closeApplication: Boolean,
    val name: String,
    val signedEvent: Event?,
    val encryptedData: EncryptedDataKind?,
    val encryptionType: EncryptionType,
    val isNostrConnectUri: Boolean,
)
