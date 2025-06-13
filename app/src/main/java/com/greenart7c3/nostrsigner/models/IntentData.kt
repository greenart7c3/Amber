package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.MutableState
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

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
    val checked: MutableState<Boolean>,
    val rememberType: MutableState<RememberType>,
    val bunkerRequest: BunkerRequest?,
    val route: String?,
    val event: Event?,
    val encryptedData: String?,
    val isNostrConnectURI: Boolean = false,
)
