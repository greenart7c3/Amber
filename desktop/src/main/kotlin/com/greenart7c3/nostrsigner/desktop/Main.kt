package com.greenart7c3.nostrsigner.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Amber Bunker") {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        Surface {
            Text("Amber Bunker Desktop")
        }
    }
}
