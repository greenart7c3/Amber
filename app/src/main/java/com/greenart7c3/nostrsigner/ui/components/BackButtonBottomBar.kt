package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.greenart7c3.nostrsigner.R

@Composable
fun BackButtonAppBar(
    destinationRoute: String,
    localBackButtonTitle: String,
    onPressed: () -> Unit,
) {
    BottomAppBar {
        IconRow(
            center = true,
            title = if (destinationRoute.startsWith("login")) {
                stringResource(R.string.go_back)
            } else if (destinationRoute.startsWith("NewNsecBunkerCreated/")) {
                stringResource(R.string.back_to, localBackButtonTitle)
            } else {
                if (destinationRoute == "NewNsecBunker") {
                    stringResource(R.string.back_to, stringResource(R.string.add_a_new_application))
                } else {
                    stringResource(R.string.back_to, localBackButtonTitle)
                }
            },
            icon = ImageVector.vectorResource(R.drawable.back),
            onClick = onPressed,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
