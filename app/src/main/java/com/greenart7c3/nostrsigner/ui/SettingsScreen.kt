package com.greenart7c3.nostrsigner.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.service.PushNotificationUtils
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupDialog
import com.greenart7c3.nostrsigner.ui.actions.ConnectOrbotDialog
import com.greenart7c3.nostrsigner.ui.actions.EditDefaultRelaysDialog
import com.greenart7c3.nostrsigner.ui.actions.LogoutDialog
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.HyperlinkText
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.components.PostButton
import com.greenart7c3.nostrsigner.ui.components.TextSpinner
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.quartz.encoders.toNpub
import java.io.IOException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

fun Context.getLocaleListFromXml(): LocaleListCompat {
    val tagsList = mutableListOf<CharSequence>()
    try {
        val xpp: XmlPullParser = resources.getXml(R.xml.locales_config)
        while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xpp.eventType == XmlPullParser.START_TAG) {
                if (xpp.name == "locale") {
                    tagsList.add(xpp.getAttributeValue(0))
                }
            }
            xpp.next()
        }
    } catch (e: XmlPullParserException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return LocaleListCompat.forLanguageTags(tagsList.joinToString(","))
}

fun Context.getLangPreferenceDropdownEntries(): ImmutableMap<String, String> {
    val localeList = getLocaleListFromXml()
    val map = mutableMapOf<String, String>()

    for (a in 0 until localeList.size()) {
        localeList[a].let {
            map.put(
                it!!.getDisplayName(it).replaceFirstChar { char -> char.uppercase() },
                it.toLanguageTag(),
            )
        }
    }
    return map.toImmutableMap()
}

fun getLanguageIndex(
    languageEntries: ImmutableMap<String, String>,
    selectedLanguage: String?,
): Int {
    var languageIndex: Int
    languageIndex =
        if (selectedLanguage != null) {
            languageEntries.values.toTypedArray().indexOf(selectedLanguage)
        } else {
            languageEntries.values.toTypedArray().indexOf(Locale.current.toLanguageTag())
        }
    if (languageIndex == -1) {
        languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.language)
    }
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf("en")
    return languageIndex
}

@Composable
fun SettingsScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
) {
    var backupDialogOpen by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var checked by remember { mutableStateOf(account.useProxy) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    var conectOrbotDialogOpen by remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf(account.proxyPort.toString()) }
    val languageEntries = remember { context.getLangPreferenceDropdownEntries() }
    val languageList = remember { languageEntries.keys.map { TitleExplainer(it) }.toImmutableList() }
    val languageIndex = getLanguageIndex(languageEntries, account.language)
    var languageDialog by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }
    var relayDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val notificationItems =
        persistentListOf(
            TitleExplainer(stringResource(NotificationType.PUSH.resourceId)),
            TitleExplainer(stringResource(NotificationType.DIRECT.resourceId)),
        )
    var notificationTypeDialog by remember { mutableStateOf(false) }

    if (relayDialog) {
        EditDefaultRelaysDialog(
            onClose = {
                relayDialog = false
            },
            onPost = {
                if (it.isNotEmpty()) {
                    LocalPreferences.setDefaultRelays(context, it)
                }
                relayDialog = false
            },
        )
    }

    if (logDialog) {
        Dialog(
            onDismissRequest = {
                logDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CloseButton {
                            logDialog = false
                        }
                    }

                    val logsFlow = NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub()).applicationDao().getLogs()
                    val logs = logsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

                    LazyColumn(
                        Modifier.weight(1f),
                    ) {
                        items(logs.value.size) { index ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(6.dp),
                            ) {
                                Column(Modifier.padding(6.dp)) {
                                    val log = logs.value[index]
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("Date: ")
                                            }
                                            append(TimeUtils.convertLongToDateTime(log.time))
                                        },
                                    )
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("URL: ")
                                            }
                                            append(log.url)
                                        },
                                    )
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("Type: ")
                                            }
                                            append(log.type)
                                        },
                                    )
                                    Text(
                                        buildAnnotatedString {
                                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                append("Message: ")
                                            }
                                            append(log.message)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub()).applicationDao().clearLogs()
                                }
                            },
                        ) {
                            Text(text = stringResource(R.string.clear_logs))
                        }
                    }
                }
            }
        }
    }

    if (notificationTypeDialog) {
        var notificationItemsIndex by remember {
            mutableIntStateOf(LocalPreferences.getNotificationType(context).screenCode)
        }
        Dialog(
            onDismissRequest = {
                notificationTypeDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CloseButton {
                            notificationTypeDialog = false
                        }

                        PostButton(
                            isActive = true,
                        ) {
                            notificationTypeDialog = false
                            scope.launch(Dispatchers.IO) {
                                LocalPreferences.updateNotificationType(context, parseNotificationType(notificationItemsIndex))
                                PushNotificationUtils.hasInit = false
                                PushNotificationUtils.init(LocalPreferences.allSavedAccounts(context))
                                if (notificationItemsIndex == 0) {
                                    NotificationDataSource.stopSync()
                                    RelayPool.disconnect()
                                } else {
                                    NostrSigner.getInstance().checkForNewRelays()
                                    NotificationDataSource.start()
                                }
                                NostrSigner.getInstance().applicationContext.startForegroundService(
                                    Intent(
                                        NostrSigner.getInstance().applicationContext,
                                        ConnectivityService::class.java,
                                    ),
                                )
                            }
                        }
                    }

                    Column {
                        Box(
                            Modifier
                                .padding(8.dp),
                        ) {
                            SettingsRow(
                                R.string.notification_type,
                                R.string.select_the_type_of_notification_you_want_to_receive,
                                notificationItems,
                                notificationItemsIndex,
                            ) {
                                notificationItemsIndex = it
                            }
                        }

                        @Suppress("KotlinConstantConditions")
                        if (BuildConfig.FLAVOR == "free") {
                            Box(
                                Modifier
                                    .padding(8.dp),
                            ) {
                                PushNotificationSettingsRow()
                            }
                        }
                    }
                }
            }
        }
    }

    if (languageDialog) {
        Dialog(
            onDismissRequest = {
                languageDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CloseButton {
                            languageDialog = false
                        }
                    }

                    Column {
                        Box(
                            Modifier
                                .padding(8.dp),
                        ) {
                            SettingsRow(
                                R.string.language,
                                R.string.language_description,
                                languageList,
                                languageIndex,
                            ) {
                                account.language = languageEntries[languageList[it].title]
                                LocalPreferences.saveToEncryptedStorage(context, account)
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(account.language),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                logoutDialog = false
                accountStateViewModel.logOff(account.keyPair.pubKey.toNpub())
            },
        )
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState()),
    ) {
        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.backup_keys),
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    backupDialogOpen = true
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.language),
                icon = Icons.Default.Language,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    languageDialog = true
                },
            )
        }

        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR != "offline") {
            Box(
                Modifier
                    .padding(8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.notification_type),
                    icon = Icons.Default.Notifications,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        notificationTypeDialog = true
                    },
                )
            }

            Box(
                Modifier
                    .padding(8.dp),
            ) {
                IconRow(
                    title = if (checked) {
                        stringResource(R.string.disconnect_from_your_orbot_setup)
                    } else {
                        stringResource(R.string.connect_via_tor_short)
                    },
                    icon = R.drawable.ic_tor,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onLongClick = {
                        conectOrbotDialogOpen = true
                    },
                    onClick = {
                        if (checked) {
                            disconnectTorDialog = true
                        } else {
                            conectOrbotDialogOpen = true
                        }
                    },
                )
            }
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.default_relays),
                icon = Icons.Default.Hub,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    relayDialog = true
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = if (account.allowNewConnections) stringResource(R.string.disable_listening_for_new_connections) else stringResource(R.string.enable_listening_for_new_connections),
                icon = Icons.Default.SurroundSound,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    account.allowNewConnections = !account.allowNewConnections
                    LocalPreferences.saveToEncryptedStorage(context, account)
                    accountStateViewModel.switchUser(account.keyPair.pubKey.toNpub(), Route.Settings.route)
                },
            )
        }

        Box(
            Modifier
                .padding(8.dp),
        ) {
            IconRow(
                title = stringResource(R.string.logs),
                icon = Icons.Default.FilterList,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    logDialog = true
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HyperlinkText(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            fullText = "v${BuildConfig.VERSION_NAME}-${BuildConfig.FLAVOR}\n\n${context.getString(
                R.string.support_development,
            )}\n\n${context.getString(R.string.source_code)}",
            hyperLinks = mutableMapOf(
                stringResource(R.string.source_code) to stringResource(R.string.amber_github_uri),
                stringResource(R.string.support_development) to stringResource(R.string.support_development_uri),
            ),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.inverseSurface,
            ),
            linkTextColor = MaterialTheme.colorScheme.primary,
            linkTextDecoration = TextDecoration.Underline,
            fontSize = 18.sp,
        )
    }

    if (conectOrbotDialogOpen) {
        ConnectOrbotDialog(
            onClose = { conectOrbotDialogOpen = false },
            onPost = {
                conectOrbotDialogOpen = false
                disconnectTorDialog = false
                checked = true
                LocalPreferences.updateProxy(context, true, proxyPort.value.toInt())
            },
            onError = {
                scope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.could_not_connect_to_tor),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            proxyPort,
        )
    }

    if (disconnectTorDialog) {
        AlertDialog(
            title = { Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_title)) },
            text = { Text(text = stringResource(R.string.do_you_really_want_to_disable_tor_text)) },
            onDismissRequest = { disconnectTorDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        disconnectTorDialog = false
                        checked = false
                        LocalPreferences.updateProxy(context, false, proxyPort.value.toInt())
                    },
                ) {
                    Text(text = stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { disconnectTorDialog = false },
                ) {
                    Text(text = stringResource(R.string.no))
                }
            },
        )
    }

    if (backupDialogOpen) {
        AccountBackupDialog(account, onClose = { backupDialogOpen = false })
    }
}

@Composable
fun SettingsRow(
    name: Int,
    description: Int,
    selectedItems: ImmutableList<TitleExplainer>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.weight(2.0f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextSpinner(
            label = "",
            placeholder = selectedItems[selectedIndex].title,
            options = selectedItems,
            onSelect = onSelect,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                .weight(1f),
        )
    }
}

enum class NotificationType(val screenCode: Int, val resourceId: Int) {
    PUSH(0, R.string.push_notifications),
    DIRECT(1, R.string.direct_connection),
}

fun parseNotificationType(screenCode: Int): NotificationType {
    return when (screenCode) {
        NotificationType.PUSH.screenCode -> NotificationType.PUSH
        NotificationType.DIRECT.screenCode -> NotificationType.DIRECT
        else -> {
            NotificationType.PUSH
        }
    }
}
