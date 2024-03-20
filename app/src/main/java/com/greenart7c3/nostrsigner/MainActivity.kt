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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.PushNotificationUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

fun Intent.isLaunchFromHistory(): Boolean = this.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // TODO: Inform user that that your app will show notifications.
        } else {
            // TODO: Inform user that that your app will not show notifications.
        }
    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }
        }
    }

    private val intents = MutableStateFlow<List<IntentData>>(listOf())

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()
        askNotificationPermission()

        FirebaseMessaging.getInstance().token.addOnCompleteListener(
            OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("token", "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                GlobalScope.launch(Dispatchers.IO) {
                    PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
                }
            }
        )

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
