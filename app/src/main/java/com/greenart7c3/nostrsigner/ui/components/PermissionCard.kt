package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import kotlin.collections.forEach

@Composable
fun PermissionCard(
    context: Context,
    acceptEventsGroup: List<MutableState<Boolean>>,
    index: Int,
    item: Pair<Any?, List<IntentData>>,
    onDetailsClick: (List<IntentData>) -> Unit,
) {
    Card(
        Modifier
            .padding(4.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        border = BorderStroke(1.dp, Color.Gray),
    ) {
        Column(
            Modifier
                .padding(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        acceptEventsGroup[index].value = !acceptEventsGroup[index].value
                        item.second.forEach { it.checked.value = acceptEventsGroup[index].value }
                    },
            ) {
                Checkbox(
                    checked = acceptEventsGroup[index].value,
                    onCheckedChange = { _ ->
                        acceptEventsGroup[index].value = !acceptEventsGroup[index].value
                        item.second.forEach { it.checked.value = acceptEventsGroup[index].value }
                    },
                    colors = CheckboxDefaults.colors().copy(
                        uncheckedBorderColor = Color.Gray,
                    ),
                )
                val first = item.first
                val permission = if (first is Int) {
                    Permission("sign_event", first)
                } else {
                    Permission(first.toString().toLowerCase(Locale.current), null)
                }

                val message = if (first == SignerType.CONNECT) {
                    stringResource(R.string.connect)
                } else {
                    val encryptedData = item.second.first().encryptedData
                    if (first.toString().contains("ENCRYPT")) {
                        when (encryptedData) {
                            is EventEncryptedDataKind -> {
                                val permission = Permission("sign_event", encryptedData.event.kind)
                                stringResource(R.string.encrypt_with, permission.toLocalizedString(context), first.toString().split("_").first())
                            }

                            is TagArrayEncryptedDataKind -> {
                                stringResource(R.string.encrypt_this_list_of_tags_with, first.toString().split("_").first())
                            }

                            else -> stringResource(R.string.encrypt_this_text_with, first.toString().split("_").first())
                        }
                    } else {
                        if (first.toString().contains("DECRYPT")) {
                            when (encryptedData) {
                                is EventEncryptedDataKind -> {
                                    val permission = Permission("sign_event", encryptedData.event.kind)
                                    stringResource(R.string.read_from_encrypted_content, permission.toLocalizedString(context), first.toString().split("_").first())
                                }

                                is TagArrayEncryptedDataKind -> {
                                    stringResource(R.string.read_this_list_of_tags_from_encrypted_content, first.toString().split("_").first())
                                }

                                is PrivateZapEncryptedDataKind -> {
                                    stringResource(R.string.decrypt_zap_event).capitalize(Locale.current)
                                }

                                else -> stringResource(R.string.read_this_text_from_encrypted_content, first.toString().split("_").first())
                            }
                        } else {
                            permission.toLocalizedString(context)
                        }
                    }
                }

                Text(
                    modifier = Modifier.weight(1f),
                    text = message,
                    color = if (acceptEventsGroup[index].value) Color.Unspecified else Color.Gray,
                )
            }
            if (acceptEventsGroup[index].value) {
                val selected = item.second.filter { it.checked.value }.size
                val total = item.second.size
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDetailsClick(item.second)
                        }
                        .padding(end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.of_events, selected, total),
                        modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )

                    Text(
                        buildAnnotatedString {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "See_details",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = {
                                        onDetailsClick(item.second)
                                    },
                                ),
                            ) {
                                append(stringResource(R.string.see_details))
                            }
                        },
                        modifier = Modifier.padding(start = 46.dp, bottom = 8.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
