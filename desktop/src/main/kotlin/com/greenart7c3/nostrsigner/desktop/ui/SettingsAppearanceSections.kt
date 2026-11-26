package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_theme_dark
import com.greenart7c3.nostrsigner.commonsui.d_theme_light
import com.greenart7c3.nostrsigner.commonsui.d_theme_system
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ThemeSection(darkTheme: Boolean?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            null to stringResource(Res.string.d_theme_system),
            false to stringResource(Res.string.d_theme_light),
            true to stringResource(Res.string.d_theme_dark),
        ).forEach { (value, label) ->
            FilterChip(
                selected = darkTheme == value,
                onClick = { SettingsStore.update { it.copy(darkTheme = value) } },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun LanguagePicker() {
    val current by Strings.currentLanguage.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val currentName = Strings.supportedLanguages.firstOrNull { it.first == current }?.second ?: current

    Box {
        AmberOutlinedButton(text = currentName, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Strings.supportedLanguages.forEach { (tag, name) ->
                DropdownMenuItem(
                    text = { Text(name + if (tag == current) "  ✓" else "") },
                    onClick = {
                        Strings.setLanguage(tag)
                        expanded = false
                    },
                )
            }
        }
    }
}
