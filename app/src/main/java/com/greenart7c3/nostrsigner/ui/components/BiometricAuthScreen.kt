package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.LockSettings
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.authenticate
import com.greenart7c3.nostrsigner.commonsui.enter_pin
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.pin_does_not_match
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.minutesBetween
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.service.Biometrics
import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun BiometricAuthScreen(
    onAuth: (Boolean) -> Unit,
) {
    val settingsStore = LocalSettingsStore.current
    val appScope = LocalAppScope.current
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPinDialog by remember { mutableStateOf(false) }
    val currentOnAuth by rememberUpdatedState(onAuth)
    val keyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                onAuth(true)
            }
        }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val settings = settingsStore.settings
            if (!shouldAuthenticate(settings)) {
                currentOnAuth(true)
            } else {
                launch(Dispatchers.Main) {
                    if (settings.usePin) {
                        showPinDialog = true
                    } else {
                        Biometrics.authenticate(
                            getStringBlocking(Res.string.authenticate),
                            context,
                            keyguardLauncher,
                            {
                                recordAuthentication(settingsStore, context)
                                currentOnAuth(true)
                            },
                            { _, message ->
                                finishWithToast(activity, appScope, context) { message }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        PinDialog(
            onDismissRequest = {
                showPinDialog = false
                finishWithToast(activity, appScope, context) { getStringBlocking(Res.string.pin_does_not_match) }
            },
            onPinEnter = { enteredPin ->
                verifyPin(scope, context, enteredPin) {
                    recordAuthentication(settingsStore, context)
                    onAuth(true)
                    showPinDialog = false
                }
            },
        )
    }
}

@Composable
private fun PinDialog(
    onDismissRequest: () -> Unit,
    onPinEnter: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                Modifier.padding(40.dp),
            ) {
                RandomPinInput(
                    text = stringResource(Res.string.enter_pin),
                    onPinEnter = onPinEnter,
                )
            }
        }
    }
}

private fun shouldAuthenticate(settings: AmberSettings): Boolean {
    val lastAuthTime = settings.lastBiometricsTime
    val whenToAsk = settings.biometricsTimeType
    val isTimeToAsk = when (whenToAsk) {
        BiometricsTimeType.EVERY_TIME -> true
        BiometricsTimeType.ONE_MINUTE -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 1
        BiometricsTimeType.FIVE_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 5
        BiometricsTimeType.TEN_MINUTES -> minutesBetween(lastAuthTime, System.currentTimeMillis()) >= 10
    }
    return (settings.useAuth || settings.usePin) && isTimeToAsk
}

private fun recordAuthentication(settingsStore: SettingsStore, context: Context) {
    settingsStore.settings = settingsStore.settings.copy(
        lastBiometricsTime = System.currentTimeMillis(),
    )
    LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
}

private fun finishWithToast(
    activity: Activity?,
    appScope: CoroutineScope,
    context: Context,
    message: () -> String,
) {
    activity?.finishAndRemoveTask()
    appScope.launch(Dispatchers.Main) {
        Toast.makeText(
            context,
            message(),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun verifyPin(
    scope: CoroutineScope,
    context: Context,
    enteredPin: String,
    onMatch: () -> Unit,
) {
    scope.launch(Dispatchers.IO) {
        val pin = LockSettings.loadPinFromEncryptedStorage(context)
        scope.launch(Dispatchers.Main) {
            if (enteredPin == pin) {
                onMatch()
            } else {
                Toast.makeText(
                    context,
                    getStringBlocking(Res.string.pin_does_not_match),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
