package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.toShortenHex

@Composable
fun PermissionsScreen(
    modifier: Modifier,
    account: Account,
    navController: NavController,
) {
    val applications = NostrSigner.instance.getDatabase(account.npub).applicationDao().getAllFlow(account.hexKey).collectAsStateWithLifecycle(emptyList())

    Column(
        modifier,
    ) {
        if (applications.value.isEmpty()) {
            Text(
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
                },
            )
        } else {
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
