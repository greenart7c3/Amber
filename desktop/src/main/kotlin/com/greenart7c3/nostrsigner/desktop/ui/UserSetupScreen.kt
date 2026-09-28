package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.greenart7c3.nostrsigner.desktop.core.DedicatedUser
import com.greenart7c3.nostrsigner.desktop.core.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Blocking first-open flow on Linux: asks for the sudo password, creates the
 * dedicated OS user with its launcher + sudoers rule, and re-launches Amber
 * under that user. Returns true when a dedicated-user process has been
 * started (the caller must exit); false when the user closed the window
 * without completing the setup.
 */
fun runUserSetupWindow(): Boolean {
    var switched = false
    application {
        val windowState = rememberWindowState(width = 560.dp, height = 400.dp)
        var password by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var working by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val language by Strings.currentLanguage.collectAsState()

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            visible = true,
            title = "Amber",
            icon = painterResource("icon.png"),
        ) {
            NostrSignerTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(Modifier.widthIn(max = 480.dp).padding(24.dp)) {
                        Text(
                            Strings.get("d_dedicated_user_title", language),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Strings.format("d_dedicated_user_desc", DedicatedUser.userName, language = language),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(Strings.get("d_dedicated_user_password", language)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AmberButton(
                                text = if (working) Strings.get("d_working", language) else Strings.get("d_dedicated_user_set_up", language),
                                enabled = !working && password.isNotEmpty(),
                                onClick = {
                                    working = true
                                    error = null
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            DedicatedUser.setup(password.toCharArray())
                                        }
                                        password = ""
                                        if (!ok) {
                                            error = Strings.get("d_dedicated_user_failed", language)
                                            working = false
                                        } else {
                                            val command = (DedicatedUser.detect() as? DedicatedUser.State.Ready)?.command
                                            if (command != null && DedicatedUser.relaunch(command)) {
                                                switched = true
                                                exitApplication()
                                            } else {
                                                error = Strings.get("d_dedicated_user_failed", language)
                                                working = false
                                            }
                                        }
                                    }
                                },
                            )
                            AmberOutlinedButton(
                                text = Strings.get("cancel", language),
                                enabled = !working,
                                onClick = ::exitApplication,
                            )
                        }
                    }
                }
            }
        }
    }
    return switched
}
