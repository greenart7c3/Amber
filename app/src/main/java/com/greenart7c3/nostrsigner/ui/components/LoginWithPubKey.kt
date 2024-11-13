package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission

@Composable
fun LoginWithPubKey(
    remember: MutableState<Boolean>,
    isBunkerRequest: Boolean,
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

    if (isBunkerRequest) {
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
                modifier = Modifier.fillMaxWidth(),
                text = applicationName ?: appName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                stringResource(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf),
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                Arrangement.Center,
                Alignment.CenterHorizontally,
            ) {
                RememberMyChoice(
                    alwaysShow = true,
                    shouldRunAcceptOrReject = null,
                    onAccept = {},
                    onReject = onReject,
                    remember = remember.value,
                    onChanged = {
                        remember.value = !remember.value
                    },
                    packageName = packageName,
                )

                AmberButton(
                    modifier = Modifier.padding(vertical = 20.dp),
                    onClick = {
                        onAccept(localPermissions, 1)
                    },
                    text = stringResource(R.string.grant_permissions),
                )

                AmberButton(
                    modifier = Modifier.padding(vertical = 20.dp),
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B00),
                    ),
                    text = stringResource(R.string.reject),
                )
            }
        }
    } else {
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
                modifier = Modifier.fillMaxWidth(),
                text = applicationName ?: appName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                stringResource(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf),
            )

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
                    if (selectedOption == index) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF1D8802),
                        )
                    } else {
                        RadioButton(
                            selected = false,
                            onClick = {
                                selectedOption = index
                            },
                        )
                    }
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
                    val enabledPermissions = localPermissions.map {
                        remember { mutableStateOf(it.checked) }
                    }
                    if (localPermissions.isNotEmpty()) {
                        localPermissions.forEachIndexed { index, permission ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                border = BorderStroke(1.dp, Color.LightGray),
                                colors = CardDefaults.cardColors().copy(
                                    containerColor = MaterialTheme.colorScheme.background,
                                ),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            permission.checked = !permission.checked
                                            enabledPermissions[index].value = permission.checked
                                        },
                                ) {
                                    Checkbox(
                                        checked = enabledPermissions[index].value,
                                        onCheckedChange = { _ ->
                                            permission.checked = !permission.checked
                                            enabledPermissions[index].value = permission.checked
                                        },
                                    )
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        text = permission.toLocalizedString(LocalContext.current),
                                    )
                                }
                            }
                        }
                    }
                }

                AmberButton(
                    modifier = Modifier.padding(vertical = 20.dp),
                    onClick = {
                        onAccept(localPermissions, selectedOption)
                    },
                    text = stringResource(R.string.grant_permissions),
                )

                AmberButton(
                    modifier = Modifier.padding(vertical = 20.dp),
                    onClick = onReject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B00),
                    ),
                    text = stringResource(R.string.reject),
                )
            }
        }
    }
}
