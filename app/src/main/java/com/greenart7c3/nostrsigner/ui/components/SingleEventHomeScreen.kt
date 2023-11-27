package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.ui.sendResult
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SingleEventHomeScreen(
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account
) {
    var key = "$packageName-${intentData.type}"
    val appName = packageName ?: intentData.name
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    when (intentData.type) {
        SignerType.GET_PUBLIC_KEY -> {
            val remember = remember {
                mutableStateOf(account.savedApps[key] ?: false)
            }
            LoginWithPubKey(
                appName,
                applicationName,
                intentData.permissions,
                { permissions ->
                    val sig = account.keyPair.pubKey.toNpub()
                    coroutineScope.launch {
                        sendResult(
                            context,
                            packageName,
                            account,
                            key,
                            remember.value,
                            clipboardManager,
                            sig,
                            sig,
                            intentData,
                            permissions = permissions
                        )
                    }
                    return@LoginWithPubKey
                },
                {
                    context.getAppCompatActivity()?.finish()
                }
            )
        }

        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
            val remember = remember {
                mutableStateOf(account.savedApps[key] ?: false)
            }
            val shouldRunOnAccept = account.savedApps[key] ?: false
            EncryptDecryptData(
                intentData.data,
                shouldRunOnAccept,
                remember,
                packageName,
                applicationName,
                appName,
                intentData.type,
                {
                    if (intentData.type == SignerType.NIP04_ENCRYPT && intentData.data.contains(
                            "?iv=",
                            ignoreCase = true
                        )
                    ) {
                        coroutineScope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.message_already_encrypted),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@EncryptDecryptData
                    } else {
                        try {
                            coroutineScope.launch(Dispatchers.IO) {
                                val sig = try {
                                    AmberUtils.encryptOrDecryptData(
                                        intentData.data,
                                        intentData.type,
                                        account,
                                        intentData.pubKey
                                    )
                                        ?: "Could not decrypt the message"
                                } catch (e: Exception) {
                                    "Could not decrypt the message"
                                }

                                if (intentData.type == SignerType.NIP04_ENCRYPT && sig == "Could not decrypt the message") {
                                    coroutineScope.launch {
                                        Toast.makeText(
                                            context,
                                            "Error encrypting content",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@launch
                                } else {
                                    val result =
                                        if (sig == "Could not decrypt the message" && (intentData.type == SignerType.DECRYPT_ZAP_EVENT)) {
                                            ""
                                        } else {
                                            sig
                                        }

                                    sendResult(
                                        context,
                                        packageName,
                                        account,
                                        key,
                                        remember.value,
                                        clipboardManager,
                                        result,
                                        result,
                                        intentData
                                    )
                                }
                            }

                            return@EncryptDecryptData
                        } catch (e: Exception) {
                            val message = if (intentData.type.toString().contains("ENCRYPT", true)) {
                                context.getString(R.string.encrypt)
                            } else {
                                context.getString(R.string.decrypt)
                            }

                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.error_to_data,
                                        message
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@EncryptDecryptData
                        }
                    }
                },
                {
                    context.getAppCompatActivity()?.finish()
                },
                {
                    try {
                        AmberUtils.encryptOrDecryptData(
                            intentData.data,
                            intentData.type,
                            account,
                            intentData.pubKey
                        )
                            ?: "Could not decrypt the message"
                    } catch (e: Exception) {
                        "Could not decrypt the message"
                    }
                }
            )
        }

        else -> {
            val event = IntentUtils.getIntent(intentData.data, account.keyPair)
            key = "$packageName-${intentData.type}-${event.kind}"
            val remember = remember {
                mutableStateOf(account.savedApps[key] ?: false)
            }
            val shouldRunOnAccept = account.savedApps[key] ?: false
            EventData(
                shouldRunOnAccept,
                remember,
                packageName,
                appName,
                applicationName,
                event,
                intentData.data,
                {
                    if (event.pubKey != account.keyPair.pubKey.toHexKey()) {
                        coroutineScope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@EventData
                    }

                    val localEvent = try {
                        Event.fromJson(intentData.data)
                    } catch (e: Exception) {
                        Event.fromJson(event.toJson())
                    }

                    account.signer.sign<Event>(
                        localEvent.createdAt,
                        localEvent.kind,
                        localEvent.tags,
                        localEvent.content
                    ) { signedEvent ->
                        coroutineScope.launch {
                            sendResult(
                                context,
                                packageName,
                                account,
                                key,
                                remember.value,
                                clipboardManager,
                                signedEvent.toJson(),
                                if (localEvent is LnZapRequestEvent && localEvent.tags.any { tag -> tag.any { t -> t == "anon" } }) signedEvent.toJson() else signedEvent.sig,
                                intentData
                            )
                        }
                    }
                },
                {
                    context.getAppCompatActivity()?.finish()
                }
            )
        }
    }
}
