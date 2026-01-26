package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.actions.onAddRelay
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TrustScoreBadge
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun EditConfigurationScreen(
    modifier: Modifier = Modifier,
    key: String,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var application by remember { mutableStateOf<ApplicationWithPermissions?>(null) }
    var name by remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    val relays = remember { mutableStateListOf<NormalizedRelayUrl>() }
    val textFieldRelay = remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    val isLoading = remember { mutableStateOf(true) }
    var closeApp by remember { mutableStateOf(false) }
    val trustScores = remember { mutableStateMapOf<String, Int?>() }
    val loadingScores = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            application = Amber.instance.getDatabase(account.npub).dao().getByKey(key)
            name = TextFieldValue(AnnotatedString(application?.application?.name?.ifBlank { application?.application?.key?.toShortenHex() } ?: ""))
            closeApp = application?.application?.closeApplication != false

            application?.application?.relays?.forEach {
                relays.add(
                    it.copy(),
                )
            }

            if (!BuildFlavorChecker.isOfflineFlavor()) {
                relays.forEach { relay ->
                    val url = relay.url
                    if (!trustScores.containsKey(url)) {
                        loadingScores[url] = true
                        scope.launch(Dispatchers.IO) {
                            val score = TrustScoreService.getScore(url)
                            trustScores[url] = score
                            loadingScores[url] = false
                        }
                    }
                }
            }

            isLoading.value = false
        }
    }

    if (isLoading.value) {
        CenterCircularProgressIndicator(
            modifier,
            if (textFieldRelay.value.text.isNotBlank()) {
                stringResource(R.string.testing_relay)
            } else {
                null
            },
        )
    } else {
        Column(
            modifier
                .fillMaxSize(),
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
                },
                label = { Text(stringResource(R.string.name)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .clickable {
                        closeApp = !closeApp
                    },
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.close_application),
                )
                Switch(
                    checked = closeApp,
                    onCheckedChange = {
                        closeApp = it
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (application?.application?.shouldShowRelays() == true) {
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
                                    onDone = {},
                                )
                            }
                        },
                    ),
                    label = {
                        Text(stringResource(R.string.wss))
                    },
                    trailingIcon = {
                        IconButton(
                            colors = IconButtonDefaults.iconButtonColors().copy(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
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
                                        onDone = {},
                                    )
                                }
                            },
                            content = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.add),
                                )
                            },
                        )
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                relays.forEachIndexed { index, relay ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        colors = CardDefaults.cardColors().copy(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                relay.url,
                                Modifier
                                    .weight(0.9f)
                                    .padding(8.dp)
                                    .padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TrustScoreBadge(
                                score = trustScores[relay.url],
                                isLoading = loadingScores[relay.url] == true,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            IconButton(
                                onClick = {
                                    relays.removeAt(index)
                                },
                            ) {
                                Icon(
                                    ImageVector.vectorResource(R.drawable.delete),
                                    stringResource(R.string.delete),
                                )
                            }
                        }
                    }
                }
            } else {
                relays.forEachIndexed { _, relay ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        colors = CardDefaults.cardColors().copy(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                relay.url,
                                Modifier
                                    .weight(0.9f)
                                    .padding(8.dp)
                                    .padding(start = 8.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.LightGray,
                            )
                        }
                    }
                }
            }

            AmberButton(
                modifier = Modifier
                    .padding(vertical = 20.dp),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        application?.let {
                            val localApplicationData =
                                it.application.copy(
                                    name = name.text,
                                    relays = relays,
                                    closeApplication = closeApp,
                                )
                            Amber.instance.getDatabase(account.npub).dao().delete(it.application)
                            Amber.instance.getDatabase(account.npub).dao().insertApplicationWithPermissions(
                                ApplicationWithPermissions(
                                    localApplicationData,
                                    it.permissions,
                                ),
                            )
                            Amber.instance.checkForNewRelaysAndUpdateAllFilters()

                            scope.launch(Dispatchers.Main) {
                                navController.navigate(Route.Applications.route) {
                                    popUpTo(0)
                                }
                            }
                        }
                    }
                },
                text = stringResource(R.string.update),
            )

            AmberButton(
                modifier = Modifier
                    .padding(top = 60.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = orange,
                ),
                onClick = {
                    application?.let {
                        scope.launch(Dispatchers.IO) {
                            Amber.instance.getDatabase(account.npub).dao().delete(it.application)
                            Amber.instance.getHistoryDatabase(account.npub).dao().deleteHistory(it.application.key)

                            scope.launch(Dispatchers.Main) {
                                navController.navigate(Route.Applications.route) {
                                    popUpTo(0)
                                }
                            }

                            Amber.instance.checkForNewRelaysAndUpdateAllFilters()
                        }
                    }
                },
                text = stringResource(R.string.delete_application),
                textColor = Color.White,
            )
        }
    }
}
