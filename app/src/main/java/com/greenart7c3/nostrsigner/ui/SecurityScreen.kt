package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.components.PostButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SecurityScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
    val biometricItems =
        persistentListOf(
            TitleExplainer(stringResource(BiometricsTimeType.EVERY_TIME.resourceId)),
            TitleExplainer(stringResource(BiometricsTimeType.ONE_MINUTE.resourceId)),
            TitleExplainer(stringResource(BiometricsTimeType.FIVE_MINUTES.resourceId)),
            TitleExplainer(stringResource(BiometricsTimeType.TEN_MINUTES.resourceId)),
        )
    var enableBiometrics by remember { mutableStateOf(NostrSigner.getInstance().settings.useAuth) }
    var biometricsIndex by remember {
        mutableIntStateOf(NostrSigner.getInstance().settings.biometricsTimeType.screenCode)
    }
    val scope = rememberCoroutineScope()
    Surface(
        modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
        ) {
            Column(
                Modifier.weight(1f),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable {
                            enableBiometrics = !enableBiometrics
                        },
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.enable_biometrics),
                    )
                    Switch(
                        checked = enableBiometrics,
                        onCheckedChange = {
                            enableBiometrics = !enableBiometrics
                        },
                    )
                }
                Box(
                    Modifier
                        .padding(8.dp),
                ) {
                    SettingsRow(
                        R.string.when_to_ask,
                        R.string.when_to_ask,
                        biometricItems,
                        biometricsIndex,
                    ) {
                        biometricsIndex = it
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PostButton(isActive = true) {
                    scope.launch(Dispatchers.IO) {
                        NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(
                            useAuth = enableBiometrics,
                            biometricsTimeType = parseBiometricsTimeType(biometricsIndex),
                        )
                        LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
                        scope.launch(Dispatchers.Main) {
                            navController.navigateUp()
                        }
                    }
                }
            }
        }
    }
}
