package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.add_a_new_application
import com.greenart7c3.nostrsigner.commonsui.back_to
import com.greenart7c3.nostrsigner.commonsui.go_back
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import org.jetbrains.compose.resources.stringResource

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
                stringResource(Res.string.go_back)
            } else if (destinationRoute.startsWith("NewNsecBunkerCreated/")) {
                stringResource(Res.string.back_to, localBackButtonTitle)
            } else {
                if (destinationRoute == "NewNsecBunker") {
                    stringResource(Res.string.back_to, stringResource(Res.string.add_a_new_application))
                } else {
                    stringResource(Res.string.back_to, localBackButtonTitle)
                }
            },
            icon = ImageVector.vectorResource(R.drawable.back),
            onClick = onPressed,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@ThemePreviews
@Composable
fun BackButtonAppBarPreview() {
    AmberPreview {
        BackButtonAppBar(
            destinationRoute = "Settings",
            localBackButtonTitle = "Applications",
            onPressed = {},
        )
    }
}
