package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        modifier,
    ) {
        RandomPinInput(
            onPinEntered = { enteredPin ->
                if (enteredPin != pin) {
                    ToastManager.toast(context.getString(R.string.pin), context.getString(R.string.pin_does_not_match))
                } else {
                    val usePin = Amber.instance.settings.usePin
                    scope.launch(Dispatchers.IO) {
                        if (usePin) {
                            Amber.instance.settings = Amber.instance.settings.copy(usePin = false)
                            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                            LocalPreferences.savePinToEncryptedStorage(null)
                        } else {
                            Amber.instance.settings = Amber.instance.settings.copy(usePin = true)
                            LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
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
