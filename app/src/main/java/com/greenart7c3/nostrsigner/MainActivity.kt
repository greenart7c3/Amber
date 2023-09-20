package com.greenart7c3.nostrsigner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        setContent {
            val packageName = callingPackage

            NostrSignerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val accountStateViewModel: AccountStateViewModel = viewModel {
                        AccountStateViewModel()
                    }
                    AccountScreen(accountStateViewModel, intent, packageName)
                }
            }
        }
    }
}
