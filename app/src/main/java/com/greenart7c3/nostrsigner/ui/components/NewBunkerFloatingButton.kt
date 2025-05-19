package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R

@Composable
fun NewBunkerFloatingButton(
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
    ) {
        FloatingActionButton(
            modifier = Modifier
                .padding(end = 8.dp),
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
            onClick = onClick,
            shape = RoundedCornerShape(24),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.connect_app),
                tint = Color.Black,
            )
        }
    }
}
