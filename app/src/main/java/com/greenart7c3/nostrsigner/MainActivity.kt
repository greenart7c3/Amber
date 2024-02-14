package com.greenart7c3.nostrsigner

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme

class MainViewModel : ViewModel() {
    var intents = IntentLiveData()
}

class IntentLiveData : MutableLiveData<MutableList<IntentData>>(mutableListOf())

class MainActivity : AppCompatActivity() {
    private val mainViewModel = MainViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        installSplashScreen()

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
                    AccountScreen(accountStateViewModel, intent, packageName, appName, mainViewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        val intentData = IntentUtils.getIntentData(intent, callingPackage) ?: return
        val list = mutableListOf(intentData)
        if (mainViewModel.intents.value == null) {
            mainViewModel.intents.value = mutableListOf()
        }
        mainViewModel.intents.value?.forEach {
            if (list.none { item -> item.id == it.id }) {
                list.add(it)
            }
        }

        list.forEach {
            val list2 = mainViewModel.intents.value!!
            if (list2.none { item -> item.id == it.id }) {
                list2.add(it)
            }
        }
        mainViewModel.intents.value = mainViewModel.intents.value?.map {
            it
        }?.toMutableList()
    }

    override fun onDestroy() {
        mainViewModel.intents.value?.clear()
        super.onDestroy()
    }
}
