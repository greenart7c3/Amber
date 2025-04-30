package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DefaultProfileRelaysScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val textFieldRelay = remember {
        mutableStateOf(TextFieldValue(""))
    }
    val isLoading = remember {
        mutableStateOf(false)
    }
    val relays2 =
        remember {
            val localRelays = mutableStateListOf<RelaySetupInfo>()
            Amber.instance.settings.defaultProfileRelays.forEach {
                localRelays.add(
                    it.copy(),
                )
            }
            localRelays
        }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize(),
    ) {
        if (isLoading.value) {
            CenterCircularProgressIndicator(
                Modifier,
                text = stringResource(R.string.testing_relay),
            )
        } else {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Text(
                    text = stringResource(R.string.manage_the_relays_used_to_fetch_your_profile_data),
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth(),
                    value = textFieldRelay.value.text,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
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
                                    relays2,
                                    scope,
                                    accountStateViewModel,
                                    account,
                                    context,
                                    onDone = {
                                        Amber.instance.settings = Amber.instance.settings.copy(
                                            defaultProfileRelays = relays2,
                                        )
                                        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                        scope.launch(Dispatchers.IO) {
                                            @Suppress("KotlinConstantConditions")
                                            if (BuildConfig.FLAVOR != "offline") {
                                                Amber.instance.checkForNewRelays()
                                                NotificationDataSource.stop()
                                                delay(2000)
                                                NotificationDataSource.start()
                                                isLoading.value = false
                                            } else {
                                                isLoading.value = false
                                            }
                                        }
                                    },
                                )
                            }
                        },
                    ),
                    label = {
                        Text("Relay")
                    },
                )

                AmberButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            onAddRelay(
                                textFieldRelay,
                                isLoading,
                                relays2,
                                scope,
                                accountStateViewModel,
                                account,
                                context,
                                onDone = {
                                    isLoading.value = true
                                    Amber.instance.settings = Amber.instance.settings.copy(
                                        defaultProfileRelays = relays2,
                                    )
                                    LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                    scope.launch(Dispatchers.IO) {
                                        @Suppress("KotlinConstantConditions")
                                        if (BuildConfig.FLAVOR != "offline") {
                                            Amber.instance.checkForNewRelays()
                                            NotificationDataSource.stop()
                                            delay(2000)
                                            NotificationDataSource.start()
                                            isLoading.value = false
                                        } else {
                                            isLoading.value = false
                                        }
                                    }
                                },
                            )
                        }
                    },
                    text = stringResource(R.string.add),
                )

                LazyColumn(
                    Modifier
                        .weight(1f),
                ) {
                    items(relays2.size) {
                        RelayCard(
                            relay = relays2[it].url,
                            onClick = {
                                isLoading.value = true
                                relays2.removeAt(it)
                                Amber.instance.settings = Amber.instance.settings.copy(
                                    defaultProfileRelays = relays2,
                                )
                                LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                scope.launch(Dispatchers.IO) {
                                    @Suppress("KotlinConstantConditions")
                                    if (BuildConfig.FLAVOR != "offline") {
                                        Amber.instance.checkForNewRelays()
                                        NotificationDataSource.stop()
                                        delay(2000)
                                        NotificationDataSource.start()
                                        isLoading.value = false
                                    } else {
                                        isLoading.value = false
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
