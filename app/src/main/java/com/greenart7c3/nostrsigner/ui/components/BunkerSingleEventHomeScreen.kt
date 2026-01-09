package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.isPrivateEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
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
import com.vitorpamplona.quartz.nip55AndroidSigner.signString
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BunkerSingleEventHomeScreen(
    modifier: Modifier,
    bunkerRequest: AmberBunkerRequest,
    account: Account,
    onLoading: (Boolean) -> Unit,
) {
    var applicationEntity by remember {
        mutableStateOf<ApplicationWithPermissions?>(null)
    }
    val key = bunkerRequest.localKey

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            applicationEntity =
                if (bunkerRequest.request is BunkerRequestConnect && bunkerRequest.request.secret?.isNotBlank() == true) {
                    Amber.instance.getDatabase(account.npub).dao().getBySecret(bunkerRequest.request.secret!!)
                } else {
                    Amber.instance.getDatabase(account.npub).dao().getByKey(key)
                }
        }
    }

    var appName = applicationEntity?.application?.name ?: bunkerRequest.name
    appName = appName.ifBlank { key.toShortenHex() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val type = BunkerRequestUtils.getTypeFromBunker(bunkerRequest.request)

    when (bunkerRequest.request) {
        is BunkerRequestPing -> {
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == type.toString()
                }

            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            BunkerPingScreen(
                modifier = modifier,
                account = account,
                appName = appName,
                shouldRunOnAccept = acceptOrReject,
                onAccept = {
                    val result = "pong"

                    BunkerRequestUtils.sendResult(
                        context = context,
                        account = account,
                        key = key,
                        response = result,
                        bunkerRequest = bunkerRequest,
                        kind = null,
                        onLoading = onLoading,
                        permissions = null,
                        appName = appName,
                        signPolicy = null,
                        shouldCloseApplication = null,
                        rememberType = it,
                    )
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }
        is BunkerRequestConnect -> {
            var showExistingAppDialog by remember { mutableStateOf(false) }
            var localPermissions by remember { mutableStateOf<List<Permission>?>(null) }
            var localCloseApplication by remember { mutableStateOf<Boolean?>(null) }
            var localSignPolicy by remember { mutableIntStateOf(0) }
            var localRememberType by remember { mutableStateOf(RememberType.NEVER) }
            var existingAppKey by remember { mutableStateOf("") }
            var localDeleteAfter by remember { mutableLongStateOf(0L) }
            var selectedAccount by remember { mutableStateOf<Account>(account) }

            if (showExistingAppDialog) {
                AlertDialog(
                    title = {
                        Text(text = stringResource(R.string.replace_connection))
                    },
                    text = {
                        Text(text = stringResource(R.string.replace_app_message, bunkerRequest.name))
                    },
                    onDismissRequest = {
                        showExistingAppDialog = false
                    },
                    confirmButton = {
                        Column {
                            AmberButton(
                                onClick = {
                                    val result = bunkerRequest.nostrConnectSecret.ifBlank { "ack" }

                                    BunkerRequestUtils.sendResult(
                                        oldKey = existingAppKey,
                                        context = context,
                                        account = selectedAccount,
                                        key = key,
                                        response = result,
                                        bunkerRequest = bunkerRequest,
                                        kind = null,
                                        onLoading = onLoading,
                                        permissions = localPermissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                                        appName = appName,
                                        signPolicy = localSignPolicy,
                                        shouldCloseApplication = localCloseApplication,
                                        rememberType = localRememberType,
                                        deleteAfter = localDeleteAfter,
                                    )
                                },
                                text = stringResource(R.string.replace),
                            )
                            AmberButton(
                                text = stringResource(R.string.create_a_new_connection),
                                onClick = {
                                    val result = bunkerRequest.nostrConnectSecret.ifBlank { "ack" }

                                    BunkerRequestUtils.sendResult(
                                        context = context,
                                        account = selectedAccount,
                                        key = key,
                                        response = result,
                                        bunkerRequest = bunkerRequest,
                                        kind = null,
                                        onLoading = onLoading,
                                        permissions = localPermissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                                        appName = appName,
                                        signPolicy = localSignPolicy,
                                        shouldCloseApplication = localCloseApplication,
                                        rememberType = localRememberType,
                                        deleteAfter = localDeleteAfter,
                                    )
                                },
                            )
                            AmberButton(
                                text = stringResource(R.string.cancel),
                                onClick = {
                                    showExistingAppDialog = false
                                },
                            )
                        }
                    },
                )
            }

            BunkerConnectRequestScreen(
                modifier = modifier,
                shouldCloseApp = applicationEntity?.application?.closeApplication ?: bunkerRequest.closeApplication,
                account = account,
                bunkerRequest = bunkerRequest,
                permissions = bunkerRequest.request.permissions?.split(",")?.map {
                    val split = it.split(":")
                    if (split.size > 1) {
                        Permission(split[0].trim(), split[1].toInt())
                    } else {
                        Permission(split[0].trim(), null)
                    }
                },
                onAccept = { permissions, signPolicy, closeApplication, rememberType, deleteAfter, acc ->
                    val result = bunkerRequest.nostrConnectSecret.ifBlank { "ack" }

                    localPermissions = permissions
                    localSignPolicy = signPolicy
                    localCloseApplication = closeApplication
                    localRememberType = rememberType
                    selectedAccount = acc

                    if (bunkerRequest.name.isNotBlank()) {
                        Amber.instance.applicationIOScope.launch {
                            val existingApp = Amber.instance.getDatabase(acc.npub).dao().getByName(bunkerRequest.name)
                            if (existingApp == null) {
                                BunkerRequestUtils.sendResult(
                                    context = context,
                                    account = acc,
                                    key = key,
                                    response = result,
                                    bunkerRequest = bunkerRequest,
                                    kind = null,
                                    onLoading = onLoading,
                                    permissions = permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                                    appName = appName,
                                    signPolicy = signPolicy,
                                    shouldCloseApplication = closeApplication,
                                    rememberType = rememberType,
                                    deleteAfter = deleteAfter,
                                )
                            } else {
                                existingAppKey = existingApp.application.key
                                showExistingAppDialog = true
                            }
                        }
                    } else {
                        BunkerRequestUtils.sendResult(
                            context = context,
                            account = account,
                            key = key,
                            response = result,
                            bunkerRequest = bunkerRequest,
                            kind = null,
                            onLoading = onLoading,
                            permissions = permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                            appName = appName,
                            signPolicy = signPolicy,
                            shouldCloseApplication = closeApplication,
                            rememberType = rememberType,
                            deleteAfter = deleteAfter,
                        )
                    }
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }
        is BunkerRequestGetPublicKey -> {
            BunkerGetPubKeyScreen(
                modifier = modifier,
                account = account,
                applicationName = appName,
                onAccept = { permissions, signPolicy, closeApplication, rememberType ->
                    val result = account.hexKey

                    BunkerRequestUtils.sendResult(
                        context = context,
                        account = account,
                        key = key,
                        response = result,
                        bunkerRequest = bunkerRequest,
                        kind = null,
                        onLoading = onLoading,
                        permissions = permissions,
                        appName = appName,
                        signPolicy = signPolicy,
                        shouldCloseApplication = closeApplication,
                        rememberType = rememberType,
                    )
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }

        is BunkerRequestNip04Encrypt -> {
            val nip = 4
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == type.toString()
                }
            if (permission == null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }

            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            BunkerEncryptDecryptData(
                account = account,
                modifier = modifier,
                encryptedData = bunkerRequest.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                appName = appName,
                type = type,
                onAccept = {
                    BunkerRequestUtils.sendResult(
                        context = context,
                        account = account,
                        key = key,
                        response = bunkerRequest.encryptedData?.result ?: "",
                        bunkerRequest = bunkerRequest,
                        kind = null,
                        onLoading = onLoading,
                        permissions = null,
                        appName = appName,
                        signPolicy = null,
                        shouldCloseApplication = bunkerRequest.closeApplication,
                        rememberType = it,
                    )
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }

        is BunkerRequestNip04Decrypt -> {
            val nip = 4
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == type.toString()
                }
            if (permission == null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }

            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            BunkerEncryptDecryptData(
                account = account,
                modifier = modifier,
                encryptedData = bunkerRequest.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                appName = appName,
                type = type,
                onAccept = {
                    val result = bunkerRequest.encryptedData?.result ?: ""

                    BunkerRequestUtils.sendResult(
                        context = context,
                        account = account,
                        key = key,
                        response = result,
                        bunkerRequest = bunkerRequest,
                        kind = null,
                        onLoading = onLoading,
                        permissions = null,
                        appName = appName,
                        signPolicy = null,
                        shouldCloseApplication = bunkerRequest.closeApplication,
                        rememberType = it,
                    )
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }

        is BunkerRequestNip44Encrypt -> {
            val nip = 44
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == type.toString()
                }
            if (permission == null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }

            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            BunkerEncryptDecryptData(
                account = account,
                modifier = modifier,
                encryptedData = bunkerRequest.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                appName = appName,
                type = type,
                onAccept = {
                    val result = bunkerRequest.encryptedData?.result ?: ""

                    BunkerRequestUtils.sendResult(
                        context = context,
                        account = account,
                        key = key,
                        response = result,
                        bunkerRequest = bunkerRequest,
                        kind = null,
                        onLoading = onLoading,
                        permissions = null,
                        appName = appName,
                        signPolicy = null,
                        shouldCloseApplication = bunkerRequest.closeApplication,
                        rememberType = it,
                    )
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }

        is BunkerRequestNip44Decrypt -> {
            val nip = 44
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == type.toString()
                }
            if (permission == null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }

            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            BunkerEncryptDecryptData(
                account = account,
                modifier = modifier,
                encryptedData = bunkerRequest.encryptedData,
                shouldRunOnAccept = acceptOrReject,
                appName = appName,
                type = type,
                onAccept = {
                    val result = bunkerRequest.encryptedData?.result ?: ""

                    BunkerRequestUtils.sendResult(
                        context = context,
                        account = account,
                        key = key,
                        response = result,
                        bunkerRequest = bunkerRequest,
                        kind = null,
                        onLoading = onLoading,
                        permissions = null,
                        appName = appName,
                        signPolicy = null,
                        shouldCloseApplication = bunkerRequest.closeApplication,
                        rememberType = it,
                    )
                },
                onReject = {
                    BunkerRequestUtils.sendRejection(
                        key = key,
                        account = account,
                        bunkerRequest = bunkerRequest,
                        appName = appName,
                        rememberType = it,
                        signerType = type,
                        kind = null,
                        onLoading = onLoading,
                    )
                },
            )
        }

        is BunkerRequestSign -> {
            val event = bunkerRequest.signedEvent!!
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
                        it.pkKey == key && ((it.type == type.toString() && it.kind == event.kind) || (nip != null && it.type == "NIP" && it.kind == nip))
                    }

                val acceptUntil = permission?.acceptUntil ?: 0
                val rejectUntil = permission?.rejectUntil ?: 0

                val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                    null
                } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                    false
                } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                    true
                } else {
                    null
                }

                BunkerEventData(
                    account = account,
                    modifier = modifier,
                    shouldAcceptOrReject = acceptOrReject,
                    appName = appName,
                    event = event,
                    rawJson = event.toJson(),
                    type = type,
                    onAccept = {
                        if (event.pubKey != account.hexKey && !isPrivateEvent(event.kind, event.tags)) {
                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return@BunkerEventData
                        }

                        BunkerRequestUtils.sendResult(
                            context = context,
                            account = account,
                            key = key,
                            response = event.toJson(),
                            bunkerRequest = bunkerRequest,
                            kind = event.kind,
                            onLoading = onLoading,
                            permissions = null,
                            appName = appName,
                            signPolicy = null,
                            shouldCloseApplication = bunkerRequest.closeApplication,
                            rememberType = it,
                        )
                    },
                    onReject = {
                        BunkerRequestUtils.sendRejection(
                            key = key,
                            account = account,
                            bunkerRequest = bunkerRequest,
                            appName = appName,
                            rememberType = it,
                            signerType = type,
                            kind = event.kind,
                            onLoading = onLoading,
                        )
                    },
                )
            }
        }

        else -> {
            if (type == SignerType.DECRYPT_ZAP_EVENT) {
                val permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == type.toString()
                    }

                val acceptUntil = permission?.acceptUntil ?: 0
                val rejectUntil = permission?.rejectUntil ?: 0

                val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                    null
                } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                    false
                } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                    true
                } else {
                    null
                }

                BunkerEncryptDecryptData(
                    account = account,
                    modifier = modifier,
                    encryptedData = bunkerRequest.encryptedData,
                    shouldRunOnAccept = acceptOrReject,
                    appName = appName,
                    type = type,
                    onAccept = {
                        val result =
                            if (bunkerRequest.encryptedData?.result == "Could not decrypt the message") {
                                ""
                            } else {
                                bunkerRequest.encryptedData?.result ?: ""
                            }

                        BunkerRequestUtils.sendResult(
                            context = context,
                            account = account,
                            key = key,
                            response = result,
                            bunkerRequest = bunkerRequest,
                            kind = null,
                            onLoading = onLoading,
                            permissions = null,
                            appName = appName,
                            signPolicy = null,
                            shouldCloseApplication = bunkerRequest.closeApplication,
                            rememberType = it,
                        )
                    },
                    onReject = {
                        BunkerRequestUtils.sendRejection(
                            key = key,
                            account = account,
                            bunkerRequest = bunkerRequest,
                            appName = appName,
                            rememberType = it,
                            signerType = type,
                            kind = null,
                            onLoading = onLoading,
                        )
                    },
                )
            } else if (type == SignerType.SIGN_MESSAGE) {
                val permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == type.toString()
                    }

                val acceptUntil = permission?.acceptUntil ?: 0
                val rejectUntil = permission?.rejectUntil ?: 0

                val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                    null
                } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                    false
                } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                    true
                } else {
                    null
                }

                BunkerSignMessage(
                    account = account,
                    modifier = modifier,
                    content = bunkerRequest.request.params.first(),
                    shouldRunOnAccept = acceptOrReject,
                    appName = appName,
                    type = type,
                    onAccept = {
                        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                            val result = account.signString(bunkerRequest.request.params.first())

                            BunkerRequestUtils.sendResult(
                                context = context,
                                account = account,
                                key = key,
                                response = result,
                                bunkerRequest = bunkerRequest,
                                kind = null,
                                onLoading = onLoading,
                                permissions = null,
                                appName = appName,
                                signPolicy = null,
                                shouldCloseApplication = bunkerRequest.closeApplication,
                                rememberType = it,
                            )
                        }
                    },
                    onReject = {
                        BunkerRequestUtils.sendRejection(
                            key = key,
                            account = account,
                            bunkerRequest = bunkerRequest,
                            appName = appName,
                            rememberType = it,
                            signerType = type,
                            kind = null,
                            onLoading = onLoading,
                        )
                    },
                )
            }
        }
    }
}
