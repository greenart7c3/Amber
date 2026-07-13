package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.TrustScoreService
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            text = stringResource(R.string.active_relays),
            onClick = {
                navController.navigate(Route.ActiveRelays.route)
            },
        )

        AmberButton(
            text = stringResource(R.string.default_profile_relays),
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
                    Text(text = stringResource(R.string.relay_trust_score))
                    Text(
                        text = stringResource(R.string.relay_trust_score_description),
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
        }
    }
}
