package com.greenart7c3.nostrsigner

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import kotlinx.coroutines.flow.MutableStateFlow

fun Intent.isLaunchFromHistory(): Boolean = this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    private val intents = MutableStateFlow<List<IntentData>>(listOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()

        setContent {
            if (intent.isLaunchFromHistory()) {
                Log.d("isLaunchFromHistory", "Cleared intent history")
                intent = Intent()
            }

            val packageName = callingPackage
            val appName = if (packageName != null) {
                val info = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                applicationContext.packageManager.getApplicationLabel(info).toString()
            } else {
                null
            }

            NostrSignerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val accountStateViewModel: AccountStateViewModel = viewModel {
                        AccountStateViewModel(intent.getStringExtra("current_user"))
                    }
                    AccountScreen(accountStateViewModel, intent, packageName, appName, intents)
                }
            }
        }
    }

    override fun onResume() {
        val notifications = EventNotificationConsumer(applicationContext).notificationManager().activeNotifications
        notifications.forEach {
            IntentUtils.bunkerRequests[it.id.toString()]?.let {
                val contentIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    data = Uri.parse("nostrsigner:")
                }
                contentIntent.putExtra("bunker", it.toJson())
                contentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                val intentData = IntentUtils.getIntentData(contentIntent, packageName)
                if (intentData != null) {
                    if (intents.value.none { item -> item.id == intentData.id }) {
                        intents.value += listOf(intentData)
                    }
                }
            }
        }
        super.onResume()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        val intentData = IntentUtils.getIntentData(intent, callingPackage) ?: return

        if (intents.value.none { item -> item.id == intentData.id }) {
            intents.value += listOf(intentData)
        }
        intents.value = intents.value.map {
            it.copy()
        }.toMutableList()
    }

    override fun onDestroy() {
        intents.value = emptyList()

        super.onDestroy()
    }
}
