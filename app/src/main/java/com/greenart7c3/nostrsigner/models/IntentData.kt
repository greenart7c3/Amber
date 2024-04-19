package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.MutableState
import com.greenart7c3.nostrsigner.service.BunkerRequest
import com.vitorpamplona.quartz.encoders.HexKey

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
    val rememberMyChoice: MutableState<Boolean>,
    val bunkerRequest: BunkerRequest?,
    val route: String?
)
