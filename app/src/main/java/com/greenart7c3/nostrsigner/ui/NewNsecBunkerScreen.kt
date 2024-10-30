package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.encoders.toHexKey
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NewNsecBunkerScreen(
    modifier: Modifier = Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    navController: NavController,
    database: AppDatabase,
) {
    val secret = remember { mutableStateOf(UUID.randomUUID().toString().substring(0, 6)) }
    var name by remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    val context = LocalContext.current

    val relays =
        remember {
            val localRelays = mutableStateListOf<RelaySetupInfo>()
            NostrSigner.getInstance().settings.defaultRelays.forEach {
                localRelays.add(
                    it.copy(),
                )
            }
            localRelays
        }

    val textFieldRelay = remember {
        mutableStateOf(TextFieldValue(""))
    }
    val scope = rememberCoroutineScope()
    val isLoading = remember { mutableStateOf(false) }

    if (isLoading.value) {
        CenterCircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize(),
        ) {
            Text(stringResource(R.string.create_nsecbunker_description))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(end = 16.dp),
                    value = textFieldRelay.value.text,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                    ),
                    onValueChange = {
                        textFieldRelay.value = TextFieldValue(it)
                    },
                    keyboardActions = KeyboardActions(
                        onDone = {
                            scope.launch(Dispatchers.IO) {
                                onAddRelay(
                                    textFieldRelay,
                                    isLoading,
                                    relays,
                                    scope,
                                    accountStateViewModel,
                                    account,
                                    context,
                                    onDone = {
                                    },
                                )
                            }
                        },
                    ),
                    label = {
                        Text(stringResource(R.string.wss))
                    },
                )
                IconButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            onAddRelay(
                                textFieldRelay,
                                isLoading,
                                relays,
                                scope,
                                accountStateViewModel,
                                account,
                                context,
                                onDone = {
                                },
                            )
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(relays.size) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            relays[it].url,
                            Modifier
                                .weight(0.9f)
                                .padding(8.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                relays.removeAt(it)
                            },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.delete),
                            )
                        }
                    }
                }
            }

            AmberButton(
                content = {
                    Text(text = stringResource(R.string.create))
                },
                onClick = {
                    if (relays.isEmpty()) {
                        accountStateViewModel.toast(
                            context.getString(R.string.relays),
                            context.getString(R.string.no_relays_added),
                        )

                        return@AmberButton
                    }

                    if (name.text.isBlank()) {
                        accountStateViewModel.toast(
                            context.getString(R.string.name),
                            context.getString(R.string.name_cannot_be_empty),
                        )

                        return@AmberButton
                    }

                    scope.launch(Dispatchers.IO) {
                        val application =
                            ApplicationEntity(
                                secret.value,
                                name.text,
                                relays,
                                "",
                                "",
                                "",
                                account.signer.keyPair.pubKey.toHexKey(),
                                false,
                                secret.value,
                                true,
                                account.signPolicy,
                            )

                        database.applicationDao().insertApplication(
                            application,
                        )
                        scope.launch(Dispatchers.Main) {
                            navController.navigate("NewNsecBunkerCreated/${secret.value}")
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun NewNsecBunkerCreatedScreen(
    database: AppDatabase,
    modifier: Modifier = Modifier,
    account: Account,
    key: String,
) {
    val isLoading = remember { mutableStateOf(false) }
    var application by remember { mutableStateOf(ApplicationEntity.empty()) }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) {
        isLoading.value = true
        launch(Dispatchers.IO) {
            application = database.applicationDao().getByKey(key)?.application ?: ApplicationEntity.empty()
            isLoading.value = false
        }
    }

    if (isLoading.value) {
        CenterCircularProgressIndicator(
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        val relays = application.relays.joinToString(separator = "&") { "relay=${it.url}" }
        val localSecret = "&secret=${application.secret}"
        val bunkerUri = "bunker://${account.signer.keyPair.pubKey.toHexKey()}?$relays$localSecret"

        Column(
            modifier = modifier
                .fillMaxSize(),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.your_nsec_bunker_has_been_created),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.use_this_url_in_your_app))

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                bunkerUri,
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            QrCodeDrawer(bunkerUri)

            Spacer(modifier = Modifier.height(8.dp))

            AmberButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(bunkerUri))
                },
                content = {
                    Text(text = stringResource(R.string.copy_to_clipboard))
                },
            )
        }
    }
}
