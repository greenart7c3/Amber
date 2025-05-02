package com.greenart7c3.nostrsigner.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.ui.actions.LogoutDialog
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.components.TextSpinner
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.TimeUtils.ONE_WEEK
import java.text.DecimalFormat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    var logoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var checked by remember { mutableStateOf(Amber.instance.settings.useProxy) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    val proxyPort = remember { mutableStateOf(Amber.instance.settings.proxyPort.toString()) }
    var allowNewConnections by remember { mutableStateOf(account.allowNewConnections) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var sizeInMBFormatted by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        launch(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("amber_db_${account.npub}")
            val df = DecimalFormat("#.###")
            sizeInMBFormatted = df.format(dbFile.length() / (1024.0 * 1024.0))
            isLoading = false
        }
    }

    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                logoutDialog = false
                accountStateViewModel.logOff(account.npub)
            },
        )
    }

    if (isLoading) {
        CenterCircularProgressIndicator(modifier, status)
    } else {
        Column(
            modifier,
        ) {
            Box(
                Modifier
                    .padding(bottom = 8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.security),
                    icon = Icons.Default.Security,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.Security.route)
                    },
                )
            }

            Box(
                Modifier
                    .padding(vertical = 8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.backup_keys),
                    icon = Icons.Default.Key,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.AccountBackup.route)
                    },
                )
            }

            Box(
                Modifier
                    .padding(vertical = 8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.language),
                    icon = Icons.Default.Language,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.Language.route)
                    },
                )
            }

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR != "offline") {
                Box(
                    Modifier
                        .padding(vertical = 8.dp),
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
                            navController.navigate(Route.TorSettings.route)
                        },
                        onClick = {
                            if (checked) {
                                disconnectTorDialog = true
                            } else {
                                navController.navigate(Route.TorSettings.route)
                            }
                        },
                    )
                }

                Box(
                    Modifier
                        .padding(vertical = 8.dp),
                ) {
                    IconRow(
                        title = stringResource(R.string.relays),
                        icon = ImageVector.vectorResource(R.drawable.relays),
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = {
                            navController.navigate(Route.RelaysScreen.route)
                        },
                    )
                }

                Box(
                    Modifier
                        .padding(vertical = 8.dp),
                ) {
                    IconRow(
                        title = if (!allowNewConnections) stringResource(R.string.enable_listening_for_new_connections) else stringResource(R.string.disable_listening_for_new_connections),
                        icon = if (allowNewConnections) Icons.Default.Stop else Icons.Default.PlayArrow,
                        tint = MaterialTheme.colorScheme.onBackground,
                        onClick = {
                            allowNewConnections = !allowNewConnections
                            account.allowNewConnections = allowNewConnections
                            scope.launch(Dispatchers.IO) {
                                LocalPreferences.saveToEncryptedStorage(context, account)
                            }
                        },
                        onLongClick = {
                            Toast.makeText(
                                context,
                                if (allowNewConnections) context.getString(R.string.disable_listening_for_new_connections) else context.getString(R.string.enable_listening_for_new_connections),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            }

            Box(
                Modifier
                    .padding(vertical = 8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.logs),
                    icon = Icons.Default.FilterList,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.Logs.route)
                    },
                )
            }

            Box(
                Modifier
                    .padding(vertical = 8.dp),
            ) {
                IconRow(
                    title = stringResource(R.string.sign_policy),
                    icon = Icons.Default.Draw,
                    tint = MaterialTheme.colorScheme.onBackground,
                    onClick = {
                        navController.navigate(Route.SignPolicy.route)
                    },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val primaryColor = MaterialTheme.colorScheme.primary

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.database_size_mb, sizeInMBFormatted), color = Color.Gray)
                AmberButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.clear_logs_and_activity),
                    onClick = {
                        Amber.instance.applicationIOScope.launch {
                            NotificationDataSource.stop()
                            isLoading = true
                            LocalPreferences.allSavedAccounts(Amber.instance).forEach {
                                Amber.instance.getDatabase(it.npub).let { database ->
                                    try {
                                        status = context.getString(R.string.deleting_old_log_entries_from, it.npub)
                                        val oneWeek = System.currentTimeMillis() - ONE_WEEK
                                        val oneWeekAgo = TimeUtils.oneWeekAgo()
                                        val countHistory = database.applicationDao().countOldHistory(oneWeekAgo)
                                        if (countHistory > 0) {
                                            status = context.getString(R.string.deleting_old_history_entries, countHistory)
                                            var logs = database.applicationDao().getOldHistory(oneWeekAgo)
                                            var count = 0
                                            while (logs.isNotEmpty()) {
                                                count++
                                                status = context.getString(R.string.deleting_old_history_entries_2, 100 * count, countHistory)
                                                logs.forEach { history ->
                                                    database.applicationDao().deleteHistory(history)
                                                }
                                                logs = database.applicationDao().getOldHistory(oneWeekAgo)
                                            }
                                        }

                                        val countNotification = database.applicationDao().countOldNotification(oneWeekAgo)
                                        if (countNotification > 0) {
                                            status = context.getString(R.string.deleting_old_notification_entries, countNotification)
                                            var logs = database.applicationDao().getOldNotification(oneWeekAgo)
                                            var count = 0
                                            while (logs.isNotEmpty()) {
                                                count++
                                                status = context.getString(R.string.deleting_old_notification_entries_2, 100 * count, countNotification)
                                                logs.forEach { history ->
                                                    database.applicationDao().deleteNotification(history)
                                                }
                                                logs = database.applicationDao().getOldNotification(oneWeekAgo)
                                            }
                                        }

                                        val countLog = database.applicationDao().countOldLog(oneWeek)
                                        if (countLog > 0) {
                                            status = context.getString(R.string.deleting_old_log_entries, countLog)
                                            var logs = database.applicationDao().getOldLog(oneWeek)
                                            var count = 0
                                            while (logs.isNotEmpty()) {
                                                count++
                                                status = context.getString(R.string.deleting_old_log_entries_2, 100 * count, countLog)
                                                logs.forEach { history ->
                                                    database.applicationDao().deleteLog(history)
                                                }
                                                logs = database.applicationDao().getOldLog(oneWeek)
                                            }
                                        }
                                        val dbFile = context.getDatabasePath("amber_db_${account.npub}")
                                        val df = DecimalFormat("#.###")
                                        sizeInMBFormatted = df.format(dbFile.length() / (1024.0 * 1024.0))
                                        status = ""
                                        isLoading = false
                                        NotificationDataSource.start()
                                    } catch (e: Exception) {
                                        isLoading = false
                                        if (e is CancellationException) throw e
                                        Log.e("NostrSigner", "Error deleting old log entries", e)
                                        val dbFile = context.getDatabasePath("amber_db_${account.npub}")
                                        val df = DecimalFormat("#.###")
                                        sizeInMBFormatted = df.format(dbFile.length() / (1024.0 * 1024.0))
                                        status = ""
                                        NotificationDataSource.start()
                                    }
                                }
                            }
                        }
                    },
                )
            }

            Text(
                buildAnnotatedString {
                    withStyle(
                        style = ParagraphStyle(
                            textAlign = TextAlign.Center,
                        ),
                    ) {
                        append("v${BuildConfig.VERSION_NAME}-${BuildConfig.FLAVOR}\n\n")
                        withLink(
                            LinkAnnotation.Url(
                                context.getString(R.string.amber_github_uri),
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = primaryColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) {
                            append("${context.getString(R.string.source_code)}\n\n")
                        }
                        withLink(
                            LinkAnnotation.Url(
                                context.getString(R.string.support_development_uri),
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = primaryColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) {
                            append(context.getString(R.string.support_development))
                        }
                    }
                    toAnnotatedString()
                },
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            )
        }
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
                        scope.launch(Dispatchers.IO) {
                            LocalPreferences.updateProxy(context, false, proxyPort.value.toInt())
                            NotificationDataSource.stop()
                            Amber.instance.checkForNewRelays()
                            NotificationDataSource.start()
                        }
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

enum class BiometricsTimeType(val screenCode: Int, val resourceId: Int) {
    EVERY_TIME(0, R.string.every_time),
    ONE_MINUTE(1, R.string.one_minute),
    FIVE_MINUTES(2, R.string.five_minutes),
    TEN_MINUTES(3, R.string.ten_minutes),
}

fun parseBiometricsTimeType(screenCode: Int): BiometricsTimeType {
    return when (screenCode) {
        BiometricsTimeType.ONE_MINUTE.screenCode -> BiometricsTimeType.ONE_MINUTE
        BiometricsTimeType.FIVE_MINUTES.screenCode -> BiometricsTimeType.FIVE_MINUTES
        BiometricsTimeType.TEN_MINUTES.screenCode -> BiometricsTimeType.TEN_MINUTES
        else -> {
            BiometricsTimeType.EVERY_TIME
        }
    }
}
