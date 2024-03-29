package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.ui.sendResult
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
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
                mutableStateOf(account.savedApps[key] != null)
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
                    if (remember.value) {
                        account.savedApps[key] = false
                    }

                    LocalPreferences.saveToEncryptedStorage(account)
                    context.getAppCompatActivity()?.intent = null
                    context.getAppCompatActivity()?.finish()
                }
            )
        }

        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
            val remember = remember {
                mutableStateOf(account.savedApps[key] != null)
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
                    if (remember.value) {
                        account.savedApps[key] = false
                    }

                    LocalPreferences.saveToEncryptedStorage(account)
                    context.getAppCompatActivity()?.intent = null
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
            val result = runCatching { IntentUtils.getIntent(intentData.data, account.keyPair) }
            if (result.isFailure) {
                var showError by remember {
                    mutableStateOf(false)
                }
                Column(
                    Modifier.fillMaxSize(),
                    Arrangement.Center,
                    Alignment.CenterHorizontally
                ) {
                    Text("Error occurred while trying to parse event")
                    Button(
                        onClick = {
                            showError = !showError
                        },
                        shape = ButtonBorder
                    ) {
                        Text(text = "Show/Hide", color = Color.White)
                    }
                    if (showError) {
                        OutlinedTextField(
                            value = result.exceptionOrNull()?.message ?: "",
                            onValueChange = { },
                            readOnly = true
                        )
                    }
                }
            } else {
                val event = result.getOrNull()!!
                key = "$packageName-${intentData.type}-${event.kind}"
                val remember = remember {
                    mutableStateOf(account.savedApps[key] != null)
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
                    intentData.type,
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
                        if (remember.value) {
                            account.savedApps[key] = false
                        }

                        LocalPreferences.saveToEncryptedStorage(account)
                        context.getAppCompatActivity()?.intent = null
                        context.getAppCompatActivity()?.finish()
                    }
                )
            }
        }
    }
}
