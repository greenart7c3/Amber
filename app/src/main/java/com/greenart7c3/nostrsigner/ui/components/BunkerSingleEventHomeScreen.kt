package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.parseRequestedPermissions
import com.greenart7c3.nostrsigner.common.psbt.PsbtDecoder
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.containsNip
import com.greenart7c3.nostrsigner.commonsui.create_a_new_connection
import com.greenart7c3.nostrsigner.commonsui.event_pubkey_is_not_equal_to_current_logged_in_user
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.not_logged_in
import com.greenart7c3.nostrsigner.commonsui.replace
import com.greenart7c3.nostrsigner.commonsui.replace_app_message
import com.greenart7c3.nostrsigner.commonsui.replace_connection
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.service.BunkerResponder
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.service.isPrivateEvent
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.relayAuthRememberType
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalBunkerResponder
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestGetPublicKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestPing
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val RELAY_AUTH_KIND = 22242

private fun viewModelKeyIsolatingGrants(account: Account, bunkerRequest: AmberBunkerRequest) = "bunker-single-${account.npub}-${bunkerRequest.queueKey}"

@Composable
fun BunkerSingleEventHomeScreen(
    horizontalPadding: Dp,
    scaffoldPadding: PaddingValues,
    bunkerRequest: AmberBunkerRequest,
    account: Account,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BunkerSingleEventViewModel = viewModel(
        key = viewModelKeyIsolatingGrants(account, bunkerRequest),
        factory = BunkerSingleEventViewModel.factory(
            accountRepository = LocalAccountRepository.current,
            bunkerResponder = LocalBunkerResponder.current,
            appScope = LocalAppScope.current,
        ),
    ),
) {
    LaunchedEffect(bunkerRequest, account.npub) { viewModel.load(bunkerRequest, account.npub) }
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val replacePrompt by viewModel.replacePrompt.collectAsStateWithLifecycle()
    val responder = LocalBunkerResponder.current
    val applicationLoadedForThisRequest = loaded?.takeIf { it.request === bunkerRequest }?.application
    val request = BunkerSingleRequest(bunkerRequest, account, applicationLoadedForThisRequest)

    when (bunkerRequest.request) {
        is BunkerRequestPing -> PingRequest(request, responder, onLoading, modifier)
        is BunkerRequestConnect -> {
            val replacePromptRaisedForThisRequest = replacePrompt?.takeIf { it.request.bunkerRequest === bunkerRequest }
            replacePromptRaisedForThisRequest?.let {
                ReplaceConnectionDialog(
                    prompt = it,
                    onReplace = { viewModel.replaceConnection(onLoading) },
                    onCreateNew = { viewModel.createNewConnection(onLoading) },
                    onDismiss = viewModel::dismissReplacePrompt,
                )
            }
            KillSwitchConnectPrompt(hasConnectRequest = true)
            ConnectRequest(request, horizontalPadding, scaffoldPadding, responder, onApprove = { viewModel.approveConnect(request, it, onLoading) }, onLoading = onLoading, loadConnectionName = viewModel::connectionName)
        }
        is BunkerRequestGetPublicKey -> GetPublicKeyRequest(request, responder, onLoading, modifier)
        is BunkerRequestNip04Encrypt, is BunkerRequestNip04Decrypt, is BunkerRequestNip44Encrypt, is BunkerRequestNip44Decrypt ->
            EncryptDecryptRequest(request, responder, onLoading, modifier)
        is BunkerRequestSign -> SignEventRequest(request, responder, onLoading, modifier)
        else -> when (request.type) {
            SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> Nip44v3Request(request, responder, onLoading, modifier)
            SignerType.DECRYPT_ZAP_EVENT -> ZapDecryptRequest(request, responder, onLoading, modifier)
            SignerType.SIGN_PSBT -> SignPsbtRequest(request, responder, onLoading, modifier)
            else -> {}
        }
    }
}

@Composable
private fun PingRequest(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BunkerPingScreen(
        modifier = modifier,
        appName = request.appName,
        iconUrl = request.appIcon,
        shouldRunOnAccept = request.decision(),
        onAccept = { responder.accept(request, "pong", it, onLoading, closeApplication = null) },
        onReject = { responder.reject(request, it, onLoading) },
    )
}

@Composable
private fun ReplaceConnectionDialog(
    prompt: ReplaceConnectionPrompt,
    onReplace: () -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        title = { Text(text = stringResource(Res.string.replace_connection)) },
        text = { Text(text = stringResource(Res.string.replace_app_message, prompt.existingName)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            Column {
                AmberButton(onClick = onReplace, text = stringResource(Res.string.replace))
                if (prompt.canCreateNew) {
                    AmberButton(text = stringResource(Res.string.create_a_new_connection), onClick = onCreateNew)
                }
                AmberButton(text = stringResource(Res.string.cancel), onClick = onDismiss)
            }
        },
    )
}

@Composable
private fun ConnectRequest(
    request: BunkerSingleRequest,
    horizontalPadding: Dp,
    scaffoldPadding: PaddingValues,
    responder: BunkerResponder,
    onApprove: (ConnectApproval) -> Unit,
    onLoading: (Boolean) -> Unit,
    loadConnectionName: suspend (npub: String, secret: String) -> String?,
) {
    val connect = request.bunkerRequest.request as BunkerRequestConnect
    BunkerConnectRequestScreen(
        horizontalPadding = horizontalPadding,
        scaffoldPadding = scaffoldPadding,
        shouldCloseApp = request.closeApplication,
        account = request.account,
        bunkerRequest = request.bunkerRequest,
        permissions = remember(connect.permissions) { parseConnectPermissions(connect.permissions) },
        onAccept = { permissions, signPolicy, closeApplication, rememberType, deleteAfter, acc ->
            onApprove(ConnectApproval(permissions, signPolicy, closeApplication, rememberType, deleteAfter, acc))
        },
        onReject = { responder.reject(request, it, onLoading) },
        loadConnectionName = loadConnectionName,
    )
}

private fun parseConnectPermissions(permissions: String?): ImmutableList<Permission>? = permissions?.let { value ->
    parseRequestedPermissions(value) { it.containsNip() }.map { Permission(it.type, it.kind) }.toImmutableList()
}

@Composable
private fun GetPublicKeyRequest(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BunkerGetPubKeyScreen(
        modifier = modifier,
        applicationName = request.appName,
        iconUrl = request.appIcon,
        onAccept = { permissions, signPolicy, closeApplication, rememberType ->
            responder.sendResult(
                account = request.account,
                key = request.key,
                response = request.account.hexKey,
                bunkerRequest = request.bunkerRequest,
                kind = null,
                onLoading = onLoading,
                permissions = permissions,
                appName = request.appName,
                signPolicy = signPolicy,
                shouldCloseApplication = closeApplication,
                rememberType = rememberType,
            )
        },
        onReject = { responder.reject(request, it, onLoading) },
    )
}

@Composable
private fun EncryptDecryptRequest(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BunkerEncryptDecryptData(
        modifier = modifier,
        encryptedData = request.bunkerRequest.encryptedData,
        shouldRunOnAccept = request.decision(),
        appName = request.appName,
        iconUrl = request.appIcon,
        type = request.type,
        account = request.account,
        onAccept = { rememberType, scope ->
            responder.accept(request, request.bunkerRequest.encryptedData?.result ?: "", rememberType, onLoading, decryptTypeScope = scope)
        },
        onReject = { rememberType, scope -> responder.reject(request, rememberType, onLoading, decryptTypeScope = scope) },
    )
}

@Composable
private fun SignEventRequest(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val event = request.bunkerRequest.signedEvent!!
    val signedInAsAuthor = SavedAccounts.all(context).any { it.npub.bechToBytes().toHexKey() == event.pubKey }

    when {
        !signedInAsAuthor && !isPrivateEvent(event.kind, event.tags) -> NotLoggedIn(event.pubKey)
        event.kind == RELAY_AUTH_KIND -> RelayAuthRequest(request, event, responder, onLoading, modifier)
        else -> BunkerEventData(
            modifier = modifier,
            shouldAcceptOrReject = request.decision(),
            appName = request.appName,
            iconUrl = request.appIcon,
            event = event,
            account = request.account,
            onAccept = {
                if (refuseOtherAuthor(context, request, event)) return@BunkerEventData
                responder.accept(request, event.toJson(), it, onLoading, kind = event.kind)
            },
            onReject = { responder.reject(request, it, onLoading, kind = event.kind) },
        )
    }
}

@Composable
private fun NotLoggedIn(pubKey: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(Hex.decode(pubKey).toNpub().toShortenHex(), fontWeight = FontWeight.Bold, fontSize = 21.sp)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(Res.string.not_logged_in))
    }
}

@Composable
private fun RelayAuthRequest(
    request: BunkerSingleRequest,
    event: Event,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relayUrl = RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))
    val rejectedByAuthWhitelistOverridingRememberedChoice = isBlockedByAuthWhitelist(RELAY_AUTH_KIND, relayUrl, LocalSettingsStore.current.settings.authWhitelist)

    BunkerRelayAuthScreen(
        modifier = modifier,
        appName = request.appName,
        iconUrl = request.appIcon,
        relayUrl = relayUrl,
        shouldAcceptOrReject = if (rejectedByAuthWhitelistOverridingRememberedChoice) false else request.decision(relayUrl),
        defaultScope = RelayAuthScope.SPECIFIC,
        account = request.account,
        onAccept = { rememberType, scope ->
            if (refuseOtherAuthor(context, request, event)) return@BunkerRelayAuthScreen
            val (relay, remember) = relayGrant(scope, relayUrl, rememberType)
            responder.accept(request, event.toJson(), remember, onLoading, kind = event.kind, relay = relay)
        },
        onReject = { rememberType, scope ->
            val (relay, remember) = relayGrant(scope, relayUrl, rememberType)
            responder.reject(request, remember, onLoading, kind = event.kind, relay = relay)
        },
    )
}

@Composable
private fun Nip44v3Request(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val v3Kind = request.bunkerRequest.request.nip44v3Kind()
    Nip44v3ApprovalData(
        modifier = modifier,
        isBunker = true,
        appName = request.appName,
        iconUrl = request.appIcon,
        packageName = null,
        prompt = Nip44v3Prompt(request.type, v3Kind, request.bunkerRequest.request.nip44v3Scope(), request.bunkerRequest.encryptedData),
        account = request.account,
        shouldRunOnAccept = request.nip44v3Decision(),
        onAccept = { rememberType, scope ->
            val grantedKind = if (scope == DecryptTypeScope.SPECIFIC) v3Kind else null
            responder.accept(request, request.bunkerRequest.encryptedData?.result ?: "", rememberType, onLoading, kind = grantedKind, decryptTypeScope = scope)
        },
        onReject = { rememberType, scope ->
            val grantedKind = if (scope == DecryptTypeScope.SPECIFIC) v3Kind else null
            responder.reject(request, rememberType, onLoading, kind = grantedKind, decryptTypeScope = scope)
        },
    )
}

@Composable
private fun ZapDecryptRequest(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BunkerEncryptDecryptData(
        modifier = modifier,
        encryptedData = request.bunkerRequest.encryptedData,
        shouldRunOnAccept = request.decision(),
        appName = request.appName,
        type = request.type,
        account = request.account,
        onAccept = { rememberType, scope ->
            val decrypted = request.bunkerRequest.encryptedData?.result
            val result = if (decrypted == "Could not decrypt the message") "" else decrypted ?: ""
            responder.accept(request, result, rememberType, onLoading, decryptTypeScope = scope)
        },
        onReject = { rememberType, scope -> responder.reject(request, rememberType, onLoading, decryptTypeScope = scope) },
    )
}

@Composable
private fun SignPsbtRequest(
    request: BunkerSingleRequest,
    responder: BunkerResponder,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appScope = LocalAppScope.current
    val coroutineScope = rememberCoroutineScope()
    val psbtHex = request.bunkerRequest.request.params.first()
    val decoded = remember(psbtHex) { PsbtDecoder.decode(psbtHex, request.account.signer.keyPair.pubKey) }

    BunkerSignPsbt(
        modifier = modifier,
        psbtHex = psbtHex,
        decoded = decoded,
        shouldRunOnAccept = request.decision(),
        appName = request.appName,
        iconUrl = request.appIcon,
        onAccept = {
            appScope.launch(Dispatchers.IO) {
                try {
                    responder.accept(request, request.account.signPsbt(psbtHex), it, onLoading)
                } catch (e: Exception) {
                    coroutineScope.launch {
                        Toast.makeText(context, e.message ?: "Could not sign the PSBT", Toast.LENGTH_SHORT).show()
                    }
                    onLoading(false)
                }
            }
        },
        onReject = { responder.reject(request, it, onLoading) },
    )
}

private fun refuseOtherAuthor(context: Context, request: BunkerSingleRequest, event: Event): Boolean {
    val refused = event.pubKey != request.account.hexKey && !isPrivateEvent(event.kind, event.tags)
    if (refused) {
        Toast.makeText(context, getStringBlocking(Res.string.event_pubkey_is_not_equal_to_current_logged_in_user), Toast.LENGTH_SHORT).show()
    }
    return refused
}

private fun relayGrant(scope: RelayAuthScope, relayUrl: String, rememberType: RememberType): Pair<String, RememberType> = Pair(
    if (scope == RelayAuthScope.ALL) "*" else relayUrl,
    relayAuthRememberType(allRelays = scope == RelayAuthScope.ALL, relayUrl = relayUrl, rememberType = rememberType),
)

private fun BunkerResponder.accept(
    request: BunkerSingleRequest,
    response: String,
    rememberType: RememberType,
    onLoading: (Boolean) -> Unit,
    kind: Int? = null,
    closeApplication: Boolean? = request.bunkerRequest.closeApplication,
    decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    relay: String = "",
) = sendResult(
    account = request.account,
    key = request.key,
    response = response,
    bunkerRequest = request.bunkerRequest,
    kind = kind,
    onLoading = onLoading,
    appName = request.appName,
    shouldCloseApplication = closeApplication,
    rememberType = rememberType,
    relay = relay,
    decryptTypeScope = decryptTypeScope,
)

private fun BunkerResponder.reject(
    request: BunkerSingleRequest,
    rememberType: RememberType,
    onLoading: (Boolean) -> Unit,
    kind: Int? = null,
    decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    relay: String = "",
) = sendRejection(
    key = request.key,
    account = request.account,
    bunkerRequest = request.bunkerRequest,
    appName = request.appName,
    rememberType = rememberType,
    signerType = request.type,
    kind = kind,
    onLoading = onLoading,
    relay = relay,
    decryptTypeScope = decryptTypeScope,
)
