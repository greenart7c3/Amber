package com.greenart7c3.nostrsigner.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.activity
import com.greenart7c3.nostrsigner.commonsui.backup
import com.greenart7c3.nostrsigner.commonsui.congratulations_your_new_account_is_ready
import com.greenart7c3.nostrsigner.commonsui.disable_kill_switch
import com.greenart7c3.nostrsigner.commonsui.kill_switch_message
import com.greenart7c3.nostrsigner.commonsui.make_backup_message
import com.greenart7c3.nostrsigner.commonsui.never
import com.greenart7c3.nostrsigner.commonsui.nostr_app
import com.greenart7c3.nostrsigner.commonsui.sort_applications_alphabetically
import com.greenart7c3.nostrsigner.commonsui.sort_applications_most_recent
import com.greenart7c3.nostrsigner.commonsui.your_account_is_ready_to_use
import com.greenart7c3.nostrsigner.commonsui.zapstore
import com.greenart7c3.nostrsigner.commonsui.zapstore_website
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.KillSwitchReceiver
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.AmberWarningCard
import com.greenart7c3.nostrsigner.ui.components.AppAvatar
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ApplicationsScreen(
    account: Account,
    navControllerWrapper: NavHostControllerWrapper,
    modifier: Modifier = Modifier,
    viewModel: ApplicationsViewModel = viewModel(factory = ApplicationsViewModel.factory(LocalAccountRepository.current)),
) {
    val context = LocalContext.current
    val settingsStore = LocalSettingsStore.current
    val appContext = LocalContext.current.applicationContext
    var sortAlphabetically by remember(account.hexKey) {
        mutableStateOf(settingsStore.settings.sortApplicationsAlphabetically)
    }

    @Suppress("NoComments")
    val applications = remember(account.hexKey, sortAlphabetically) {
        // remember: without it every recomposition (killSwitch / backup-warning
        // state flips) builds a new Pager + cold flow and re-runs the initial
        // load from scratch. Keyed on the account and on the sort toggle so
        // switching either reloads.
        viewModel.applications(account.npub, account.hexKey, sortAlphabetically)
    }
    val lazyPagingItems = applications.collectAsLazyPagingItems()

    val hasAccountsWithoutBackup = rememberHasAccountsWithoutBackup(appContext)

    val killSwitch = settingsStore.settings.killSwitch.collectAsStateWithLifecycle()

    LazyColumn(modifier) {
        if (killSwitch.value) {
            item { KillSwitchWarning(onClick = { disableKillSwitch(appContext, account.npub) }) }
        }

        if (hasAccountsWithoutBackup) {
            if (killSwitch.value) {
                item { Spacer(Modifier.height(4.dp)) }
            }
            item { BackupWarning(onClick = { navControllerWrapper.navController.navigate(Route.AccountBackup.route) }) }
        }

        if (lazyPagingItems.itemCount == 0) {
            item { NewAccountTitle() }
            item { NewAccountLinks(isZapstoreInstalled = appContext.isZapstoreInstalled()) }
        } else {
            item {
                ApplicationsHeader(
                    sortAlphabetically = sortAlphabetically,
                    onOpenActivity = { navControllerWrapper.navController.navigate(Route.Activities.route) },
                    onToggleSort = {
                        val newValue = !sortAlphabetically
                        sortAlphabetically = newValue
                        settingsStore.settings =
                            settingsStore.settings.copy(sortApplicationsAlphabetically = newValue)
                        LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                    },
                )
            }

            items(lazyPagingItems.itemCount) { index ->
                lazyPagingItems[index]?.let { applicationWithHistory ->
                    ApplicationRow(
                        key = applicationWithHistory.key,
                        name = applicationWithHistory.name,
                        icon = applicationWithHistory.icon,
                        relays = applicationWithHistory.relays,
                        lastUsed = applicationWithHistory.lastUsed,
                        onClick = { openPermissions(navControllerWrapper.navController, applicationWithHistory.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberHasAccountsWithoutBackup(appContext: Context): Boolean {
    var hasAccountsWithoutBackup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            hasAccountsWithoutBackup = AccountStorage.all(appContext).any { !it.didBackup }
        }
    }
    return hasAccountsWithoutBackup
}

private fun disableKillSwitch(appContext: Context, npub: String) {
    val killSwitchIntent = Intent(appContext, KillSwitchReceiver::class.java)
    appContext.sendBroadcast(killSwitchIntent)
    SavedAccounts.switchTo(appContext, npub)
}

@Suppress("NoComments")
private fun openPermissions(navController: NavHostController, key: String) {
    // Only navigate when we are still on the Applications screen. A second
    // tap (or a tap landing while the NavHost graph is being recreated) can
    // otherwise fire navigate() against a graph that no longer contains the
    // Permission destination, crashing with IllegalArgumentException.
    if (navController.currentDestination?.route == Route.Applications.route) {
        runCatching {
            navController.navigate("Permission/$key")
        }.onFailure {
            AmberLog.e("ApplicationsScreen", "Failed to open permissions for $key", it)
        }
    }
}

@Composable
private fun KillSwitchWarning(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AmberWarningCard(
        message = stringResource(Res.string.kill_switch_message),
        buttonText = stringResource(Res.string.disable_kill_switch),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun BackupWarning(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AmberWarningCard(
        message = stringResource(Res.string.make_backup_message),
        buttonText = stringResource(Res.string.backup),
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun NewAccountTitle(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(top = 8.dp),
        text = stringResource(Res.string.congratulations_your_new_account_is_ready),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun NewAccountLinks(isZapstoreInstalled: Boolean, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(stringResource(Res.string.your_account_is_ready_to_use))
            withLink(
                LinkAnnotation.Url(
                    "https://" + stringResource(Res.string.nostr_app),
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(" " + stringResource(Res.string.nostr_app))
            }
            append(" or ")
            withLink(
                LinkAnnotation.Url(
                    if (isZapstoreInstalled) "zapstore://" else stringResource(Res.string.zapstore_website),
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                ),
            ) {
                append(stringResource(Res.string.zapstore))
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ApplicationsHeader(
    sortAlphabetically: Boolean,
    onOpenActivity: () -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AmberButton(
            modifier = Modifier.weight(1f),
            onClick = onOpenActivity,
            text = stringResource(Res.string.activity),
        )
        IconButton(
            onClick = onToggleSort,
        ) {
            Icon(
                imageVector = Icons.Default.SortByAlpha,
                contentDescription = stringResource(
                    if (sortAlphabetically) {
                        Res.string.sort_applications_most_recent
                    } else {
                        Res.string.sort_applications_alphabetically
                    },
                ),
                tint = if (sortAlphabetically) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ApplicationRow(
    key: String,
    name: String,
    icon: String,
    relays: List<NormalizedRelayUrl>,
    lastUsed: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppAvatar(
            modifier = Modifier.padding(end = 12.dp),
            iconUrl = icon,
            packageName = key,
            name = name.ifBlank { key.toShortenHex() },
            size = 40.dp,
        )
        Column(
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                modifier = Modifier.padding(top = 16.dp),
                text = name.ifBlank { key.toShortenHex() },
                fontSize = 24.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (relays.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = relays.joinToString { it.displayUrl() },
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    text = key.toShortenHex(),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    text = if (lastUsed == 0L) stringResource(Res.string.never) else TimeUtils.formatDateTime(LocalContext.current, lastUsed * 1000),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.weight(1f))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

fun Context.isZapstoreInstalled(): Boolean = try {
    packageManager.getPackageInfo("dev.zapstore.app", 0)
    true
} catch (_: Exception) {
    false
}
