package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@Composable
fun AppTitle(appName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = appName,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}
