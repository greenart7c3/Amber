package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity.RESULT_OK
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.minutesBetween
import com.greenart7c3.nostrsigner.service.Biometrics
import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BiometricAuthScreen(
    onAuth: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                onAuth(true)
            }
        }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val settings = Amber.instance.settings
            val lastAuthTime = settings.lastBiometricsTime
            val whenToAsk = settings.biometricsTimeType
            val isTimeToAsk = when (whenToAsk) {
                BiometricsTimeType.EVERY_TIME -> true
                BiometricsTimeType.ONE_MINUTE -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 1
                BiometricsTimeType.FIVE_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 5
                BiometricsTimeType.TEN_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 10
            }
            val shouldAuthenticate = (settings.useAuth || settings.usePin) && isTimeToAsk
            if (!shouldAuthenticate) {
                onAuth(true)
            } else {
                launch(Dispatchers.Main) {
                    if (settings.usePin) {
                        showPinDialog = true
                    } else {
                        Biometrics.authenticate(
                            context.getString(R.string.authenticate),
                            context,
                            keyguardLauncher,
                            {
                                Amber.instance.settings = Amber.instance.settings.copy(
                                    lastBiometricsTime = System.currentTimeMillis(),
                                )

                                LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                onAuth(true)
                            },
                            { _, message ->
                                Amber.instance.getMainActivity()?.finishAndRemoveTask()
                                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        message,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        Dialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = {
                showPinDialog = false
                Amber.instance.getMainActivity()?.finishAndRemoveTask()
                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        Amber.instance.getString(R.string.pin_does_not_match),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(
                    Modifier.padding(40.dp),
                ) {
                    RandomPinInput(
                        text = stringResource(R.string.enter_pin),
                        onPinEntered = {
                            val pin = LocalPreferences.loadPinFromEncryptedStorage()
                            if (it == pin) {
                                Amber.instance.settings = Amber.instance.settings.copy(
                                    lastBiometricsTime = System.currentTimeMillis(),
                                )

                                LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
                                onAuth(true)
                                showPinDialog = false
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.pin_does_not_match),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    )
                }
            }
        }
    }
}
