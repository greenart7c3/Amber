package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.ConnectivityService
import com.greenart7c3.nostrsigner.service.NotificationDataSource
import com.greenart7c3.nostrsigner.service.PushNotificationUtils
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import com.vitorpamplona.ammolite.relays.RelayPool
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NotificationTypeScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            if (!LocalPreferences.isNotificationTypeConfigured()) {
                AmberListenerSingleton.accountStateViewModel?.toast(
                    context.getString(R.string.notification_settings),
                    context.getString(R.string.please_configure_your_notification_settings_before_continuing),
                )
            }
        }
    }

    val scope = rememberCoroutineScope()
    var notificationItemsIndex by remember {
        mutableIntStateOf(NostrSigner.getInstance().settings.notificationType.screenCode)
    }
    val notificationItems =
        persistentListOf(
            TitleExplainer(
                stringResource(NotificationType.PUSH.resourceId),
                stringResource(R.string.push_notifications_explainer),
            ),
            TitleExplainer(
                stringResource(NotificationType.DIRECT.resourceId),
                stringResource(R.string.direct_notifications_explainer),
            ),
        )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        Column(
            Modifier.weight(1f),
        ) {
            Box(
                Modifier
                    .padding(8.dp),
            ) {
                SettingsRow(
                    R.string.notification_type,
                    R.string.select_the_type_of_notification_you_want_to_receive,
                    notificationItems,
                    notificationItemsIndex,
                ) {
                    notificationItemsIndex = it
                }
            }

            @Suppress("KotlinConstantConditions")
            if (BuildConfig.FLAVOR == "free") {
                Box(
                    Modifier
                        .padding(8.dp),
                ) {
                    PushNotificationSettingsRow()
                }
            }
        }
        AmberButton(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(
                        notificationType = parseNotificationType(notificationItemsIndex),
                    )
                    LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
                    PushNotificationUtils.hasInit = false
                    PushNotificationUtils.init(LocalPreferences.allSavedAccounts(context))
                    if (notificationItemsIndex == 0) {
                        NotificationDataSource.stopSync()
                        RelayPool.disconnect()
                    } else {
                        NostrSigner.getInstance().checkForNewRelays()
                        NotificationDataSource.start()
                    }
                    NostrSigner.getInstance().applicationContext.startForegroundService(
                        Intent(
                            NostrSigner.getInstance().applicationContext,
                            ConnectivityService::class.java,
                        ),
                    )
                    scope.launch(Dispatchers.Main) {
                        onDone()
                    }
                }
            },
            content = {
                Text(
                    text = stringResource(R.string.save),
                )
            },
        )
    }
}
