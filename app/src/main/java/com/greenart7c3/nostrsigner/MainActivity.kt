package com.greenart7c3.nostrsigner

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.PushNotificationUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun Intent.isLaunchFromHistory(): Boolean = this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            initNotifications()
        } else {
            if (LocalPreferences.shouldShowRationale() == null) {
                LocalPreferences.updateShoulShowRationale(true)
            }
        }
    }

    private fun initNotifications() {
        runBlocking {
            PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
        }
    }

    private fun askNotificationPermission(onShouldShowRequestPermissionRationale: () -> Unit) {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
            ) {
                val shouldShowRationale = LocalPreferences.shouldShowRationale()
                if (shouldShowRationale == null) {
                    requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                    return
                }

                if (!shouldShowRationale) {
                    return
                }

                onShouldShowRequestPermissionRationale()
            }
        } else {
            requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
        }
    }

    private val intents = MutableStateFlow<List<IntentData>>(listOf())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()

        initNotifications()

        setContent {
            var showDialog by remember { mutableStateOf(false) }
            askNotificationPermission {
                showDialog = true
            }

            if (intent.isLaunchFromHistory()) {
                Log.d("isLaunchFromHistory", "Cleared intent history")
                intent = Intent()
            }

            LocalPreferences.appDatabase = runBlocking {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext)
                }
            }

            val packageName = callingPackage
            val appName = if (packageName != null) {
                val info = applicationContext.packageManager.getApplicationInfo(packageName, 0)
                applicationContext.packageManager.getApplicationLabel(info).toString()
            } else {
                null
            }

            NostrSignerTheme {
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showDialog = false
                        },
                        title = {
                            Text(text = "Permission Needed")
                        },
                        text = {
                            Text(text = "Notifications are needed to use Amber as a nsec bunker.")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDialog = false
                                    requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                                }
                            ) {
                                Text(text = "Allow")
                            }
                        },
                        dismissButton = {
                            Button(
                                onClick = {
                                    showDialog = false
                                    LocalPreferences.updateShoulShowRationale(false)
                                }
                            ) {
                                Text(text = "Deny")
                            }
                        }
                    )
                }

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
