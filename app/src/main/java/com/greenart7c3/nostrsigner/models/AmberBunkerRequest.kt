package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.greenart7c3.nostrsigner.ui.RememberType
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
    val checked: MutableState<Boolean> = mutableStateOf(true),
    val rememberType: MutableState<RememberType> = mutableStateOf(RememberType.NEVER),
    val encryptionType: EncryptionType,
)
