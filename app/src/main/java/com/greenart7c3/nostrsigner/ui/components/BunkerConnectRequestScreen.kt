package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import kotlin.collections.forEach

@Composable
fun BunkerConnectRequestScreen(
    shouldCloseApp: Boolean,
    paddingValues: PaddingValues,
    account: Account,
    appName: String,
    permissions: List<Permission>?,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    val localPermissions = remember {
        val snapshot = mutableStateListOf<Permission>()
        permissions?.forEach {
            snapshot.add(it)
        }
        snapshot
    }

    val scrollState = rememberScrollState()
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(paddingValues),
    ) {
        ProfilePicture(account)

        Text(
            modifier = Modifier.fillMaxWidth(),
            text = appName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Text(
            stringResource(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf),
        )

        var selectedOption by remember { mutableIntStateOf(account.signPolicy) }

        Text(
            text = stringResource(R.string.handle_application_permissions),
        )

        Spacer(modifier = Modifier.size(8.dp))

        var closeApp by remember { mutableStateOf(shouldCloseApp) }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable {
                    closeApp = !closeApp
                },
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.close_application),
            )
            Switch(
                checked = closeApp,
                onCheckedChange = {
                    closeApp = it
                },
            )
        }

        ChooseSignPolicy(
            selectedOption = selectedOption,
            onSelected = {
                selectedOption = it
            },
        )

        Spacer(modifier = Modifier.weight(1f))

        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            Arrangement.Center,
            Alignment.CenterHorizontally,
        ) {
            if (selectedOption == 1) {
                EnabledPermissions(localPermissions)
            }

            AmberButton(
                modifier = Modifier.padding(vertical = 20.dp),
                onClick = {
                    onAccept(localPermissions, selectedOption, closeApp, rememberType)
                },
                text = stringResource(R.string.grant_permissions),
            )

            AmberButton(
                modifier = Modifier.padding(vertical = 20.dp),
                onClick = {
                    onReject(rememberType)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B00),
                ),
                text = stringResource(R.string.reject),
            )
        }
    }
}
