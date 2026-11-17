package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.pin
import com.greenart7c3.nostrsigner.commonsui.pin_does_not_match
import com.greenart7c3.nostrsigner.ui.components.RandomPinInput
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SetupPinScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
    Column(
        modifier,
    ) {
        RandomPinInput { pin ->
            navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
        }
    }
}

@Composable
fun ConfirmPinScreen(
    modifier: Modifier = Modifier,
    pin: String,
    navController: NavController,
) {
    val settingsStore = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    Column(
        modifier,
    ) {
        RandomPinInput(
            onPinEntered = { enteredPin ->
                if (enteredPin != pin) {
                    ToastManager.toast(getStringBlocking(Res.string.pin), getStringBlocking(Res.string.pin_does_not_match))
                } else {
                    val usePin = settingsStore.settings.usePin
                    scope.launch(Dispatchers.IO) {
                        if (usePin) {
                            settingsStore.settings = settingsStore.settings.copy(usePin = false)
                            LocalPreferences.saveSettingsToEncryptedStorage(settingsStore.settings)
                            LocalPreferences.savePinToEncryptedStorage(null)
                        } else {
                            settingsStore.settings = settingsStore.settings.copy(usePin = true)
                            LocalPreferences.saveSettingsToEncryptedStorage(settingsStore.settings)
                            LocalPreferences.savePinToEncryptedStorage(pin)
                        }
                        scope.launch(Dispatchers.Main) {
                            navController.navigate(Route.Security.route) {
                                popUpTo(Route.Security.route) {
                                    inclusive = true
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}
