package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NewApplicationScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account,
    navController: NavController,
) {
    val dialogOpen = remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.warning)
    val message = stringResource(R.string.invalid_nostr_connect_uri)
    if (dialogOpen.value) {
        SimpleQrCodeScanner {
            dialogOpen.value = false

            if (it.isNullOrBlank()) {
                accountStateViewModel.toast(
                    title,
                    message,
                )
                return@SimpleQrCodeScanner
            }

            if (!it.startsWith("nostrconnect://")) {
                accountStateViewModel.toast(
                    title,
                    message,
                )
                return@SimpleQrCodeScanner
            }

            Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = it.toUri()
                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent.putExtra("closeApplication", false)
                intent.`package` = context.packageName
                IntentUtils.getIntentData(context, intent, null, Route.IncomingRequest.route, account)
            }
        }
    }

    Column(
        modifier =
        modifier
            .fillMaxSize(),
    ) {
        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.new_app_description),
        )

        AmberButton(
            onClick = {
                scope.launch {
                    val clipboardText = clipboardManager.getClipEntry()?.clipData?.getItemAt(0)
                    if (clipboardText == null) {
                        accountStateViewModel.toast(
                            title,
                            message,
                        )
                        return@launch
                    }

                    if (clipboardText.text.isBlank()) {
                        accountStateViewModel.toast(
                            title,
                            message,
                        )
                        return@launch
                    }
                    if (!clipboardText.text.startsWith("nostrconnect://")) {
                        accountStateViewModel.toast(
                            title,
                            message,
                        )
                        return@launch
                    }

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = clipboardText.text.toString().toUri()
                    intent.`package` = context.packageName
                    IntentUtils.getIntentData(
                        context = Amber.instance,
                        intent = intent,
                        packageName = null,
                        route = Route.IncomingRequest.route,
                        currentLoggedInAccount = account,
                    )
                }
            },
            text = stringResource(R.string.paste_from_clipboard),
        )

        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.nostr_connect_description),
        )

        AmberButton(
            onClick = {
                dialogOpen.value = true
            },
            text = stringResource(R.string.scan_qr_code),
        )

        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.nostr_connect_qr_description),
        )

        AmberButton(
            onClick = {
                navController.navigate(Route.NewNsecBunker.route)
            },
            text = stringResource(R.string.add_a_nsecbunker),
        )

        Text(
            modifier = Modifier.padding(bottom = 20.dp),
            text = stringResource(R.string.nsecbunker_description),
        )

        Text(
            buildAnnotatedString {
                append(stringResource(R.string.discover_more))
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
    }
}
