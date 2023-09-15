package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.util.Log
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
import com.greenart7c3.nostrsigner.service.CryptoUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.model.Event
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.Drawer
import com.greenart7c3.nostrsigner.ui.components.MainAppBar
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.vitorpamplona.quartz.crypto.CryptoUtils.decryptNIP04
import com.vitorpamplona.quartz.crypto.CryptoUtils.decryptNIP44
import com.vitorpamplona.quartz.crypto.CryptoUtils.encryptNIP44
import com.vitorpamplona.quartz.crypto.CryptoUtils.getSharedSecretNIP44
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.Charset
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.vitorpamplona.quartz.encoders.hexToByteArray as hexToByteArray1

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
                                    val activity = context.getAppCompatActivity()
                                    if (packageName != null) {
                                        account.savedApps[key] = remember.value
                                        LocalPreferences.saveToEncryptedStorage(account)
                                        val intent = Intent()
                                        intent.putExtra("signature", sig)
                                        activity?.setResult(RESULT_OK, intent)
                                    } else {
                                        clipboardManager.setText(AnnotatedString(sig))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.signature_copied_to_the_clipboard),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    activity?.finish()
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
                                            when (it.type) {
                                                SignerType.DECRYPT_ZAP_EVENT -> {
                                                    val event = com.vitorpamplona.quartz.events.Event.fromJson(it.data) as LnZapRequestEvent
                                                    val loggedInPrivateKey = account.keyPair.privKey

                                                    if (event.isPrivateZap()) {
                                                        val recipientPK = event.zappedAuthor().firstOrNull()
                                                        val recipientPost = event.zappedPost().firstOrNull()
                                                        if (recipientPK == account.keyPair.pubKey.toHexKey()) {
                                                            // if the receiver is logged in, these are the params.
                                                            val privateKeyToUse = loggedInPrivateKey
                                                            val pubkeyToUse = event.pubKey

                                                            event.getPrivateZapEvent(privateKeyToUse, pubkeyToUse)?.toJson() ?: ""
                                                        } else {
                                                            // if the sender is logged in, these are the params
                                                            val altPubkeyToUse = recipientPK
                                                            val altPrivateKeyToUse = if (recipientPost != null) {
                                                                LnZapRequestEvent.createEncryptionPrivateKey(
                                                                    loggedInPrivateKey.toHexKey(),
                                                                    recipientPost,
                                                                    event.createdAt
                                                                )
                                                            } else if (recipientPK != null) {
                                                                LnZapRequestEvent.createEncryptionPrivateKey(
                                                                    loggedInPrivateKey.toHexKey(),
                                                                    recipientPK,
                                                                    event.createdAt
                                                                )
                                                            } else {
                                                                null
                                                            }

                                                            try {
                                                                if (altPrivateKeyToUse != null && altPubkeyToUse != null) {
                                                                    val altPubKeyFromPrivate = com.vitorpamplona.quartz.crypto.CryptoUtils.pubkeyCreate(altPrivateKeyToUse).toHexKey()

                                                                    if (altPubKeyFromPrivate == event.pubKey) {
                                                                        val result = event.getPrivateZapEvent(altPrivateKeyToUse, altPubkeyToUse)

                                                                        result?.toJson() ?: ""
                                                                    } else {
                                                                        null
                                                                    }
                                                                } else {
                                                                    null
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("Account", "Failed to create pubkey for ZapRequest ${event.id}", e)
                                                                null
                                                            }
                                                        }
                                                    } else {
                                                        null
                                                    }
                                                }
                                                SignerType.NIP04_DECRYPT -> {
                                                    CryptoUtils.decrypt(
                                                        it.data,
                                                        account.keyPair.privKey,
                                                        Hex.decode(it.pubKey)
                                                    )
                                                }
                                                SignerType.NIP04_ENCRYPT -> {
                                                    CryptoUtils.encrypt(
                                                        it.data,
                                                        account.keyPair.privKey,
                                                        Hex.decode(it.pubKey)
                                                    )
                                                }
                                                SignerType.NIP44_ENCRYPT -> {
                                                    val sharedSecret = getSharedSecretNIP44(account.keyPair.privKey, it.pubKey.hexToByteArray1())

                                                    encodeNIP44(
                                                        encryptNIP44(
                                                            it.data,
                                                            sharedSecret
                                                        )
                                                    )
                                                }
                                                else -> {
                                                    val toDecrypt = decodeNIP44(it.data) ?: return@launch
                                                    when (toDecrypt.v) {
                                                        Nip44Version.NIP04.versionCode -> decryptNIP04(toDecrypt, account.keyPair.privKey, it.pubKey.hexToByteArray1())
                                                        Nip44Version.NIP44.versionCode -> decryptNIP44(toDecrypt, account.keyPair.privKey, it.pubKey.hexToByteArray1())
                                                        else -> null
                                                    }
                                                }
                                            } ?: "Could not decrypt the message"
                                        } catch (e: Exception) {
                                            "Could not decrypt the message"
                                        }

                                        val activity = context.getAppCompatActivity()
                                        if (packageName != null) {
                                            if (remember.value) {
                                                account.savedApps[key] = remember.value
                                                LocalPreferences.saveToEncryptedStorage(account)
                                            }
                                            val intent = Intent()
                                            if (sig == "Could not decrypt the message" && (it.type == SignerType.DECRYPT_ZAP_EVENT)) {
                                                intent.putExtra("signature", "")
                                            } else {
                                                intent.putExtra("signature", sig)
                                            }
                                            if (it.type == SignerType.NIP44_DECRYPT || it.type == SignerType.NIP04_DECRYPT) {
                                                intent.putExtra("id", it.id)
                                            }
                                            activity?.setResult(RESULT_OK, intent)
                                        } else {
                                            clipboardManager.setText(AnnotatedString(sig))
                                            coroutineScope.launch {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.signature_copied_to_the_clipboard),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        activity?.finish()
                                    }

                                    return@EncryptDecryptData
                                } catch (e: Exception) {
                                    val message = if (it.type == SignerType.NIP04_ENCRYPT) "encrypt" else "decrypt"
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
                                if (localEvent is LnZapRequestEvent) {
                                    val isPrivateZap = localEvent.tags.any { tag -> tag.any { t -> t == "anon" } }
                                    val originalNoteId = localEvent.zappedPost()[0]
                                    val pubkey = localEvent.zappedAuthor()[0]
                                    var privkey = account.keyPair.privKey
                                    var pubKey = account.keyPair.pubKey.toHexKey()
                                    if (isPrivateZap) {
                                        val encryptionPrivateKey = LnZapRequestEvent.createEncryptionPrivateKey(
                                            privkey.toHexKey(),
                                            originalNoteId,
                                            localEvent.createdAt
                                        )
                                        val noteJson = (LnZapPrivateEvent.create(privkey, listOf(localEvent.tags[0], localEvent.tags[1]), localEvent.content)).toJson()
                                        val encryptedContent = encryptPrivateZapMessage(
                                            noteJson,
                                            encryptionPrivateKey,
                                            pubkey.hexToByteArray()
                                        )
                                        var tags = localEvent.tags.filter { !it.contains("anon") }
                                        tags = tags + listOf(listOf("anon", encryptedContent))
                                        privkey = encryptionPrivateKey // sign event with generated privkey
                                        pubKey = com.vitorpamplona.quartz.crypto.CryptoUtils.pubkeyCreate(encryptionPrivateKey).toHexKey() // updated event with according pubkey

                                        val id = com.vitorpamplona.quartz.events.Event.generateId(
                                            pubKey,
                                            localEvent.createdAt,
                                            LnZapRequestEvent.kind,
                                            tags,
                                            ""
                                        )
                                        val sig = com.vitorpamplona.quartz.crypto.CryptoUtils.sign(id, privkey)
                                        val event = LnZapRequestEvent(id.toHexKey(), pubKey, localEvent.createdAt, tags, "", sig.toHexKey())
                                        coroutineScope.launch {
                                            val activity = context.getAppCompatActivity()
                                            if (packageName != null) {
                                                if (remember.value) {
                                                    account.savedApps[key] = remember.value
                                                    LocalPreferences.saveToEncryptedStorage(account)
                                                }
                                                val intent = Intent()
                                                intent.putExtra("event", event.toJson())
                                                intent.putExtra("signature", event.toJson())

                                                activity?.setResult(RESULT_OK, intent)
                                            } else {
                                                clipboardManager.setText(AnnotatedString(event.toJson()))
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.signature_copied_to_the_clipboard),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            activity?.finish()
                                        }
                                    } else {
                                        val id = event.id.hexToByteArray1()
                                        val sig = CryptoUtils.sign(id, account.keyPair.privKey).toHexKey()

                                        coroutineScope.launch {
                                            val activity = context.getAppCompatActivity()
                                            if (packageName != null) {
                                                account.savedApps[key] = remember.value
                                                LocalPreferences.saveToEncryptedStorage(account)
                                                val intent = Intent()
                                                val signedEvent = Event(
                                                    event.id,
                                                    event.pubKey,
                                                    event.createdAt,
                                                    event.kind,
                                                    event.tags,
                                                    event.content,
                                                    sig
                                                )
                                                intent.putExtra("id", it.id)
                                                intent.putExtra("event", signedEvent.toJson())
                                                intent.putExtra("signature", sig)

                                                activity?.setResult(RESULT_OK, intent)
                                            } else {
                                                clipboardManager.setText(AnnotatedString(sig))
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.signature_copied_to_the_clipboard),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            activity?.finish()
                                        }
                                    }
                                } else {
                                    val id = event.id.hexToByteArray1()
                                    val sig = CryptoUtils.sign(id, account.keyPair.privKey).toHexKey()

                                    coroutineScope.launch {
                                        val activity = context.getAppCompatActivity()
                                        if (packageName != null) {
                                            if (remember.value) {
                                                account.savedApps[key] = remember.value
                                                LocalPreferences.saveToEncryptedStorage(account)
                                            }
                                            val intent = Intent()
                                            val signedEvent = Event(
                                                event.id,
                                                event.pubKey,
                                                event.createdAt,
                                                event.kind,
                                                event.tags,
                                                event.content,
                                                sig
                                            )
                                            intent.putExtra("id", it.id)
                                            intent.putExtra("event", signedEvent.toJson())
                                            intent.putExtra("signature", sig)

                                            activity?.setResult(RESULT_OK, intent)
                                        } else {
                                            clipboardManager.setText(AnnotatedString(sig))
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.signature_copied_to_the_clipboard),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        activity?.finish()
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
                val message = if (type == SignerType.NIP04_ENCRYPT) "encrypt" else "decrypt"
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

fun encryptPrivateZapMessage(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
    val sharedSecret = com.vitorpamplona.quartz.crypto.CryptoUtils.getSharedSecretNIP04(privkey, pubkey)
    val iv = ByteArray(16)
    SecureRandom().nextBytes(iv)

    val keySpec = SecretKeySpec(sharedSecret, "AES")
    val ivSpec = IvParameterSpec(iv)

    val utf8message = msg.toByteArray(Charset.forName("utf-8"))
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encryptedMsg = cipher.doFinal(utf8message)

    val encryptedMsgBech32 = Bech32.encode("pzap", Bech32.eight2five(encryptedMsg), Bech32.Encoding.Bech32)
    val ivBech32 = Bech32.encode("iv", Bech32.eight2five(iv), Bech32.Encoding.Bech32)

    return encryptedMsgBech32 + "_" + ivBech32
}
