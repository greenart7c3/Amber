package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest

@Immutable
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
    val signerPrivKey: String = "",
    val clientMetadata: BunkerClientMetadata? = null,
) {
    /**
     * Identity of this request in the pending queue and the approval UI. The
     * JSON-RPC id is chosen by the client, so two clients (or one client on two
     * accounts) can reuse it; it is only unique per (account, client key).
     */
    val queueKey: String get() = "$currentAccount|$localKey|${request.id}"
}
