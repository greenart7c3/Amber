package com.greenart7c3.nostrsigner.ui

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.GsonBuilder
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.EncryptDecryptData
import com.greenart7c3.nostrsigner.ui.components.EventData
import com.greenart7c3.nostrsigner.ui.components.LoginWithPubKey
import com.greenart7c3.nostrsigner.ui.components.RememberMyChoice
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier,
    intents: List<IntentData?>,
    packageName: String?,
    applicationName: String?,
    account: Account
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var loading by remember { mutableStateOf(false) }

    if (loading) {
        Box(
            modifier
        ) {
            Column(
                Modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
        }
    } else {
        Box(
            modifier
        ) {
            if (intents.filterNotNull().isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    Arrangement.Center,
                    Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.no_event_to_sign))
                }
            } else {
                if (intents.size == 1) {
                    intents.first()?.let {
                        var key = "$packageName-${it.type}"
                        val appName = packageName ?: it.name
                        when (it.type) {
                            SignerType.GET_PUBLIC_KEY -> {
                                val remember = remember {
                                    mutableStateOf(account.savedApps[key] ?: false)
                                }
                                LoginWithPubKey(
                                    appName,
                                    applicationName,
                                    it.permissions,
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
                                                it,
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
                                    it.data,
                                    shouldRunOnAccept,
                                    remember,
                                    packageName,
                                    applicationName,
                                    appName,
                                    it.type,
                                    {
                                        if (it.type == SignerType.NIP04_ENCRYPT && it.data.contains(
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
                                                            it.data,
                                                            it.type,
                                                            account,
                                                            it.pubKey
                                                        )
                                                            ?: "Could not decrypt the message"
                                                    } catch (e: Exception) {
                                                        "Could not decrypt the message"
                                                    }

                                                    if (it.type == SignerType.NIP04_ENCRYPT && sig == "Could not decrypt the message") {
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
                                                            if (sig == "Could not decrypt the message" && (it.type == SignerType.DECRYPT_ZAP_EVENT)) {
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
                                                            it
                                                        )
                                                    }
                                                }

                                                return@EncryptDecryptData
                                            } catch (e: Exception) {
                                                val message = if (it.type.toString().contains("ENCRYPT", true)) {
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
                                                it.data,
                                                it.type,
                                                account,
                                                it.pubKey
                                            )
                                                ?: "Could not decrypt the message"
                                        } catch (e: Exception) {
                                            "Could not decrypt the message"
                                        }
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
                                    applicationName,
                                    event,
                                    it.data,
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
                                            Event.fromJson(it.data)
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
                                                    it
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
                } else {
                    Column(
                        Modifier.fillMaxSize()
                    ) {
                        var selectAll by remember {
                            mutableStateOf(false)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable {
                                    selectAll = !selectAll
                                    intents
                                        .filterNotNull()
                                        .forEach {
                                            it.checked.value = selectAll
                                        }
                                }
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = stringResource(R.string.select_deselect_all)
                            )
                            Switch(
                                checked = selectAll,
                                onCheckedChange = {
                                    selectAll = !selectAll
                                    intents.filterNotNull().forEach {
                                        it.checked.value = selectAll
                                    }
                                }
                            )
                        }
                        LazyColumn(
                            Modifier.fillMaxHeight(0.9f)
                        ) {
                            items(intents.size) {
                                var isExpanded by remember { mutableStateOf(false) }
                                Card(
                                    Modifier
                                        .padding(4.dp)
                                        .clickable {
                                            isExpanded = !isExpanded
                                        }
                                ) {
                                    intents[it]?.let {
                                        val name = LocalPreferences.getAccountName(it.currentAccount)
                                        Row(
                                            Modifier
                                                .fillMaxWidth(),
                                            Arrangement.Center,
                                            Alignment.CenterVertically
                                        ) {
                                            Text(
                                                name.ifBlank { it.currentAccount.toShortenHex() },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.run {
                                                if (isExpanded) {
                                                    KeyboardArrowDown
                                                } else {
                                                    KeyboardArrowUp
                                                }
                                            },
                                            contentDescription = "",
                                            tint = Color.LightGray
                                        )

                                        intents[it]?.let {
                                            val appName = applicationName ?: packageName ?: it.name
                                            val text = if (it.type == SignerType.SIGN_EVENT) {
                                                val event =
                                                    IntentUtils.getIntent(it.data, account.keyPair)
                                                if (event.kind == 22242) "requests client authentication" else "requests event signature"
                                            } else {
                                                when (it.type) {
                                                    SignerType.NIP44_ENCRYPT -> stringResource(R.string.encrypt_nip44)
                                                    SignerType.NIP04_ENCRYPT -> stringResource(R.string.encrypt_nip04)
                                                    SignerType.NIP44_DECRYPT -> stringResource(R.string.decrypt_nip44)
                                                    SignerType.NIP04_DECRYPT -> stringResource(R.string.decrypt_nip04)
                                                    SignerType.DECRYPT_ZAP_EVENT -> stringResource(R.string.decrypt_zap_event)
                                                    else -> stringResource(R.string.encrypt_decrypt)
                                                }
                                            }
                                            Text(
                                                modifier = Modifier.weight(1f),
                                                text = buildAnnotatedString {
                                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                        append(appName)
                                                    }
                                                    append(" $text")
                                                },
                                                fontSize = 18.sp
                                            )

                                            Switch(
                                                checked = it.checked.value,
                                                onCheckedChange = { _ ->
                                                    it.checked.value = !it.checked.value
                                                }
                                            )
                                        }
                                    }
                                    intents[it]?.let {
                                        if (isExpanded) {
                                            Column(
                                                Modifier
                                                    .fillMaxSize()
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    "Event content",
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val content =
                                                    if (it.type == SignerType.SIGN_EVENT) {
                                                        val event = IntentUtils.getIntent(
                                                            it.data,
                                                            account.keyPair
                                                        )
                                                        if (event.kind == 22242) event.relay() else event.content
                                                    } else {
                                                        it.data
                                                    }
                                                Text(
                                                    content,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 8.dp)
                                                )
                                                RememberMyChoice(
                                                    shouldRunOnAccept = false,
                                                    it.rememberMyChoice.value,
                                                    packageName,
                                                    { }
                                                ) {
                                                    it.rememberMyChoice.value = !it.rememberMyChoice.value
                                                    intents.filter { intentData ->
                                                        intentData?.type == it.type
                                                    }.forEach { intentData ->
                                                        intentData?.let { intent ->
                                                            intent.rememberMyChoice.value =
                                                                it.rememberMyChoice.value
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            Arrangement.Center
                        ) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ButtonBorder,
                                onClick = {
                                    loading = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            val activity = context.getAppCompatActivity()
                                            val results = mutableListOf<Result>()

                                            for (intentData in intents.filterNotNull()) {
                                                val localAccount =
                                                    if (intentData.currentAccount.isNotBlank()) {
                                                        LocalPreferences.loadFromEncryptedStorage(
                                                            intentData.currentAccount
                                                        )
                                                    } else {
                                                        account
                                                    } ?: continue

                                                if (packageName != null) {
                                                    if (intentData.type == SignerType.SIGN_EVENT) {
                                                        val localEvent = try {
                                                            Event.fromJson(intentData.data)
                                                        } catch (e: Exception) {
                                                            Event.fromJson(
                                                                IntentUtils.getIntent(
                                                                    intentData.data,
                                                                    localAccount.keyPair
                                                                ).toJson()
                                                            )
                                                        }
                                                        val key = "$packageName-${intentData.type}-${localEvent.kind}"
                                                        if (intentData.rememberMyChoice.value) {
                                                            localAccount.savedApps[key] =
                                                                intentData.rememberMyChoice.value
                                                            LocalPreferences.saveToEncryptedStorage(
                                                                localAccount
                                                            )
                                                        }
                                                        localAccount.signer.sign<Event>(
                                                            localEvent.createdAt,
                                                            localEvent.kind,
                                                            localEvent.tags,
                                                            localEvent.content
                                                        ) { signedEvent ->
                                                            results.add(
                                                                Result(
                                                                    null,
                                                                    signedEvent.sig,
                                                                    intentData.id
                                                                )
                                                            )
                                                        }
                                                    } else {
                                                        val key = "$packageName-${intentData.type}"
                                                        localAccount.savedApps[key] =
                                                            intentData.rememberMyChoice.value
                                                        LocalPreferences.saveToEncryptedStorage(
                                                            localAccount
                                                        )
                                                        val signature = AmberUtils.encryptOrDecryptData(
                                                            intentData.data,
                                                            intentData.type,
                                                            localAccount,
                                                            intentData.pubKey
                                                        ) ?: continue
                                                        results.add(
                                                            Result(
                                                                null,
                                                                signature,
                                                                intentData.id
                                                            )
                                                        )
                                                    }
                                                }
                                            }

                                            if (results.isNotEmpty()) {
                                                val gson = GsonBuilder().serializeNulls().create()
                                                val json = gson.toJson(results)
                                                val intent = Intent()
                                                intent.putExtra("results", json)
                                                activity?.setResult(Activity.RESULT_OK, intent)
                                            }
                                            activity?.finish()
                                        } finally {
                                            loading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Confirm")
                            }
                        }
                    }
                }
            }
        }
    }
}

class Result(
    val `package`: String?,
    val signature: String?,
    val id: String?
)
