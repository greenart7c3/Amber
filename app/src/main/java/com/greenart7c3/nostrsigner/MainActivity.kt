package com.greenart7c3.nostrsigner

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.ui.AccountScreen
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.theme.NostrSignerTheme
import java.net.URLDecoder

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var event: IntentData? = null
            if (intent.data != null) {
                val data = URLDecoder.decode(intent?.data?.toString()?.replace("+", "%2b") ?: "", "utf-8")
                event = IntentData(data, intent.getStringExtra("name") ?: "")
            }

            NostrSignerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val accountStateViewModel: AccountStateViewModel = viewModel {
                        AccountStateViewModel()
                    }
                    AccountScreen(accountStateViewModel, event)
                }
            }
        }
    }
}
