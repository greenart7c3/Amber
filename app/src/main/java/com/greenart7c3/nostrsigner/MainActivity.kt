package com.greenart7c3.nostrsigner

import android.content.Intent
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

        if (intent.isLaunchFromHistory()) {
            Log.d("isLaunchFromHistory", "Cleared intent history")
            intent = Intent()
        }

        setContent {
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
