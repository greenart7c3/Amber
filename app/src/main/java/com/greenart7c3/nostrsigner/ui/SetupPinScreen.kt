package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.components.RandomPinInput
import com.greenart7c3.nostrsigner.ui.navigation.Route

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
    accountStateViewModel: AccountStateViewModel,
    pin: String,
    navController: NavController,
) {
    val context = LocalContext.current
    Column(
        modifier,
    ) {
        RandomPinInput(
            onPinEntered = { enteredPin ->
                if (enteredPin != pin) {
                    accountStateViewModel.toast(context.getString(R.string.pin), context.getString(R.string.pin_does_not_match))
                } else {
                    val usePin = NostrSigner.instance.settings.usePin
                    if (usePin) {
                        NostrSigner.instance.settings = NostrSigner.instance.settings.copy(usePin = false)
                        LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.instance.settings)
                        LocalPreferences.savePinToEncryptedStorage(null)
                        navController.navigate(Route.Security.route) {
                            popUpTo(Route.Security.route) {
                                inclusive = true
                            }
                        }
                    } else {
                        NostrSigner.instance.settings = NostrSigner.instance.settings.copy(usePin = true)
                        LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.instance.settings)
                        LocalPreferences.savePinToEncryptedStorage(pin)
                        navController.navigate(Route.Security.route) {
                            popUpTo(Route.Security.route) {
                                inclusive = true
                            }
                        }
                    }
                }
            },
        )
    }
}
