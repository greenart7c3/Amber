package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission

@Composable
fun LoginWithPubKey(
    account: Account,
    packageName: String?,
    appName: String,
    applicationName: String?,
    permissions: List<Permission>?,
    onAccept: (List<Permission>?, Int) -> Unit,
    onReject: () -> Unit,
) {
    val localPermissions = remember {
        val snapshot = mutableStateListOf<Permission>()
        permissions?.forEach {
            snapshot.add(it)
        }
        snapshot
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        packageName?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = it,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
        }

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(applicationName ?: appName)
                }
                append(stringResource(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf))
            },
            fontSize = 18.sp,
        )

        Spacer(Modifier.size(8.dp))

        val radioOptions = listOf(
            TitleExplainer(
                title = stringResource(R.string.sign_policy_basic),
                explainer = stringResource(R.string.sign_policy_basic_explainer),
            ),
            TitleExplainer(
                title = stringResource(R.string.sign_policy_manual_new_app),
                explainer = stringResource(R.string.sign_policy_manual_new_app_explainer),
            ),
            TitleExplainer(
                title = stringResource(R.string.sign_policy_fully),
                explainer = stringResource(R.string.sign_policy_fully_explainer),
            ),
        )
        var selectedOption by remember { mutableIntStateOf(account.signPolicy) }

        Text(
            text = stringResource(R.string.handle_application_permissions),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.size(8.dp))

        radioOptions.forEachIndexed { index, option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selectedOption == index,
                        onClick = {
                            selectedOption = index
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedOption == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selectedOption == index,
                    onClick = {
                        selectedOption = index
                    },
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = option.title,
                        modifier = Modifier.padding(start = 16.dp),
                        fontWeight = FontWeight.Bold,
                    )
                    option.explainer?.let {
                        Text(
                            text = it,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally,
        ) {
            if (selectedOption == 1) {
                var selectAll by remember {
                    mutableStateOf(true)
                }
                val enabledPermissions = localPermissions.map {
                    remember { mutableStateOf(it.checked) }
                }
                if (localPermissions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                selectAll = !selectAll
                                val permissions2 = localPermissions.map { permission ->
                                    permission.copy(checked = selectAll)
                                }
                                localPermissions.clear()
                                localPermissions.addAll(permissions2)
                                enabledPermissions.forEach {
                                    it.value = selectAll
                                }
                            },
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(R.string.select_deselect_all),
                        )
                        Switch(
                            checked = selectAll,
                            onCheckedChange = {
                                selectAll = !selectAll
                                val permissions2 = localPermissions.map { permission ->
                                    permission.copy(checked = selectAll)
                                }
                                localPermissions.clear()
                                localPermissions.addAll(permissions2)
                                enabledPermissions.forEach {
                                    it.value = selectAll
                                }
                            },
                        )
                    }
                    localPermissions.forEachIndexed { index, permission ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    permission.checked = !permission.checked
                                    enabledPermissions[index].value = permission.checked
                                },
                        ) {
                            Text(
                                modifier = Modifier.weight(1f),
                                text = permission.toLocalizedString(LocalContext.current),
                            )
                            Switch(
                                checked = enabledPermissions[index].value,
                                onCheckedChange = { _ ->
                                    permission.checked = !permission.checked
                                    enabledPermissions[index].value = permission.checked
                                },
                            )
                        }
                    }
                }
            }

            AmberButton(
                onClick = {
                    onAccept(localPermissions, selectedOption)
                },
                content = {
                    Text(stringResource(R.string.grant_permissions))
                },
            )
            Spacer(modifier = Modifier.width(16.dp))
            AmberButton(
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B00),
                ),
                content = {
                    Text(stringResource(R.string.reject))
                },
            )
        }
    }
}
