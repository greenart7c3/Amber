package com.greenart7c3.nostrsigner.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.KeystorePolicy
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.authenticate
import com.greenart7c3.nostrsigner.commonsui.enable_biometrics
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.history_retention
import com.greenart7c3.nostrsigner.commonsui.history_retention_description
import com.greenart7c3.nostrsigner.commonsui.log_retention
import com.greenart7c3.nostrsigner.commonsui.log_retention_description
import com.greenart7c3.nostrsigner.commonsui.privacy_mode
import com.greenart7c3.nostrsigner.commonsui.privacy_mode_description
import com.greenart7c3.nostrsigner.commonsui.require_unlocked_device
import com.greenart7c3.nostrsigner.commonsui.require_unlocked_device_description
import com.greenart7c3.nostrsigner.commonsui.require_unlocked_device_do_not_close
import com.greenart7c3.nostrsigner.commonsui.require_unlocked_device_legacy_note
import com.greenart7c3.nostrsigner.commonsui.require_unlocked_device_updating
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.commonsui.setup_pin
import com.greenart7c3.nostrsigner.commonsui.when_to_ask
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.AmberSettings
import com.greenart7c3.nostrsigner.models.RetentionPeriod
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SecurityScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val settingsStore = LocalSettingsStore.current
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    val state = remember { SecurityState(settingsStore.settings, KeystorePolicy.requireUnlockedDevice(context)) }
    val scope = rememberCoroutineScope()
    val biometricsKeyguardLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                state.enableBiometrics = !state.enableBiometrics
            }
        }
    val actions = SecurityActions(state, context, appScope, scope, biometricsKeyguardLauncher, navController)

    Surface(
        modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            SecuritySettings(
                state = state,
                actions = actions,
                modifier = Modifier.weight(1f),
            )

            AmberButton(
                onClick = { actions.save(settingsStore) },
                text = stringResource(Res.string.save),
            )
        }
    }
}

@Stable
private class SecurityState(settings: AmberSettings, requireUnlockedDevice: Boolean) {
    var enableBiometrics by mutableStateOf(settings.useAuth)
    val setupPin = settings.usePin
    var privacyMode by mutableStateOf(settings.privacyMode)
    var logRetention by mutableStateOf(settings.logRetention)
    var historyRetention by mutableStateOf(settings.historyRetention)
    var requireUnlockedDevice by mutableStateOf(requireUnlockedDevice)
    var requireUnlockedDeviceUpdating by mutableStateOf(false)
    var biometricsIndex by mutableIntStateOf(settings.biometricsTimeType.screenCode)
}

private class SecurityActions(
    private val state: SecurityState,
    private val context: Context,
    private val appScope: CoroutineScope,
    private val scope: CoroutineScope,
    private val keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    private val navController: NavController,
) {
    fun toggleBiometrics() {
        authenticate(
            title = getStringBlocking(Res.string.authenticate),
            context = context,
            keyguardLauncher = keyguardLauncher,
            onApproved = {
                state.enableBiometrics = !state.enableBiometrics
            },
            onError = { _, message ->
                appScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        message,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    fun setPrivacyMode(enabled: Boolean) {
        state.privacyMode = enabled
        scope.launch(Dispatchers.IO) {
            LocalPreferences.updatePrivacyMode(context, enabled)
        }
    }

    fun selectLogRetention(index: Int) {
        val selected = RetentionPeriod.entries[index]
        state.logRetention = selected
        appScope.launch {
            LocalPreferences.updateLogRetention(context, selected)
        }
    }

    fun selectHistoryRetention(index: Int) {
        val selected = RetentionPeriod.entries[index]
        state.historyRetention = selected
        appScope.launch {
            LocalPreferences.updateHistoryRetention(context, selected)
        }
    }

    fun toggleRequireUnlockedDevice(enabled: Boolean) {
        state.requireUnlockedDevice = enabled
        state.requireUnlockedDeviceUpdating = true
        // Use the application-scoped IOScope, not the
        // composition scope: key rotation must complete
        // even if the user leaves the screen/app, or
        // stored secrets could be left inaccessible.
        appScope.launch(Dispatchers.IO) {
            try {
                LocalPreferences.updateRequireUnlockedDevice(context, enabled)
            } catch (e: Exception) {
                AmberLog.e(Amber.TAG, "Error toggling require unlocked device", e)
            } finally {
                // Re-sync the toggle with the persisted setting (reverts on failure)
                state.requireUnlockedDevice = KeystorePolicy.requireUnlockedDevice(context)
                state.requireUnlockedDeviceUpdating = false
            }
        }
    }

    fun togglePin() {
        if (state.setupPin) {
            scope.launch(Dispatchers.IO) {
                val pin = LocalPreferences.loadPinFromEncryptedStorage(context)
                scope.launch(Dispatchers.Main) {
                    navController.navigate("${Route.ConfirmPin.route.split("/")[0]}/$pin")
                }
            }
        } else {
            navController.navigate(Route.SetupPin.route)
        }
    }

    fun save(settingsStore: SettingsStore) {
        scope.launch(Dispatchers.IO) {
            settingsStore.settings = settingsStore.settings.copy(
                useAuth = state.enableBiometrics,
                biometricsTimeType = parseBiometricsTimeType(state.biometricsIndex),
            )
            LocalPreferences.saveSettingsToEncryptedStorage(context, settingsStore.settings)
            scope.launch(Dispatchers.Main) {
                navController.navigateUp()
            }
        }
    }
}

@Composable
private fun SecuritySettings(
    state: SecurityState,
    actions: SecurityActions,
    modifier: Modifier = Modifier,
) {
    val retentionItems = RetentionPeriod.entries.map {
        TitleExplainer(stringResource(it.resourceId))
    }.toImmutableList()

    Column(
        modifier
            .verticalScroll(rememberScrollState()),
    ) {
        SwitchRow(Res.string.enable_biometrics, state.enableBiometrics, actions::toggleBiometrics)

        DescribedSwitchRow(
            title = Res.string.privacy_mode,
            description = Res.string.privacy_mode_description,
            checked = state.privacyMode,
            enabled = true,
            onClick = { actions.setPrivacyMode(!state.privacyMode) },
            onCheckedChange = actions::setPrivacyMode,
        ) {}

        if (!state.privacyMode) {
            RetentionRow(
                name = Res.string.log_retention,
                description = Res.string.log_retention_description,
                items = retentionItems,
                selected = state.logRetention,
                onSelect = actions::selectLogRetention,
            )
            RetentionRow(
                name = Res.string.history_retention,
                description = Res.string.history_retention_description,
                items = retentionItems,
                selected = state.historyRetention,
                onSelect = actions::selectHistoryRetention,
            )
        }

        // GHSA-8844-q5vh-9j8f, L1: opt-in toggle
        DescribedSwitchRow(
            title = Res.string.require_unlocked_device,
            description = Res.string.require_unlocked_device_description,
            checked = state.requireUnlockedDevice,
            enabled = !state.requireUnlockedDeviceUpdating,
            onClick = { actions.toggleRequireUnlockedDevice(!state.requireUnlockedDevice) },
            onCheckedChange = actions::toggleRequireUnlockedDevice,
        ) {
            RequireUnlockedDeviceNotes(updating = state.requireUnlockedDeviceUpdating)
        }

        SwitchRow(Res.string.setup_pin, state.setupPin, actions::togglePin)

        WhenToAskRow(
            selectedIndex = state.biometricsIndex,
            onSelect = {
                state.biometricsIndex = it
            },
        )
    }
}

@Composable
private fun WhenToAskRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val biometricItems =
        persistentListOf(
            TitleExplainer(stringResource(BiometricsTimeType.EVERY_TIME.resourceId)),
            TitleExplainer(stringResource(BiometricsTimeType.ONE_MINUTE.resourceId)),
            TitleExplainer(stringResource(BiometricsTimeType.FIVE_MINUTES.resourceId)),
            TitleExplainer(stringResource(BiometricsTimeType.TEN_MINUTES.resourceId)),
        )
    Box(
        modifier
            .padding(8.dp),
    ) {
        SettingsRow(
            Res.string.when_to_ask,
            Res.string.when_to_ask,
            biometricItems,
            selectedIndex,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun SwitchRow(
    text: StringResource,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clickable {
                onToggle()
            },
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(text),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                onToggle()
            },
        )
    }
}

@Composable
private fun DescribedSwitchRow(
    title: StringResource,
    description: StringResource,
    checked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(enabled = enabled) {
                onClick()
            },
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title))
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
            content()
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ColumnScope.RequireUnlockedDeviceNotes(updating: Boolean) {
    // setUnlockedDeviceRequired needs API 28 (SecureCryptoHelper), so
    // on Android 8 only the software DeviceUnlockPolicy gate applies.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        Text(
            text = stringResource(Res.string.require_unlocked_device_legacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (updating) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(Res.string.require_unlocked_device_updating),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = stringResource(Res.string.require_unlocked_device_do_not_close),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun RetentionRow(
    name: StringResource,
    description: StringResource,
    items: ImmutableList<TitleExplainer>,
    selected: RetentionPeriod,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        SettingsRow(
            name = name,
            description = description,
            selectedItems = items,
            selectedIndex = RetentionPeriod.entries.indexOf(selected),
            onSelect = onSelect,
        )
    }
}
