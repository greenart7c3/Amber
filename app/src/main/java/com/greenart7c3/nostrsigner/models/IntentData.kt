package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Immutable
data class IntentData(
    val data: String,
    val name: String,
    val type: SignerType,
    val pubKey: HexKey,
    val id: String,
    val callBackUrl: String?,
    val compression: CompressionType,
    val returnType: ReturnType,
    val permissions: List<Permission>?,
    val currentAccount: String,
    val route: String?,
    val event: Event?,
    val encryptedData: EncryptedDataKind?,
    val isNostrConnectURI: Boolean = false,
    val unsignedEventKey: HexKey = "",
    // NIP-44 v3 context fields. Required when [type] is one of the
    // NIP44_V3_* variants; ignored otherwise.
    val nip44v3Kind: Int? = null,
    val nip44v3Scope: String = "",
    // The Amber screen (MainViewModel.ownerId) that queued this request. Each
    // calling app gets its own SignerActivity, but the queue is shared: a
    // screen shows, answers and returns only the requests it owns, so one
    // app's request can never be answered to another app.
    val owner: String = "",
) {
    /** Unique across screens: two apps may both use the same request id. */
    val queueKey: String get() = "$owner:$id"
}
