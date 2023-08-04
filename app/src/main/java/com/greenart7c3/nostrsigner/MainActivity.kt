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
                var data = URLDecoder.decode(intent?.data?.toString()?.replace("+", "%2b") ?: "", "utf-8")
                val split = data.split(";")
                var name = ""
                if (split.isNotEmpty()) {
                    if (split.last().lowercase().contains("name=")) {
                        name = split.last().replace("name=", "")
                        val newList = split.toList().dropLast(1)
                        data = newList.joinToString("")
                    }
                }
                event = IntentData(data, name)
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
