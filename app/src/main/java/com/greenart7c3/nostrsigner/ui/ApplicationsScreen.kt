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
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.LocalPreferences
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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun ApplicationsScreen(
    modifier: Modifier,
    account: Account,
    navControllerWrapper: NavHostControllerWrapper,
) {
    // remember: without it every recomposition (killSwitch / backup-warning
    // state flips) builds a new Pager + cold flow and re-runs the initial
    // load from scratch. Keyed on the account and on the sort toggle so
    // switching either reloads.
    var sortAlphabetically by remember(account.hexKey) {
        mutableStateOf(Amber.instance.settings.sortApplicationsAlphabetically)
    }

    val pager =
        remember(account.hexKey, sortAlphabetically) {
            Pager(
                PagingConfig(
                    pageSize = 20,
                    enablePlaceholders = false,
                ),
            ) {
                // Projection excludes the envelope-encrypted secret/localKey
                // columns: the list renders neither, and each Keystore decrypt
                // is a binder + TEE round trip (2 x rows per page).
                val dao = Amber.instance.getDatabase(account.npub).dao()
                if (sortAlphabetically) {
                    dao.getApplicationListItemsPagingByName(account.hexKey)
                } else {
                    dao.getApplicationListItemsPaging(account.hexKey)
                }
            }
        }

    val lazyPagingItems = pager.flow.collectAsLazyPagingItems()

    var hasAccountsWithoutBackup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            hasAccountsWithoutBackup = LocalPreferences.allAccounts(Amber.instance).any { !it.didBackup }
        }
    }

    val killSwitch = Amber.instance.settings.killSwitch.collectAsStateWithLifecycle()

    LazyColumn(
        modifier,
    ) {
        if (killSwitch.value) {
            item {
                AmberWarningCard(
                    message = stringResource(Res.string.kill_switch_message),
                    buttonText = stringResource(Res.string.disable_kill_switch),
                    onClick = {
                        val killSwitchIntent = Intent(Amber.instance, KillSwitchReceiver::class.java)
                        Amber.instance.sendBroadcast(killSwitchIntent)
                        LocalPreferences.switchToAccount(Amber.instance, account.npub)
                    },
                )
            }
        }

        if (hasAccountsWithoutBackup) {
            if (killSwitch.value) {
                item {
                    Spacer(Modifier.height(4.dp))
                }
            }
            item {
                AmberWarningCard(
                    message = stringResource(Res.string.make_backup_message),
                    buttonText = stringResource(Res.string.backup),
                    onClick = {
                        navControllerWrapper.navController.navigate(Route.AccountBackup.route)
                    },
                )
            }
        }

        if (lazyPagingItems.itemCount == 0) {
            item {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(Res.string.congratulations_your_new_account_is_ready),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
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
                                if (Amber.instance.isZapstoreInstalled()) "zapstore://" else stringResource(Res.string.zapstore_website),
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
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AmberButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            navControllerWrapper.navController.navigate(Route.Activities.route)
                        },
                        text = stringResource(Res.string.activity),
                    )
                    IconButton(
                        onClick = {
                            val newValue = !sortAlphabetically
                            sortAlphabetically = newValue
                            Amber.instance.settings =
                                Amber.instance.settings.copy(sortApplicationsAlphabetically = newValue)
                            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                        },
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

            items(lazyPagingItems.itemCount) { index ->
                val applicationWithHistory = lazyPagingItems[index]
                applicationWithHistory?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .clickable {
                                // Only navigate when we are still on the Applications screen. A second
                                // tap (or a tap landing while the NavHost graph is being recreated) can
                                // otherwise fire navigate() against a graph that no longer contains the
                                // Permission destination, crashing with IllegalArgumentException.
                                if (navControllerWrapper.navController.currentDestination?.route == Route.Applications.route) {
                                    runCatching {
                                        navControllerWrapper.navController.navigate("Permission/${applicationWithHistory.key}")
                                    }.onFailure {
                                        AmberLog.e("ApplicationsScreen", "Failed to open permissions for ${applicationWithHistory.key}", it)
                                    }
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppAvatar(
                            modifier = Modifier.padding(end = 12.dp),
                            iconUrl = applicationWithHistory.icon,
                            packageName = applicationWithHistory.key,
                            name = applicationWithHistory.name.ifBlank { applicationWithHistory.key.toShortenHex() },
                            size = 40.dp,
                        )
                        Column(
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                modifier = Modifier.padding(top = 16.dp),
                                text = applicationWithHistory.name.ifBlank { applicationWithHistory.key.toShortenHex() },
                                fontSize = 24.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (applicationWithHistory.relays.isNotEmpty()) {
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = applicationWithHistory.relays.joinToString { it.displayUrl() },
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
                                    text = applicationWithHistory.key.toShortenHex(),
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                                    text = if (applicationWithHistory.lastUsed == 0L) stringResource(Res.string.never) else TimeUtils.formatDateTime(LocalContext.current, applicationWithHistory.lastUsed * 1000),
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
            }
        }
    }
}

fun Context.isZapstoreInstalled(): Boolean = try {
    packageManager.getPackageInfo("dev.zapstore.app", 0)
    true
} catch (_: Exception) {
    false
}
