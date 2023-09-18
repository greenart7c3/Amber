package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.DrawerValue
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.rememberDrawerState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.Drawer
import com.greenart7c3.nostrsigner.ui.components.MainAppBar
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

fun sendResult(
    context: Context,
    packageName: String?,
    account: Account,
    key: String,
    rememberChoice: Boolean,
    clipboardManager: ClipboardManager,
    event: String,
    id: String,
    value: String
) {
    val activity = context.getAppCompatActivity()
    if (packageName != null) {
        account.savedApps[key] = rememberChoice
        LocalPreferences.saveToEncryptedStorage(account)
        val intent = Intent()
        intent.putExtra("signature", value)
        intent.putExtra("id", id)
        intent.putExtra("event", event)
        activity?.setResult(RESULT_OK, intent)
    } else {
        clipboardManager.setText(AnnotatedString(value))
        Toast.makeText(
            context,
            context.getString(R.string.signature_copied_to_the_clipboard),
            Toast.LENGTH_SHORT
        ).show()
    }
    activity?.finish()
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(account: Account, accountStateViewModel: AccountStateViewModel, json: IntentData?, packageName: String?) {
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Scaffold(
        scaffoldState = scaffoldState,
        drawerContent = {
            Drawer(
                accountStateViewModel = accountStateViewModel,
                account = account,
                scaffoldState = scaffoldState
            )
        },
        topBar = {
            MainAppBar(
                scaffoldState,
                accountStateViewModel,
                account
            )
        }
    ) {
        if (json == null) {
            Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally
            ) {
                Text("No event to sign")
            }
        } else {
            json.let {
                var key = "$packageName-${it.type}"
                val appName = packageName ?: it.name
                when (it.type) {
                    SignerType.GET_PUBLIC_KEY -> {
                        val remember = remember {
                            mutableStateOf(account.savedApps[key] ?: false)
                        }
                        val shouldRunOnAccept = account.savedApps[key] ?: false
                        LoginWithPubKey(
                            shouldRunOnAccept,
                            remember,
                            packageName,
                            appName,
                            {
                                val sig = account.keyPair.pubKey.toNpub()
                                coroutineScope.launch {
                                    sendResult(
                                        context,
                                        packageName,
                                        account,
                                        key,
                                        remember.value,
                                        clipboardManager,
                                        "",
                                        "",
                                        sig
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
                            shouldRunOnAccept,
                            remember,
                            packageName,
                            appName,
                            it.type,
                            {
                                try {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val sig = try {
                                            AmberUtils.encryptOrDecryptData(
                                                it.data,
                                                it.type,
                                                account,
                                                it.pubKey
                                            ) ?: context.getString(R.string.could_not_decrypt_the_message)
                                        } catch (e: Exception) {
                                            context.getString(R.string.could_not_decrypt_the_message)
                                        }

                                        val result = if (sig == context.getString(R.string.could_not_decrypt_the_message) && (it.type == SignerType.DECRYPT_ZAP_EVENT)) {
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
                                            "",
                                            it.id,
                                            result
                                        )
                                    }

                                    return@EncryptDecryptData
                                } catch (e: Exception) {
                                    val message = if (it.type.toString().contains("ENCRYPT", true)) "encrypt" else "decrypt"
                                    coroutineScope.launch {
                                        Toast.makeText(
                                            context,
                                            "Error to $message data",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@EncryptDecryptData
                                }
                            },
                            {
                                context.getAppCompatActivity()?.finish()
                            }
                        )
                    }
                    else -> {
                        val event = IntentUtils.getIntent(it.data, account.keyPair)
                        key = "$packageName-${it.type}-${event.kind}"
                        val remember = remember {
                            mutableStateOf(account.savedApps[key] ?: false)
                        }
                        val shouldRunOnAccept = account.savedApps[key] ?: false
                        EventData(
                            shouldRunOnAccept,
                            remember,
                            packageName,
                            appName,
                            event,
                            event.toJson(),
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

                                val localEvent = com.vitorpamplona.quartz.events.Event.fromJson(it.data)
                                if (localEvent is LnZapRequestEvent && localEvent.tags.any { tag -> tag.any { t -> t == "anon" } }) {
                                    val resultEvent = AmberUtils.getZapRequestEvent(localEvent, account.keyPair.privKey)
                                    coroutineScope.launch {
                                        sendResult(
                                            context,
                                            packageName,
                                            account,
                                            key,
                                            remember.value,
                                            clipboardManager,
                                            resultEvent.toJson(),
                                            it.id,
                                            resultEvent.toJson()
                                        )
                                    }
                                } else {
                                    val signedEvent = AmberUtils.getSignedEvent(event, account.keyPair.privKey)

                                    coroutineScope.launch {
                                        sendResult(
                                            context,
                                            packageName,
                                            account,
                                            key,
                                            remember.value,
                                            clipboardManager,
                                            signedEvent.toJson(),
                                            it.id,
                                            signedEvent.sig
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
        }
    }
}

@Composable
fun AppTitle(appName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = appName,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
fun RememberMyChoice(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    onAccept: () -> Unit
) {
    if (shouldRunOnAccept) {
        LaunchedEffect(Unit) {
            onAccept()
        }
    }
    if (packageName != null) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    remember.value = !remember.value
                }
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Remember my choice and don't ask again"
            )
            Switch(
                checked = remember.value,
                onCheckedChange = {
                    remember.value = !remember.value
                }
            )
        }
    }
}

@Composable
fun AcceptRejectButtons(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(10.dp),
        Arrangement.Center
    ) {
        Button(
            shape = ButtonBorder,
            onClick = onReject
        ) {
            Text("Reject")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            shape = ButtonBorder,
            onClick = onAccept
        ) {
            Text("Accept")
        }
    }
}

@Composable
fun EncryptDecryptData(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    type: SignerType,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        AppTitle(appName)
        Spacer(Modifier.size(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(6.dp)) {
                val message = when (type) {
                    SignerType.NIP44_ENCRYPT -> "encrypt nip44"
                    SignerType.NIP04_ENCRYPT -> "encrypt nip04"
                    SignerType.NIP44_DECRYPT -> "decrypt nip44"
                    SignerType.NIP04_DECRYPT -> "decrypt nip04"
                    SignerType.DECRYPT_ZAP_EVENT -> "decrypt zap event"
                    else -> "encrypt/decrypt"
                }
                Text(
                    "wants you to $message data",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        RememberMyChoice(
            shouldRunOnAccept,
            remember,
            packageName,
            onAccept
        )

        AcceptRejectButtons(
            onAccept,
            onReject
        )
    }
}

@Composable
fun LoginWithPubKey(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        AppTitle(appName)
        Spacer(Modifier.size(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .padding(6.dp)
            ) {
                Text(
                    "wants to read your public key",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        RememberMyChoice(
            shouldRunOnAccept,
            remember,
            packageName,
            onAccept
        )

        AcceptRejectButtons(
            onAccept,
            onReject
        )
    }
}

@Composable
fun EventData(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    appName: String,
    event: Event,
    rawJson: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var showMore by remember {
        mutableStateOf(false)
    }
    val eventDescription = event.description()

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        AppTitle(appName)
        Spacer(Modifier.size(4.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(Modifier.padding(6.dp)) {
                val message = if (event.kind == 22242) {
                    "client authentication"
                } else {
                    eventDescription
                }
                Text(
                    "wants you to sign a $message",
                    fontWeight = FontWeight.Bold
                )

                EventRow(
                    Icons.Default.CalendarMonth,
                    "Kind ",
                    "${event.kind}"
                )

                EventRow(
                    Icons.Default.AccessTime,
                    "Created at ",
                    TimeUtils.format(event.createdAt)
                )

                EventRow(
                    Icons.Default.Person,
                    "Signed by ",
                    event.pubKey.toShortenHex()
                )

                EventRow(
                    Icons.Default.ContentPaste,
                    "Content ",
                    event.content
                )

                Divider(
                    modifier = Modifier.padding(top = 15.dp),
                    thickness = Dp.Hairline
                )
            }
        }
        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) "Show Details" else "Hide Details"
        )
        if (showMore) {
            RawJson(rawJson, Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            remember,
            packageName,
            onAccept
        )

        AcceptRejectButtons(
            onAccept,
            onReject
        )
    }
}

@Composable
fun RawJsonButton(
    onCLick: () -> Unit,
    text: String
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            shape = ButtonBorder,
            onClick = onCLick
        ) {
            Text(text)
        }
    }
}

@Composable
fun RawJson(
    rawJson: String,
    modifier: Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    OutlinedTextField(
        modifier = modifier,
        value = TextFieldValue(JSONObject(rawJson).toString(2)),
        onValueChange = { },
        readOnly = true
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            shape = ButtonBorder,
            onClick = {
                clipboardManager.setText(AnnotatedString(rawJson))

                coroutineScope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.raw_json_copied_to_the_clipboard),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        ) {
            Text("Copy raw json")
        }
    }
}

@Composable
fun EventRow(
    icon: ImageVector,
    title: String,
    content: String
) {
    Divider(
        modifier = Modifier.padding(vertical = 15.dp),
        thickness = Dp.Hairline
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colors.onBackground
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = "$title ",
            fontWeight = FontWeight.Bold
        )
        Text(
            content,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
