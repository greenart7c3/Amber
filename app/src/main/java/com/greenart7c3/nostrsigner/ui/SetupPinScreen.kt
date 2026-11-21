package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
    ) {
        RandomPinInput(
            onPinEnter = { pin ->
                navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
            },
        )
    }
}

@Composable
fun ConfirmPinScreen(
    pin: String,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val settingsStore = LocalSettingsStore.current
    val scope = rememberCoroutineScope()
    Column(
        modifier,
    ) {
        RandomPinInput(
            onPinEnter = { enteredPin ->
                if (enteredPin != pin) {
                    ToastManager.toast(getStringBlocking(Res.string.pin), getStringBlocking(Res.string.pin_does_not_match))
                } else {
                    val usePin = settingsStore.settings.usePin
                    scope.launch(Dispatchers.IO) {
                        if (usePin) {
                            settingsStore.settings = settingsStore.settings.copy(usePin = false)
                            LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                            LocalPreferences.savePinToEncryptedStorage(context, null)
                        } else {
                            settingsStore.settings = settingsStore.settings.copy(usePin = true)
                            LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
                            LocalPreferences.savePinToEncryptedStorage(context, pin)
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
