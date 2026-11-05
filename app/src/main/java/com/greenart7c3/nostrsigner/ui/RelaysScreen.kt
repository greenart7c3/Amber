package com.greenart7c3.nostrsigner.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.active_relays
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port_in_use
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port_invalid
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_server
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_server_sub
import com.greenart7c3.nostrsigner.commonsui.d_saved
import com.greenart7c3.nostrsigner.commonsui.default_profile_relays
import com.greenart7c3.nostrsigner.commonsui.relay_trust_score
import com.greenart7c3.nostrsigner.commonsui.relay_trust_score_description
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.service.LocalBunkerRelayServer
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun RelaysScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
    val scope = rememberCoroutineScope()
    var trustScoreEnabled by remember { mutableStateOf(Amber.instance.settings.trustScoreEnabled) }

    fun setTrustScoreEnabled(enabled: Boolean) {
        trustScoreEnabled = enabled
        Amber.instance.settings = Amber.instance.settings.copy(trustScoreEnabled = enabled)
        if (!enabled) {
            TrustScoreService.clearCache()
        }
        scope.launch(Dispatchers.IO) {
            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AmberButton(
            text = stringResource(Res.string.active_relays),
            onClick = {
                navController.navigate(Route.ActiveRelays.route)
            },
        )

        AmberButton(
            text = stringResource(Res.string.default_profile_relays),
            onClick = {
                navController.navigate(Route.DefaultProfileRelaysScreen.route)
            },
        )

        if (!BuildFlavorChecker.isOfflineFlavor()) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        setTrustScoreEnabled(!trustScoreEnabled)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(Res.string.relay_trust_score))
                    Text(
                        text = stringResource(Res.string.relay_trust_score_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                Switch(
                    checked = trustScoreEnabled,
                    onCheckedChange = { enabled ->
                        setTrustScoreEnabled(enabled)
                    },
                )
            }

            LocalBunkerServerSettings()
        }
    }
}

/** The local bunker relay switch and its port (mirrors desktop Settings -> Desktop). */
@Composable
private fun LocalBunkerServerSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(Amber.instance.settings.localBunkerServer) }
    var port by remember { mutableIntStateOf(LocalBunkerRelayServer.port()) }
    var portText by remember { mutableStateOf(port.toString()) }
    val invalidPort = stringResource(Res.string.d_local_bunker_port_invalid)
    val portInUse = stringResource(Res.string.d_local_bunker_port_in_use)
    val saved = stringResource(Res.string.d_saved)

    fun setEnabled(value: Boolean) {
        enabled = value
        Amber.instance.settings = Amber.instance.settings.copy(localBunkerServer = value)
        scope.launch(Dispatchers.IO) {
            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
            LocalBunkerRelayServer.applySetting()
        }
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { setEnabled(!enabled) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(Res.string.d_local_bunker_server))
            Text(
                text = stringResource(Res.string.d_local_bunker_server_sub, port),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { setEnabled(it) },
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(Res.string.d_local_bunker_port)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        AmberButton(
            text = stringResource(Res.string.save),
            onClick = {
                val newPort = portText.toIntOrNull()
                if (newPort == null || newPort !in 1024..65535) {
                    Toast.makeText(context, invalidPort, Toast.LENGTH_SHORT).show()
                    return@AmberButton
                }
                scope.launch(Dispatchers.IO) {
                    val applied = LocalBunkerRelayServer.changePort(newPort)
                    withContext(Dispatchers.Main) {
                        if (applied) port = newPort else portText = port.toString()
                        Toast.makeText(context, if (applied) saved else portInUse, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}
