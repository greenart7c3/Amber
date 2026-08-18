package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
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
    var privacyMode by remember { mutableStateOf(Amber.instance.settings.privacyMode) }
    var requireUnlockedDevice by remember { mutableStateOf(Amber.instance.settings.requireUnlockedDevice) }
    var requireUnlockedDeviceUpdating by remember { mutableStateOf(false) }
    var biometricsIndex by remember {
        mutableIntStateOf(Amber.instance.settings.biometricsTimeType.screenCode)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun toggleRequireUnlockedDevice(enabled: Boolean) {
        requireUnlockedDevice = enabled
        requireUnlockedDeviceUpdating = true
        // Use the application-scoped IOScope, not the
        // composition scope: key rotation must complete
        // even if the user leaves the screen/app, or
        // stored secrets could be left inaccessible.
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            try {
                LocalPreferences.updateRequireUnlockedDevice(context, enabled)
            } catch (e: Exception) {
                AmberLog.e(Amber.TAG, "Error toggling require unlocked device", e)
            } finally {
                // Re-sync the toggle with the persisted setting (reverts on failure)
                requireUnlockedDevice = Amber.instance.settings.requireUnlockedDevice
                requireUnlockedDeviceUpdating = false
            }
        }
    }
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
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable {
                            val newValue = !privacyMode
                            privacyMode = newValue
                            scope.launch(Dispatchers.IO) {
                                LocalPreferences.updatePrivacyMode(context, newValue)
                            }
                        },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.privacy_mode))
                        Text(
                            text = stringResource(R.string.privacy_mode_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                    Switch(
                        checked = privacyMode,
                        onCheckedChange = { enabled ->
                            privacyMode = enabled
                            scope.launch(Dispatchers.IO) {
                                LocalPreferences.updatePrivacyMode(context, enabled)
                            }
                        },
                    )
                }

                // GHSA-8844-q5vh-9j8f, L1: opt-in toggle
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clickable(enabled = !requireUnlockedDeviceUpdating) {
                            toggleRequireUnlockedDevice(!requireUnlockedDevice)
                        },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = stringResource(R.string.require_unlocked_device))
                        Text(
                            text = stringResource(R.string.require_unlocked_device_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                        if (requireUnlockedDeviceUpdating) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.require_unlocked_device_updating),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = stringResource(R.string.require_unlocked_device_do_not_close),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Switch(
                        checked = requireUnlockedDevice,
                        enabled = !requireUnlockedDeviceUpdating,
                        onCheckedChange = { enabled ->
                            toggleRequireUnlockedDevice(enabled)
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
                                scope.launch(Dispatchers.IO) {
                                    val pin = LocalPreferences.loadPinFromEncryptedStorage()
                                    scope.launch(Dispatchers.Main) {
                                        navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
                                    }
                                }
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
                                scope.launch(Dispatchers.IO) {
                                    val pin = LocalPreferences.loadPinFromEncryptedStorage()
                                    scope.launch(Dispatchers.Main) {
                                        navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
                                    }
                                }
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
