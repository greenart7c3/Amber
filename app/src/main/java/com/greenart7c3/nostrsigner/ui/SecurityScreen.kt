package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.greenart7c3.nostrsigner.ui.navigation.Route
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
    var enableBiometrics by remember { mutableStateOf(Amber.instance.settings.useAuth) }
    val setupPin by remember { mutableStateOf(Amber.instance.settings.usePin) }
    var biometricsIndex by remember {
        mutableIntStateOf(Amber.instance.settings.biometricsTimeType.screenCode)
    }
    val scope = rememberCoroutineScope()
    Surface(
        modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
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

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable {
                            if (setupPin) {
                                val pin = LocalPreferences.loadPinFromEncryptedStorage()
                                navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
                            } else {
                                navController.navigate(Route.SetupPin.route)
                            }
                        },
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.setup_pin),
                    )
                    Switch(
                        checked = setupPin,
                        onCheckedChange = {
                            if (setupPin) {
                                val pin = LocalPreferences.loadPinFromEncryptedStorage()
                                navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
                            } else {
                                navController.navigate(Route.SetupPin.route)
                            }
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

            AmberButton(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        Amber.instance.settings = Amber.instance.settings.copy(
                            useAuth = enableBiometrics,
                            biometricsTimeType = parseBiometricsTimeType(biometricsIndex),
                        )
                        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                        scope.launch(Dispatchers.Main) {
                            navController.navigateUp()
                        }
                    }
                },
                text = stringResource(R.string.save),
            )
        }
    }
}
