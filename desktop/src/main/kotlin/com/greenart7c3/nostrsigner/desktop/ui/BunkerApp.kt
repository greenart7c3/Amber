package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.data.AccountStore
import com.greenart7c3.nostrsigner.desktop.ui.theme.DesktopTheme
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BunkerApp(scope: CoroutineScope) {
    var account by remember { mutableStateOf<KeyPair?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        account = AccountStore.load()
        loading = false
    }

    DesktopTheme(darkTheme = false) {
        Surface {
            when {
                loading -> Text("Loading...", modifier = Modifier.padding(24.dp))
                account == null -> SetupScreen(
                    onGenerate = { scope.launch { account = AccountStore.generate() } },
                    onImport = { hex -> scope.launch { account = AccountStore.import(hex) } },
                )
                else -> AppShell(account!!, scope)
            }
        }
    }
}
