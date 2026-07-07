package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.models.toPermissionType
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.PsbtDecoder
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.finishAndRemoveTaskSafely
import com.greenart7c3.nostrsigner.service.isPrivateEvent
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.ToastManager
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun IntentSingleEventHomeScreen(
    horizontalPadding: Dp,
    modifier: Modifier,
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    var applicationEntity by remember {
        mutableStateOf<ApplicationWithPermissions?>(null)
    }
    val key = "$packageName"

    LaunchedEffect(Unit) {
        // Browser deep-links (nostrsigner://) arrive with no calling package, so
        // every web caller would otherwise share the single "null" key. Honoring a
        // remembered grant for that shared identity would let a grant given to one
        // website auto-approve any other website. Never load (and thus never honor)
        // the shared "null" application: force always-ask for null-package callers.
        if (packageName == null) return@LaunchedEffect
        launch(Dispatchers.IO) {
            applicationEntity = Amber.instance.getDatabase(account.npub).dao().getByKey(key)
        }
    }

    var appName = applicationEntity?.application?.name ?: packageName ?: intentData.name
    appName = appName.ifBlank { key.toShortenHex() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current

    when (intentData.type) {
        SignerType.GET_PUBLIC_KEY -> {
            LoginWithPubKey(
                horizontalPadding = horizontalPadding,
                packageName = packageName,
                modifier = modifier,
                account = account,
                permissions = intentData.permissions?.toImmutableList(),
                onAccept = { permissions, signPolicy, closeApplication, rememberType, acc ->
                    val result = acc.hexKey

                    IntentUtils.sendResult(
                        context,
                        packageName,
                        acc,
                        key,
                        clipboardManager,
                        result,
                        result,
                        intentData,
                        null,
                        permissions = permissions,
                        appName = applicationName ?: appName,
                        onLoading = onLoading,
                        signPolicy = signPolicy,
                        onRemoveIntentData = onRemoveIntentData,
                        shouldCloseApplication = closeApplication,
                        rememberType = rememberType,
                    )
                },
                onReject = {
                    val activity = Amber.instance.getMainActivity()
                    activity?.intent = null
                    activity?.finishAndRemoveTaskSafely()
                    onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                    onLoading(false)
                },
            )
        }

        SignerType.SIGN_PSBT -> {
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }

            val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)
            val decoded = remember(intentData.data) { PsbtDecoder.decode(intentData.data, account) }

            SignPsbt(
                modifier = modifier,
                psbtHex = intentData.data,
                decoded = decoded,
                shouldRunOnAccept = acceptOrReject,
                packageName = packageName,
                onAccept = { rememberType ->
                    Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                        try {
                            val result = account.signPsbt(intentData.data)
                            IntentUtils.sendResult(
                                context,
                                packageName,
                                account,
                                key,
                                clipboardManager,
                                result,
                                result,
                                intentData,
                                null,
                                onLoading,
                                onRemoveIntentData = onRemoveIntentData,
                                rememberType = rememberType,
                            )
                        } catch (e: Exception) {
                            ToastManager.toast(
                                title = context.getString(R.string.warning),
                                message = e.message ?: "Could not sign the PSBT",
                            )
                            onLoading(false)
                        }
                    }
                },
                onReject = { rememberType ->
                    IntentUtils.sendRejection(
                        key = key,
                        account = account,
                        intentData = intentData,
                        appName = appName,
                        rememberType = rememberType,
                        onLoading = onLoading,
                        onRemoveIntentData = onRemoveIntentData,
                        kind = null,
                    )
                },
            )
        }

        SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> {
            // V3 grants are scoped by (app, type, kind); fall back to a
            // kind=null "all kinds" grant. They never satisfy v2/v4 requests.
            val permType = intentData.type.toString()
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == permType && it.kind == intentData.nip44v3Kind
                } ?: applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == permType && it.kind == null
                }

            val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)

            Nip44v3ApprovalData(
                modifier = modifier,
                isBunker = false,
                appName = appName,
                packageName = packageName,
                type = intentData.type,
                account = account,
                kind = intentData.nip44v3Kind,
                scope = intentData.nip44v3Scope,
                encryptedData = intentData.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                onAccept = { rememberType, scope ->
                    val result = intentData.encryptedData?.result ?: ""
                    // SPECIFIC ⇒ kind-scoped grant; ALL ⇒ all-kinds grant (kind=null).
                    val v3Kind = if (scope == DecryptTypeScope.SPECIFIC) intentData.nip44v3Kind else null
                    IntentUtils.sendResult(
                        context,
                        packageName,
                        account,
                        key,
                        clipboardManager,
                        result,
                        result,
                        intentData,
                        v3Kind,
                        onRemoveIntentData = onRemoveIntentData,
                        onLoading = onLoading,
                        rememberType = rememberType,
                        decryptTypeScope = scope,
                    )
                },
                onReject = { rememberType, scope ->
                    val v3Kind = if (scope == DecryptTypeScope.SPECIFIC) intentData.nip44v3Kind else null
                    IntentUtils.sendRejection(
                        key = key,
                        account = account,
                        intentData = intentData,
                        appName = appName,
                        rememberType = rememberType,
                        onLoading = onLoading,
                        onRemoveIntentData = onRemoveIntentData,
                        kind = v3Kind,
                        decryptTypeScope = scope,
                    )
                },
            )
        }

        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
            val nip = when (intentData.type) {
                SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT -> 4
                SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT -> 44
                else -> null
            }
            val isEncrypt = intentData.type == SignerType.NIP04_ENCRYPT || intentData.type == SignerType.NIP44_ENCRYPT
            val permType = intentData.encryptedData.toPermissionType(isEncrypt = isEncrypt)
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == permType
                }
            if (permission == null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == intentData.type.toString()
                    }
            }
            if (permission == null && nip != null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }

            val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)

            EncryptDecryptData(
                modifier = modifier,
                encryptedData = intentData.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                packageName = packageName,
                type = intentData.type,
                account = account,
                onAccept = { rememberType, scope ->
                    val result =
                        if (intentData.encryptedData?.result == "Could not decrypt the message" && (intentData.type == SignerType.DECRYPT_ZAP_EVENT)) {
                            ""
                        } else {
                            intentData.encryptedData?.result ?: ""
                        }

                    IntentUtils.sendResult(
                        context,
                        packageName,
                        account,
                        key,
                        clipboardManager,
                        result,
                        result,
                        intentData,
                        null,
                        onRemoveIntentData = onRemoveIntentData,
                        onLoading = onLoading,
                        rememberType = rememberType,
                        decryptTypeScope = scope,
                    )
                },
                onReject = { rememberType, scope ->
                    IntentUtils.sendRejection(
                        key = key,
                        account = account,
                        intentData = intentData,
                        appName = appName,
                        rememberType = rememberType,
                        onLoading = onLoading,
                        onRemoveIntentData = onRemoveIntentData,
                        kind = null,
                        decryptTypeScope = scope,
                    )
                },
            )
        }

        else -> {
            val event = intentData.event!!
            val accounts =
                LocalPreferences.allSavedAccounts(context).filter {
                    it.npub.bechToBytes().toHexKey() == event.pubKey
                }

            if (accounts.isEmpty() && !isPrivateEvent(event.kind, event.tags)) {
                Column(
                    Modifier.fillMaxSize(),
                    Arrangement.Center,
                    Alignment.CenterHorizontally,
                ) {
                    Text(
                        Hex.decode(event.pubKey).toNpub().toShortenHex(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Not logged in")
                }
            } else if (event.kind == 22242) {
                // Kind 22242 = relay client authentication (NIP-42)
                // Permission is per-relay hostname extracted from the event's "relay" tag
                val relayUrl = RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))

                // Check for relay-specific permission first, then wildcard "*" (all relays)
                val permission = applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString() && it.kind == 22242 && it.relay == relayUrl
                } ?: applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString() && it.kind == 22242 && it.relay == "*"
                }

                // If the auth whitelist is non-empty and this relay is not in it, auto-reject
                val authWhitelist = Amber.instance.settings.authWhitelist
                val blockedByWhitelist = authWhitelist.isNotEmpty() && relayUrl !in authWhitelist

                val acceptOrReject = if (blockedByWhitelist) {
                    false
                } else if (permission == null) {
                    IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, null)
                } else {
                    IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)
                }

                BunkerRelayAuthScreen(
                    modifier = modifier,
                    appName = appName,
                    relayUrl = relayUrl,
                    shouldAcceptOrReject = acceptOrReject,
                    defaultScope = RelayAuthScope.SPECIFIC,
                    account = account,
                    onAccept = { rememberType, scope ->
                        if (intentData.unsignedEventKey.isNotBlank() && intentData.unsignedEventKey != account.hexKey && !isPrivateEvent(event.kind, event.tags)) {
                            ToastManager.toast(
                                title = context.getString(R.string.warning),
                                message = context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                            )
                            return@BunkerRelayAuthScreen
                        }

                        val relayPermission = if (scope == RelayAuthScope.ALL) "*" else relayUrl
                        IntentUtils.sendResult(
                            context = context,
                            packageName = packageName,
                            account = account,
                            key = key,
                            clipboardManager = clipboardManager,
                            event = event.toJson(),
                            value = event.sig,
                            intentData = intentData,
                            kind = event.kind,
                            onLoading = onLoading,
                            onRemoveIntentData = onRemoveIntentData,
                            signPolicy = null,
                            appName = applicationName ?: appName,
                            permissions = null,
                            rememberType = rememberType,
                            relay = relayPermission,
                        )
                    },
                    onReject = { rememberType, scope ->
                        val relayPermission = if (scope == RelayAuthScope.ALL) "*" else relayUrl
                        IntentUtils.sendRejection(
                            key = key,
                            account = account,
                            intentData = intentData,
                            appName = appName,
                            rememberType = rememberType,
                            onLoading = onLoading,
                            onRemoveIntentData = onRemoveIntentData,
                            kind = event.kind,
                            relay = relayPermission,
                        )
                    },
                )
            } else {
                val permission =
                    applicationEntity?.permissions?.firstOrNull {
                        val nip = event.kind.kindToNip()?.toIntOrNull()
                        it.pkKey == key && ((it.type == intentData.type.toString() && it.kind == event.kind) || (nip != null && it.type == "NIP" && it.kind == nip))
                    }

                val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)

                EventData(
                    modifier = modifier,
                    shouldAcceptOrReject = acceptOrReject,
                    packageName = packageName,
                    event = event,
                    account = account,
                    onAccept = {
                        if (intentData.unsignedEventKey.isNotBlank() && intentData.unsignedEventKey != account.hexKey && !isPrivateEvent(event.kind, event.tags)) {
                            ToastManager.toast(
                                title = context.getString(R.string.warning),
                                message = context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                            )
                            return@EventData
                        }

                        IntentUtils.sendResult(
                            context = context,
                            packageName = packageName,
                            account = account,
                            key = key,
                            clipboardManager = clipboardManager,
                            event = event.toJson(),
                            value = if (event is LnZapRequestEvent &&
                                event.tags.any { tag ->
                                    tag.any { t -> t == "anon" }
                                }
                            ) {
                                event.toJson()
                            } else {
                                event.sig
                            },
                            intentData = intentData,
                            kind = event.kind,
                            onLoading = onLoading,
                            onRemoveIntentData = onRemoveIntentData,
                            signPolicy = null,
                            appName = applicationName ?: appName,
                            permissions = null,
                            rememberType = it,
                        )
                    },
                    onReject = {
                        IntentUtils.sendRejection(
                            key = key,
                            account = account,
                            intentData = intentData,
                            appName = appName,
                            rememberType = it,
                            onLoading = onLoading,
                            onRemoveIntentData = onRemoveIntentData,
                            kind = event.kind,
                        )
                    },
                )
            }
        }
    }
}
