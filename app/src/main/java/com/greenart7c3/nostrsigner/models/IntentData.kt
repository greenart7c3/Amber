package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A request from a calling app, queued for an approval screen.
 *
 * @property nip44v3Kind NIP-44 v3 context kind; required, with [nip44v3Scope], when [type] is one
 * of the NIP44_V3_* variants and ignored otherwise.
 * @property owner the Amber screen (MainViewModel.ownerId) that queued this request. Each calling
 * app gets its own SignerActivity, but the queue is shared: a screen shows, answers and returns
 * only the requests it owns, so one app's request can never be answered to another app.
 */
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
    val nip44v3Kind: Int? = null,
    val nip44v3Scope: String = "",
    val owner: String = "",
    val acceptResultUri: Boolean = false,
    val explicitSignatureOnly: Boolean = false,
) {
    /** Unique across screens: two apps may both use the same request id. */
    val queueKey: String get() = "$owner:$id"
}
