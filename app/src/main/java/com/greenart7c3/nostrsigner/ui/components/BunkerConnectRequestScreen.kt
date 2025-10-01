package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.greenart7c3.nostrsigner.ui.DeleteAfterType
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.SettingsRow
import com.greenart7c3.nostrsigner.ui.deleteAfterToSeconds
import com.greenart7c3.nostrsigner.ui.parseDeleteAfterType
import kotlin.collections.forEach
import kotlinx.collections.immutable.persistentListOf

@Composable
fun BunkerConnectRequestScreen(
    modifier: Modifier,
    shouldCloseApp: Boolean,
    account: Account,
    appName: String,
    permissions: List<Permission>?,
    onAccept: (List<Permission>?, Int, Boolean?, RememberType, Long) -> Unit,
    onReject: (RememberType) -> Unit,
) {
    val localPermissions = remember {
        val snapshot = mutableStateListOf<Permission>()
        permissions?.forEach {
            snapshot.add(it)
        }
        snapshot
    }

    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    Column(
        modifier,
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
        val deleteAfterItems =
            persistentListOf(
                TitleExplainer(stringResource(DeleteAfterType.NEVER.resourceId)),
                TitleExplainer(stringResource(DeleteAfterType.FIVE_MINUTES.resourceId)),
                TitleExplainer(stringResource(DeleteAfterType.TEN_MINUTES.resourceId)),
                TitleExplainer(stringResource(DeleteAfterType.ONE_HOUR.resourceId)),
                TitleExplainer(stringResource(DeleteAfterType.ONE_DAY.resourceId)),
                TitleExplainer(stringResource(DeleteAfterType.ONE_WEEK.resourceId)),
            )
        var deleteAfterIndex by remember { mutableIntStateOf(DeleteAfterType.NEVER.screenCode) }

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

        Box(
            Modifier.padding(4.dp),
        ) {
            SettingsRow(
                R.string.delete_after,
                null,
                deleteAfterItems,
                deleteAfterIndex,
            ) {
                deleteAfterIndex = it
            }
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
                    val deleteAfter = deleteAfterToSeconds(parseDeleteAfterType(deleteAfterIndex))
                    onAccept(localPermissions, selectedOption, closeApp, rememberType, deleteAfter)
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
