package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.navigation.Route

@Composable
fun RelaysScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AmberButton(
            text = stringResource(R.string.active_relays),
            onClick = {
                navController.navigate(Route.ActiveRelays.route)
            },
        )

        AmberButton(
            text = stringResource(R.string.default_profile_relays),
            onClick = {
                navController.navigate(Route.DefaultProfileRelaysScreen.route)
            },
        )
    }
}
