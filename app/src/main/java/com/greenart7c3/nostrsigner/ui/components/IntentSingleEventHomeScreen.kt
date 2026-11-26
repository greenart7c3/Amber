package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.RELAY_AUTH_KIND
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.psbt.PsbtDecoder
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.event_pubkey_is_not_equal_to_current_logged_in_user
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.not_logged_in
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.service.IntentApproval
import com.greenart7c3.nostrsigner.service.IntentReply
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.service.isCrossAccountSignEvent
import com.greenart7c3.nostrsigner.service.isPrivateEvent
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.relayAuthRememberType
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.greenart7c3.nostrsigner.ui.rememberResultCopier
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Immutable
private data class IntentRequest(
    val intentData: IntentData,
    val account: Account,
    val packageName: String?,
    val appName: String,
    val approvalAppName: String,
    val saved: LoadedIntentApplication?,
) {
    val key: String get() = "$packageName"

    fun remembered(relayUrl: String? = null): Boolean? = saved?.remembered(key, relayUrl)

    fun reply(event: String, value: String = event) = IntentReply(intentData, packageName, event, value)
}

@Composable
fun IntentSingleEventHomeScreen(
    horizontalPadding: Dp,
    scaffoldPadding: PaddingValues,
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntentSingleEventViewModel = viewModel(
        key = "intent-single-${account.npub}-$packageName-${intentData.id}",
        factory = IntentSingleEventViewModel.factory(LocalAccountRepository.current),
    ),
) {
    LaunchedEffect(intentData, account.npub, packageName) { viewModel.load(intentData, account.npub, packageName) }
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val loadedForThisRequest = loaded?.takeIf { it.intentData === intentData }
    val appName = sanitizeAppName(loadedForThisRequest?.name ?: packageName ?: intentData.name).ifBlank { "$packageName".toShortenHex() }
    val request = IntentRequest(intentData, account, packageName, appName, applicationName ?: appName, loadedForThisRequest)

    when (intentData.type) {
        SignerType.GET_PUBLIC_KEY -> GetPublicKeyRequest(request, horizontalPadding, scaffoldPadding, onRemoveIntentData, onLoading)
        SignerType.SIGN_PSBT -> SignPsbtRequest(request, onRemoveIntentData, onLoading, modifier)
        SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> Nip44v3Request(request, onRemoveIntentData, onLoading, modifier)
        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT ->
            EncryptDecryptRequest(request, onRemoveIntentData, onLoading, modifier)
        else -> SignEventRequest(request, onRemoveIntentData, onLoading, modifier)
    }
}

@Composable
private fun GetPublicKeyRequest(
    request: IntentRequest,
    horizontalPadding: Dp,
    scaffoldPadding: PaddingValues,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberResultCopier()
    val activity = LocalActivity.current
    LoginWithPubKey(
        horizontalPadding = horizontalPadding,
        scaffoldPadding = scaffoldPadding,
        packageName = request.packageName,
        account = request.account,
        permissions = request.intentData.permissions?.toImmutableList(),
        onAccept = { permissions, signPolicy, closeApplication, rememberType, acc ->
            IntentUtils.sendResult(
                context,
                acc,
                request.reply(acc.hexKey),
                IntentApproval(
                    rememberType = rememberType,
                    signPolicy = signPolicy,
                    permissions = permissions,
                    appName = request.approvalAppName,
                    shouldCloseApplication = closeApplication,
                ),
                copyToClipboard,
                onLoading,
                onRemoveIntentData,
            )
        },
        onReject = {
            activity?.intent = null
            activity?.finishAndRemoveTask()
            onRemoveIntentData(listOf(request.intentData), IntentResultType.REMOVE)
            onLoading(false)
        },
    )
}

@Composable
private fun SignPsbtRequest(
    request: IntentRequest,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberResultCopier()
    val appScope = LocalAppScope.current
    val intentData = request.intentData
    val decoded = remember(intentData.data) { PsbtDecoder.decode(intentData.data, request.account.signer.keyPair.pubKey) }
    SignPsbt(
        modifier = modifier,
        psbtHex = intentData.data,
        decoded = decoded,
        shouldRunOnAccept = request.remembered(),
        packageName = request.packageName,
        onAccept = { rememberType ->
            appScope.launch(Dispatchers.IO) {
                try {
                    val result = request.account.signPsbt(intentData.data)
                    IntentUtils.sendResult(
                        context,
                        request.account,
                        request.reply(result),
                        IntentApproval(rememberType),
                        copyToClipboard,
                        onLoading,
                        onRemoveIntentData,
                    )
                } catch (e: Exception) {
                    ToastManager.toast(
                        title = getStringBlocking(Res.string.warning),
                        message = e.message ?: "Could not sign the PSBT",
                    )
                    onLoading(false)
                }
            }
        },
        onReject = { rememberType ->
            IntentUtils.sendRejection(
                key = request.key,
                account = request.account,
                intentData = intentData,
                appName = request.appName,
                rememberType = rememberType,
                onLoading = onLoading,
                onRemoveIntentData = onRemoveIntentData,
                kind = null,
            )
        },
    )
}

@Composable
private fun Nip44v3Request(
    request: IntentRequest,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberResultCopier()
    val intentData = request.intentData
    Nip44v3ApprovalData(
        modifier = modifier,
        isBunker = false,
        appName = request.appName,
        packageName = request.packageName,
        prompt = Nip44v3Prompt(intentData.type, intentData.nip44v3Kind, intentData.nip44v3Scope, intentData.encryptedData),
        account = request.account,
        shouldRunOnAccept = request.remembered(),
        onAccept = { rememberType, scope ->
            val result = intentData.encryptedData?.result ?: ""
            val v3Kind = if (scope == DecryptTypeScope.SPECIFIC) intentData.nip44v3Kind else null
            IntentUtils.sendResult(
                context,
                request.account,
                request.reply(result),
                IntentApproval(rememberType, kind = v3Kind, decryptTypeScope = scope),
                copyToClipboard,
                onLoading,
                onRemoveIntentData,
            )
        },
        onReject = { rememberType, scope ->
            val v3Kind = if (scope == DecryptTypeScope.SPECIFIC) intentData.nip44v3Kind else null
            IntentUtils.sendRejection(
                key = request.key,
                account = request.account,
                intentData = intentData,
                appName = request.appName,
                rememberType = rememberType,
                onLoading = onLoading,
                onRemoveIntentData = onRemoveIntentData,
                kind = v3Kind,
                decryptTypeScope = scope,
            )
        },
    )
}

@Composable
private fun EncryptDecryptRequest(
    request: IntentRequest,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberResultCopier()
    val intentData = request.intentData
    EncryptDecryptData(
        modifier = modifier,
        encryptedData = intentData.encryptedData,
        shouldRunOnAccept = request.remembered(),
        packageName = request.packageName,
        type = intentData.type,
        account = request.account,
        onAccept = { rememberType, scope ->
            val result =
                if (intentData.encryptedData?.result == "Could not decrypt the message" && (intentData.type == SignerType.DECRYPT_ZAP_EVENT)) {
                    ""
                } else {
                    intentData.encryptedData?.result ?: ""
                }
            IntentUtils.sendResult(
                context,
                request.account,
                request.reply(result),
                IntentApproval(rememberType, decryptTypeScope = scope),
                copyToClipboard,
                onLoading,
                onRemoveIntentData,
            )
        },
        onReject = { rememberType, scope ->
            IntentUtils.sendRejection(
                key = request.key,
                account = request.account,
                intentData = intentData,
                appName = request.appName,
                rememberType = rememberType,
                onLoading = onLoading,
                onRemoveIntentData = onRemoveIntentData,
                kind = null,
                decryptTypeScope = scope,
            )
        },
    )
}

@Composable
private fun SignEventRequest(
    request: IntentRequest,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val event = request.intentData.event!!
    val accounts = SavedAccounts.all(context).filter { it.npub.bechToBytes().toHexKey() == event.pubKey }

    when {
        accounts.isEmpty() && !isPrivateEvent(event.kind, event.tags) -> NotLoggedIn(event.pubKey, modifier)
        event.kind == RELAY_AUTH_KIND -> RelayAuthRequest(request, onRemoveIntentData, onLoading, modifier)
        else -> EventRequest(request, onRemoveIntentData, onLoading, modifier)
    }
}

@Composable
private fun NotLoggedIn(pubKey: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        Text(
            Hex.decode(pubKey).toNpub().toShortenHex(),
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
        )
        Spacer(Modifier.size(8.dp))
        Text(stringResource(Res.string.not_logged_in))
    }
}

@Composable
private fun RelayAuthRequest(
    request: IntentRequest,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberResultCopier()
    val settingsStore = LocalSettingsStore.current
    val intentData = request.intentData
    val event = intentData.event!!
    val relayUrl = RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))
    val blockedByWhitelist = isBlockedByAuthWhitelist(RELAY_AUTH_KIND, relayUrl, settingsStore.settings.authWhitelist)

    BunkerRelayAuthScreen(
        modifier = modifier,
        appName = request.appName,
        relayUrl = relayUrl,
        shouldAcceptOrReject = if (blockedByWhitelist) false else request.remembered(relayUrl),
        defaultScope = RelayAuthScope.SPECIFIC,
        account = request.account,
        onAccept = { rememberType, scope ->
            if (refuseCrossAccount(context, request)) return@BunkerRelayAuthScreen
            val (relayPermission, relayRemember) = relayGrant(scope, relayUrl, rememberType)
            IntentUtils.sendResult(
                context,
                request.account,
                request.reply(event.toJson(), event.sig),
                IntentApproval(relayRemember, kind = event.kind, appName = request.approvalAppName, relay = relayPermission),
                copyToClipboard,
                onLoading,
                onRemoveIntentData,
            )
        },
        onReject = { rememberType, scope ->
            val (relayPermission, relayRemember) = relayGrant(scope, relayUrl, rememberType)
            IntentUtils.sendRejection(
                key = request.key,
                account = request.account,
                intentData = intentData,
                appName = request.appName,
                rememberType = relayRemember,
                onLoading = onLoading,
                onRemoveIntentData = onRemoveIntentData,
                kind = event.kind,
                relay = relayPermission,
            )
        },
    )
}

@Composable
private fun EventRequest(
    request: IntentRequest,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copyToClipboard = rememberResultCopier()
    val intentData = request.intentData
    val event = intentData.event!!
    EventData(
        modifier = modifier,
        shouldAcceptOrReject = request.remembered(),
        packageName = request.packageName,
        event = event,
        account = request.account,
        onAccept = {
            if (refuseCrossAccount(context, request)) return@EventData

            val isAnonZap = event is ZapRequestEvent && event.tags.any { tag -> tag.any { t -> t == "anon" } }
            IntentUtils.sendResult(
                context,
                request.account,
                request.reply(event.toJson(), if (isAnonZap) event.toJson() else event.sig),
                IntentApproval(it, kind = event.kind, appName = request.approvalAppName),
                copyToClipboard,
                onLoading,
                onRemoveIntentData,
            )
        },
        onReject = {
            IntentUtils.sendRejection(
                key = request.key,
                account = request.account,
                intentData = intentData,
                appName = request.appName,
                rememberType = it,
                onLoading = onLoading,
                onRemoveIntentData = onRemoveIntentData,
                kind = event.kind,
            )
        },
    )
}

private fun refuseCrossAccount(context: Context, request: IntentRequest): Boolean {
    val event = request.intentData.event!!
    val crossAccount = isCrossAccountSignEvent(
        request.intentData.unsignedEventKey,
        request.account.hexKey,
        event.kind,
        event.tags,
        SavedAccounts.all(context).map(AccountInfo::npub),
    )
    if (crossAccount) {
        ToastManager.toast(
            title = getStringBlocking(Res.string.warning),
            message = getStringBlocking(Res.string.event_pubkey_is_not_equal_to_current_logged_in_user),
        )
    }
    return crossAccount
}

private fun relayGrant(scope: RelayAuthScope, relayUrl: String, rememberType: RememberType): Pair<String, RememberType> = Pair(
    if (scope == RelayAuthScope.ALL) "*" else relayUrl,
    relayAuthRememberType(allRelays = scope == RelayAuthScope.ALL, relayUrl = relayUrl, rememberType = rememberType),
)
