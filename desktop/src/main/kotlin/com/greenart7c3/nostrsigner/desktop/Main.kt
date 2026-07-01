package com.greenart7c3.nostrsigner.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.greenart7c3.nostrsigner.desktop.ui.BunkerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Amber Bunker") {
        BunkerApp(appScope)
    }
}
