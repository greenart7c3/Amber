package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ServiceSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var startServiceOnBoot by remember { mutableStateOf(Amber.instance.settings.startServiceOnBoot) }

    Column(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    val newValue = !startServiceOnBoot
                    startServiceOnBoot = newValue
                    scope.launch(Dispatchers.IO) {
                        LocalPreferences.updateStartServiceOnBoot(context, newValue)
                    }
                },
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.start_service_on_boot))
                Text(
                    text = stringResource(R.string.start_service_on_boot_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            Switch(
                checked = startServiceOnBoot,
                onCheckedChange = { enabled ->
                    startServiceOnBoot = enabled
                    scope.launch(Dispatchers.IO) {
                        LocalPreferences.updateStartServiceOnBoot(context, enabled)
                    }
                },
            )
        }
    }
}
