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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
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
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.KillSwitchReceiver
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.AmberWarningCard
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ApplicationsScreen(
    modifier: Modifier,
    account: Account,
    navController: NavController,
) {
    val applications = Amber.instance.getDatabase(account.npub).applicationDao().getAllFlow(account.hexKey).collectAsStateWithLifecycle(emptyList())
    var hasAccountsWithoutBackup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            hasAccountsWithoutBackup = LocalPreferences.allAccounts(Amber.instance).any { !it.didBackup }
        }
    }

    Column(
        modifier,
    ) {
        val killSwitch = Amber.instance.settings.killSwitch.collectAsStateWithLifecycle()
        if (killSwitch.value) {
            AmberWarningCard(
                message = stringResource(R.string.kill_switch_message),
                buttonText = stringResource(R.string.disable_kill_switch),
                onClick = {
                    val killSwitchIntent = Intent(Amber.instance, KillSwitchReceiver::class.java)
                    Amber.instance.sendBroadcast(killSwitchIntent)
                    LocalPreferences.switchToAccount(Amber.instance, account.npub)
                },
            )
        }

        if (hasAccountsWithoutBackup) {
            if (killSwitch.value) {
                Spacer(Modifier.height(4.dp))
            }
            AmberWarningCard(
                message = stringResource(R.string.make_backup_message),
                buttonText = stringResource(R.string.backup),
                onClick = {
                    navController.navigate(Route.AccountBackup.route)
                },
            )
        }

        if (applications.value.isEmpty()) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = stringResource(R.string.congratulations_your_new_account_is_ready),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.your_account_is_ready_to_use))
                    withLink(
                        LinkAnnotation.Url(
                            "https://" + stringResource(R.string.nostr_app),
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) {
                        append(" " + stringResource(R.string.nostr_app))
                    }
                    append(" or ")
                    withLink(
                        LinkAnnotation.Url(
                            if (Amber.instance.isZapstoreInstalled()) "zapstore://" else stringResource(R.string.zapstore_website),
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                        ),
                    ) {
                        append(stringResource(R.string.zapstore))
                    }
                },
            )
        } else {
            AmberButton(
                modifier = Modifier.padding(top = 20.dp),
                onClick = {
                    navController.navigate(Route.Activities.route)
                },
                text = stringResource(R.string.activity),
            )

            applications.value.forEach { applicationWithHistory ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp)
                        .clickable {
                            navController.navigate("Permission/${applicationWithHistory.application.key}")
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            modifier = Modifier.padding(top = 16.dp),
                            text = applicationWithHistory.application.name.ifBlank { applicationWithHistory.application.key.toShortenHex() },
                            fontSize = 24.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                                text = applicationWithHistory.application.key.toShortenHex(),
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                                text = if (applicationWithHistory.latestTime == null) stringResource(R.string.never) else TimeUtils.formatLongToCustomDateTime(applicationWithHistory.latestTime * 1000),
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

fun Context.isZapstoreInstalled(): Boolean {
    return try {
        packageManager.getPackageInfo("dev.zapstore.app", 0)
        true
    } catch (_: Exception) {
        false
    }
}
