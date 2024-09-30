package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.greenart7c3.nostrsigner.ui.actions.AdjustPermissionsDialog
import com.greenart7c3.nostrsigner.ui.theme.ButtonBorder

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
    var localPermissions by remember {
        mutableStateOf(permissions)
    }

    var showAdjustDialog by remember {
        mutableStateOf(false)
    }

    if (showAdjustDialog) {
        AdjustPermissionsDialog(
            localPermissions ?: emptyList(),
            onClose = {
                showAdjustDialog = false
            },
        ) { dialogPermissions ->
            localPermissions =
                dialogPermissions.map {
                    it.copy()
                }
            showAdjustDialog = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
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
                localPermissions?.let {
                    if (it.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                showAdjustDialog = true
                            },
                            modifier = Modifier
                                .padding(8.dp),
                        ) {
                            Text(text = stringResource(R.string.adjust))
                        }
                    }
                }
            }

            Button(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(8.dp),
                shape = ButtonBorder,
                onClick = {
                    onAccept(localPermissions, selectedOption)
                },
            ) {
                Text(stringResource(R.string.grant_permissions))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                modifier =
                Modifier
                    .padding(8.dp),
                shape = ButtonBorder,
                onClick = onReject,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF5A5554),
                ),
            ) {
                Text(stringResource(R.string.reject))
            }
        }
    }
}
