package com.greenart7c3.nostrsigner.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.model.AmberEvent

@Composable
fun SeeDetailsScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        var rememberMyChoice by remember { mutableStateOf(MultiEventScreenIntents.intents.first().rememberMyChoice.value) }
        val first = MultiEventScreenIntents.intents.first()
        val permission = if (first.type == SignerType.SIGN_EVENT) {
            Permission("sign_event", first.event!!.kind)
        } else {
            Permission(first.type.toString().toLowerCase(Locale.current), null)
        }

        val message = if (first.type == SignerType.CONNECT) {
            stringResource(R.string.connect)
        } else {
            permission.toLocalizedString(context)
        }
        Text(
            stringResource(R.string.is_requiring_to_sign_these_events_related_to_permission, MultiEventScreenIntents.appName, message),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable {
                    rememberMyChoice = !rememberMyChoice
                    MultiEventScreenIntents.intents.forEach { intent ->
                        intent.rememberMyChoice.value = rememberMyChoice
                    }
                },
        ) {
            Switch(
                modifier = Modifier.scale(0.85f),
                checked = rememberMyChoice,
                onCheckedChange = {
                    rememberMyChoice = !rememberMyChoice
                    MultiEventScreenIntents.intents.forEach { intent ->
                        intent.rememberMyChoice.value = rememberMyChoice
                    }
                },
            )
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                text = stringResource(R.string.always_approve_this_permission),
            )
        }

        MultiEventScreenIntents.intents.forEach { intent ->
            Card(
                Modifier
                    .padding(4.dp),
                colors = CardDefaults.cardColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                border = BorderStroke(1.dp, Color.Gray),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            intent.checked.value = !intent.checked.value
                        },
                ) {
                    Checkbox(
                        checked = intent.checked.value,
                        onCheckedChange = { _ ->
                            intent.checked.value = !intent.checked.value
                        },
                        colors = CheckboxDefaults.colors().copy(
                            uncheckedBorderColor = Color.Gray,
                        ),
                    )

                    val data = if (intent.type == SignerType.SIGN_EVENT) {
                        val event = intent.event!!
                        if (event.kind == 22242) AmberEvent.relay(event) else event.content
                    } else {
                        intent.encryptedData ?: intent.data
                    }

                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        text = data.ifBlank { message },
                        color = if (intent.checked.value) Color.Unspecified else Color.Gray,
                    )
                }
            }
        }
    }
}
