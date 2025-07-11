package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowWidthSizeClass
import coil3.compose.AsyncImage
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.service.ProfileFetcherService
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ProfilePicture(account: Account) {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    if (windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT) {
        var profileUrl by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                ProfileFetcherService().fetchProfileData(
                    account = account,
                    onPictureFound = {
                        profileUrl = it
                    },
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            @Suppress("KotlinConstantConditions")
            if (!profileUrl.isNullOrBlank() && BuildConfig.FLAVOR != "offline") {
                AsyncImage(
                    profileUrl,
                    Route.Accounts.route,
                    Modifier
                        .clip(
                            RoundedCornerShape(50),
                        )
                        .height(120.dp)
                        .width(120.dp),
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    Route.Accounts.route,
                    modifier = Modifier
                        .border(
                            2.dp,
                            Color.fromHex(account.hexKey.slice(0..5)),
                            CircleShape,
                        )
                        .height(120.dp)
                        .width(120.dp),
                )
            }
            Text(
                account.name.ifBlank { account.npub.toShortenHex() },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun LoginWithPubKey(
    shouldCloseApp: Boolean,
    modifier: Modifier,
    account: Account,
    packageName: String?,
    appName: String,
    applicationName: String?,
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

    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    Column(
        modifier,
    ) {
        ProfilePicture(account)

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

        var selectedOption by remember { mutableIntStateOf(account.signPolicy) }

        Text(
            text = stringResource(R.string.handle_application_permissions),
        )

        Spacer(modifier = Modifier.size(8.dp))

        var closeApp by remember { mutableStateOf(shouldCloseApp) }
        if (packageName == null) {
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
