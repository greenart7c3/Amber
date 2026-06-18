package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.ui.actions.LogoutDialog
import com.greenart7c3.nostrsigner.ui.components.AmberButton
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
    onNav: (String) -> Unit,
) {
    var logoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var torMode by remember { mutableStateOf(Amber.instance.settings.torMode) }
    var disconnectTorDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var sizeInMBFormatted by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        launch(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("amber_db_${account.npub}")
            val logFile = context.getDatabasePath("log_db_${account.npub}")
            val historyFile = context.getDatabasePath("history_db_${account.npub}")
            val df = DecimalFormat("#.###")
            sizeInMBFormatted = df.format((dbFile.length() + logFile.length() + historyFile.length()) / (1024.0 * 1024.0))
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
        val isOffline = BuildFlavorChecker.isOfflineFlavor()

        Column(
            modifier,
        ) {
            SettingsSection(title = stringResource(R.string.settings_section_account)) {
                SettingsItem(
                    title = stringResource(R.string.security),
                    subtitle = stringResource(R.string.security_subtitle),
                    painter = rememberVectorPainter(Icons.Default.Security),
                    onClick = { onNav(Route.Security.route) },
                )
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.backup_keys),
                    subtitle = stringResource(R.string.backup_keys_subtitle),
                    painter = rememberVectorPainter(Icons.Default.Key),
                    onClick = { onNav(Route.AccountBackup.route) },
                )
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.sign_policy),
                    subtitle = stringResource(R.string.sign_policy_subtitle),
                    painter = rememberVectorPainter(Icons.Default.Draw),
                    onClick = { onNav(Route.SignPolicy.route) },
                )
            }

            if (!isOffline) {
                SettingsSection(title = stringResource(R.string.settings_section_network)) {
                    SettingsItem(
                        title = when (torMode) {
                            TorMode.BUILTIN -> stringResource(R.string.disconnect_builtin_tor)
                            TorMode.ORBOT -> stringResource(R.string.disconnect_from_your_orbot_setup)
                            TorMode.DISABLED -> stringResource(R.string.connect_via_tor_short)
                        },
                        subtitle = stringResource(R.string.tor_subtitle),
                        painter = painterResource(R.drawable.ic_tor),
                        onLongClick = { onNav(Route.TorSettings.route) },
                        onClick = {
                            if (torMode != TorMode.DISABLED) {
                                disconnectTorDialog = true
                            } else {
                                onNav(Route.TorSettings.route)
                            }
                        },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.relays),
                        subtitle = stringResource(R.string.relays_subtitle),
                        painter = painterResource(R.drawable.relays),
                        onClick = { onNav(Route.RelaysScreen.route) },
                    )
                }

                SettingsSection(title = stringResource(R.string.settings_section_service)) {
                    SettingsItem(
                        title = stringResource(R.string.service_settings),
                        subtitle = stringResource(R.string.service_settings_subtitle),
                        painter = rememberVectorPainter(Icons.Default.PowerSettingsNew),
                        onClick = { onNav(Route.ServiceSettings.route) },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.applications_backup),
                        subtitle = stringResource(R.string.applications_backup_subtitle),
                        painter = rememberVectorPainter(Icons.Default.CloudUpload),
                        onClick = { onNav(Route.ApplicationsBackup.route) },
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.settings_section_general)) {
                SettingsItem(
                    title = stringResource(R.string.language),
                    subtitle = stringResource(R.string.language_subtitle),
                    painter = rememberVectorPainter(Icons.Default.Language),
                    onClick = { onNav(Route.Language.route) },
                )
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.auth_whitelist),
                    subtitle = stringResource(R.string.auth_whitelist_subtitle),
                    painter = rememberVectorPainter(Icons.Default.VerifiedUser),
                    onClick = { onNav(Route.AuthWhitelist.route) },
                )
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.logs),
                    subtitle = stringResource(R.string.logs_subtitle),
                    painter = rememberVectorPainter(Icons.Default.Article),
                    onClick = { onNav(Route.Logs.route) },
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_help)) {
                SettingsItem(
                    title = stringResource(R.string.give_us_feedback),
                    subtitle = stringResource(R.string.give_us_feedback_subtitle),
                    painter = rememberVectorPainter(Icons.Default.Feedback),
                    onClick = { onNav(Route.Feedback.route) },
                )

                if (!isOffline && !BuildConfig.IS_FDROID_BUILD) {
                    val updater = Amber.instance.zapstoreUpdater
                    if (updater != null) {
                        val latestRelease by updater.latestRelease.collectAsStateWithLifecycle()
                        val updateAvailable = latestRelease != null

                        SettingsDivider()
                        SettingsItem(
                            title = if (updateAvailable) {
                                stringResource(R.string.update_available, latestRelease!!.version)
                            } else {
                                stringResource(R.string.update_settings)
                            },
                            subtitle = stringResource(R.string.update_settings_subtitle),
                            painter = rememberVectorPainter(Icons.Default.SystemUpdate),
                            iconTint = if (updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            titleColor = if (updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            onClick = { onNav(Route.UpdateSettings.route) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val primaryColor = MaterialTheme.colorScheme.primary

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.database_size_mb, sizeInMBFormatted), color = Color.Gray)
                AmberButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(R.string.clear_logs_and_activity),
                    onClick = {
                        Amber.instance.applicationIOScope.launch {
                            isLoading = true
                            LocalPreferences.allSavedAccounts(Amber.instance).forEach {
                                try {
                                    status = context.getString(R.string.deleting_old_log_entries_from, it.npub)
                                    val oneWeek = System.currentTimeMillis() - (ONE_WEEK * 1000L)
                                    val oneWeekAgo = TimeUtils.oneWeekAgo()
                                    val historyDatabase = Amber.instance.getHistoryDatabase(it.npub)
                                    val deletedHistory = historyDatabase.dao().deleteOldHistory(oneWeekAgo)
                                    if (deletedHistory > 0) {
                                        AmberLog.d(Amber.TAG, "Deleted $deletedHistory old history entries")
                                    }

                                    val logDatabase = Amber.instance.getLogDatabase(it.npub)
                                    val deletedLogs = logDatabase.dao().deleteOldLog(oneWeek)
                                    if (deletedLogs > 0) {
                                        AmberLog.d(Amber.TAG, "Deleted $deletedLogs old log entries")
                                    }

                                    val dbFile = context.getDatabasePath("amber_db_${account.npub}")
                                    val logFile = context.getDatabasePath("log_db_${account.npub}")
                                    val historyFile = context.getDatabasePath("history_db_${account.npub}")
                                    val df = DecimalFormat("#.###")
                                    sizeInMBFormatted = df.format((dbFile.length() + logFile.length() + historyFile.length()) / (1024.0 * 1024.0))

                                    status = ""
                                    isLoading = false
                                } catch (e: Exception) {
                                    isLoading = false
                                    if (e is CancellationException) throw e
                                    AmberLog.e(Amber.TAG, "Error deleting old log entries", e)
                                    val dbFile = context.getDatabasePath("amber_db_${account.npub}")
                                    val logFile = context.getDatabasePath("log_db_${account.npub}")
                                    val historyFile = context.getDatabasePath("history_db_${account.npub}")
                                    val df = DecimalFormat("#.###")
                                    sizeInMBFormatted = df.format((dbFile.length() + logFile.length() + historyFile.length()) / (1024.0 * 1024.0))
                                    status = ""
                                }
                            }

                            Amber.instance.checkForNewRelaysAndUpdateAllFilters()
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
                        torMode = TorMode.DISABLED
                        scope.launch(Dispatchers.IO) {
                            if (Amber.instance.settings.torMode == TorMode.BUILTIN) {
                                com.greenart7c3.nostrsigner.service.TorManager.stop()
                            }
                            LocalPreferences.updateTorMode(context, TorMode.DISABLED)
                            Amber.instance.checkForNewRelaysAndUpdateAllFilters()
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
    description: Int?,
    selectedItems: ImmutableList<TitleExplainer>,
    selectedIndex: Int,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
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
                maxLines = maxLines,
                overflow = overflow,
            )
            description?.let {
                Text(
                    text = stringResource(description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsItem(
    title: String,
    painter: Painter,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onBackground,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconTint,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
        )
    }
}

enum class RememberType(val screenCode: Int, val resourceId: Int, val shortResourceId: Int) {
    NEVER(0, R.string.never, R.string.never),
    ONE_MINUTE(1, R.string.one_minute, R.string.one_minute_short),
    FIVE_MINUTES(2, R.string.five_minutes, R.string.five_minutes_short),
    TEN_MINUTES(3, R.string.ten_minutes, R.string.ten_minutes_short),
    ALWAYS(4, R.string.always, R.string.always),
    ONE_HOUR(5, R.string.one_hour, R.string.one_hour_short),
    ONE_DAY(6, R.string.one_day, R.string.one_day_short),
    ONE_WEEK(7, R.string.one_week, R.string.one_week_short),
}

val rememberTypeDisplayOrder: List<RememberType> = listOf(
    RememberType.NEVER,
    RememberType.FIVE_MINUTES,
    RememberType.TEN_MINUTES,
    RememberType.ONE_HOUR,
    RememberType.ONE_DAY,
    RememberType.ONE_WEEK,
    RememberType.ALWAYS,
)

fun parseRememberType(screenCode: Int): RememberType = when (screenCode) {
    RememberType.NEVER.screenCode -> RememberType.NEVER
    RememberType.ONE_MINUTE.screenCode -> RememberType.ONE_MINUTE
    RememberType.FIVE_MINUTES.screenCode -> RememberType.FIVE_MINUTES
    RememberType.TEN_MINUTES.screenCode -> RememberType.TEN_MINUTES
    RememberType.ONE_HOUR.screenCode -> RememberType.ONE_HOUR
    RememberType.ONE_DAY.screenCode -> RememberType.ONE_DAY
    RememberType.ONE_WEEK.screenCode -> RememberType.ONE_WEEK
    else -> RememberType.ALWAYS
}

enum class DeleteAfterType(val screenCode: Int, val resourceId: Int) {
    NEVER(0, R.string.never),
    FIVE_MINUTES(1, R.string.five_minutes),
    TEN_MINUTES(2, R.string.ten_minutes),
    ONE_HOUR(3, R.string.one_hour),
    ONE_DAY(4, R.string.one_day),
    ONE_WEEK(5, R.string.one_week),
}

fun parseDeleteAfterType(screenCode: Int): DeleteAfterType = when (screenCode) {
    DeleteAfterType.FIVE_MINUTES.screenCode -> DeleteAfterType.FIVE_MINUTES
    DeleteAfterType.TEN_MINUTES.screenCode -> DeleteAfterType.TEN_MINUTES
    DeleteAfterType.ONE_HOUR.screenCode -> DeleteAfterType.ONE_HOUR
    DeleteAfterType.ONE_DAY.screenCode -> DeleteAfterType.ONE_DAY
    DeleteAfterType.ONE_WEEK.screenCode -> DeleteAfterType.ONE_WEEK
    else -> DeleteAfterType.NEVER
}

fun deleteAfterToSeconds(deleteAfterType: DeleteAfterType): Long {
    val now = System.currentTimeMillis() / 1000
    return when (deleteAfterType) {
        DeleteAfterType.FIVE_MINUTES -> now + 300
        DeleteAfterType.TEN_MINUTES -> now + 600
        DeleteAfterType.ONE_HOUR -> now + 3600
        DeleteAfterType.ONE_DAY -> now + 86400
        DeleteAfterType.ONE_WEEK -> now + 604800
        else -> 0
    }
}

enum class BiometricsTimeType(val screenCode: Int, val resourceId: Int) {
    EVERY_TIME(0, R.string.every_time),
    ONE_MINUTE(1, R.string.one_minute),
    FIVE_MINUTES(2, R.string.five_minutes),
    TEN_MINUTES(3, R.string.ten_minutes),
}

fun parseBiometricsTimeType(screenCode: Int): BiometricsTimeType = when (screenCode) {
    BiometricsTimeType.ONE_MINUTE.screenCode -> BiometricsTimeType.ONE_MINUTE
    BiometricsTimeType.FIVE_MINUTES.screenCode -> BiometricsTimeType.FIVE_MINUTES
    BiometricsTimeType.TEN_MINUTES.screenCode -> BiometricsTimeType.TEN_MINUTES
    else -> {
        BiometricsTimeType.EVERY_TIME
    }
}
