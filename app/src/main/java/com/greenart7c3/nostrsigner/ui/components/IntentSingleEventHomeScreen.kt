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
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.isPrivateEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun IntentSingleEventHomeScreen(
    modifier: Modifier,
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    var applicationEntity by remember {
        mutableStateOf<ApplicationWithPermissions?>(null)
    }
    val key = "$packageName"

    LaunchedEffect(Unit) {
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
                packageName = packageName,
                modifier = modifier,
                account = account,
                permissions = intentData.permissions,
                onAccept = { permissions, signPolicy, closeApplication, rememberType, acc ->
                    val result = if (packageName == null) {
                        acc.hexKey
                    } else {
                        acc.npub
                    }

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
                    activity?.finishAndRemoveTask()
                    onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                    onLoading(false)
                },
            )
        }

        SignerType.SIGN_MESSAGE -> {
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }

            val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)

            SignMessage(
                account = account,
                modifier = modifier,
                content = intentData.data,
                shouldRunOnAccept = acceptOrReject,
                packageName = packageName,
                applicationName = applicationName,
                appName = appName,
                type = intentData.type,
                onAccept = {
                    Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                        val result = account.signString(intentData.data)
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
                            rememberType = it,
                        )
                    }
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
                        kind = null,
                    )
                },
            )
        }

        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
            val nip = when (intentData.type) {
                SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT -> 4
                SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT -> 44
                SignerType.DECRYPT_ZAP_EVENT -> null
                else -> null
            }
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }
            if (permission == null && nip != null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }

            val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)

            EncryptDecryptData(
                account = account,
                modifier = modifier,
                encryptedData = intentData.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                packageName = packageName,
                applicationName = applicationName,
                appName = appName,
                type = intentData.type,
                onAccept = {
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
                        kind = null,
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
            } else {
                val permission =
                    applicationEntity?.permissions?.firstOrNull {
                        val nip = event.kind.kindToNip()?.toIntOrNull()
                        it.pkKey == key && ((it.type == intentData.type.toString() && it.kind == event.kind) || (nip != null && it.type == "NIP" && it.kind == nip))
                    }

                val acceptOrReject = IntentUtils.isRemembered(applicationEntity?.application?.signPolicy, permission)

                EventData(
                    account = account,
                    modifier = modifier,
                    shouldAcceptOrReject = acceptOrReject,
                    packageName = packageName,
                    appName = appName,
                    applicationName = applicationName,
                    event = event,
                    rawJson = event.toJson(),
                    type = intentData.type,
                    onAccept = {
                        if (intentData.unsignedEventKey.isNotBlank() && intentData.unsignedEventKey != account.hexKey && !isPrivateEvent(event.kind, event.tags)) {
                            accountStateViewModel.toast(
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
