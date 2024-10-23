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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EditConfigurationScreen(
    modifier: Modifier = Modifier,
    database: AppDatabase,
    key: String,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var application by remember { mutableStateOf<ApplicationWithPermissions?>(null) }
    var name by remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    val relays = remember { mutableStateListOf<RelaySetupInfo>() }
    val textFieldRelay = remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    val isLoading = remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            application = database.applicationDao().getByKey(key)
            name = TextFieldValue(AnnotatedString(application?.application?.name ?: ""))

            application?.application?.relays?.forEach {
                relays.add(
                    it.copy(),
                )
            }

            isLoading.value = false
        }
    }

    if (isLoading.value) {
        CenterCircularProgressIndicator(modifier)
    } else {
        Column(
            modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.edit_configuration_description))

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                onValueChange = {
                    name = it
                    scope.launch(Dispatchers.IO) {
                        application?.let {
                            val localApplicationData =
                                it.application.copy(
                                    name = name.text,
                                    relays = relays,
                                )
                            database.applicationDao().delete(it.application)
                            database.applicationDao().insertApplicationWithPermissions(
                                ApplicationWithPermissions(
                                    localApplicationData,
                                    it.permissions,
                                ),
                            )
                        }
                    }
                },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                                relays,
                                scope,
                                accountStateViewModel,
                                account,
                                context,
                                onDone = {
                                    scope.launch(Dispatchers.IO) {
                                        application?.let {
                                            val localApplicationData =
                                                it.application.copy(
                                                    name = name.text,
                                                    relays = relays,
                                                )
                                            database.applicationDao().delete(it.application)
                                            database.applicationDao().insertApplicationWithPermissions(
                                                ApplicationWithPermissions(
                                                    localApplicationData,
                                                    it.permissions,
                                                ),
                                            )
                                            if (NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                                                NostrSigner.getInstance().checkForNewRelays()
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    },
                ),
                label = {
                    Text(stringResource(R.string.wss))
                },
            )

            AmberButton(
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
                                scope.launch(Dispatchers.IO) {
                                    application?.let {
                                        val localApplicationData =
                                            it.application.copy(
                                                name = name.text,
                                                relays = relays,
                                            )
                                        database.applicationDao().delete(it.application)
                                        database.applicationDao().insertApplicationWithPermissions(
                                            ApplicationWithPermissions(
                                                localApplicationData,
                                                it.permissions,
                                            ),
                                        )
                                        if (NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                                            NostrSigner.getInstance().checkForNewRelays()
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
                content = {
                    Text(stringResource(R.string.add))
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (application?.application?.shouldShowRelays() == true) {
                LazyColumn(
                    Modifier.weight(1f),
                ) {
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
                                    scope.launch(Dispatchers.IO) {
                                        application?.let {
                                            val localApplicationData =
                                                it.application.copy(
                                                    name = name.text,
                                                    relays = relays,
                                                )
                                            database.applicationDao().delete(it.application)
                                            database.applicationDao().insertApplicationWithPermissions(
                                                ApplicationWithPermissions(
                                                    localApplicationData,
                                                    it.permissions,
                                                ),
                                            )
                                            if (NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                                                NostrSigner.getInstance().checkForNewRelays()
                                            }
                                        }
                                    }
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    onClick = {
                        application?.let {
                            scope.launch(Dispatchers.IO) {
                                database.applicationDao().delete(it.application)

                                if (NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                                    NostrSigner.getInstance().checkForNewRelays()
                                }

                                scope.launch(Dispatchers.Main) {
                                    navController.navigate(Route.Applications.route) {
                                        popUpTo(0)
                                    }
                                }
                            }
                        }
                    },
                    content = {
                        Text(stringResource(R.string.delete_application))
                    },
                )
            }
        }
    }
}
