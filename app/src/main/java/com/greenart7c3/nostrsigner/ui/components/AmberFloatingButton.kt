package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.NavBackStackEntryWrapper
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.navigation.Route

@Composable
fun AmberFloatingButton(
    navController: NavHostControllerWrapper,
    navBackStackEntry: NavBackStackEntryWrapper,
) {
    if (navBackStackEntry.navBackStackEntry?.destination?.route == Route.Applications.route && !BuildFlavorChecker.isOfflineFlavor()) {
        NewBunkerFloatingButton(
            onClick = {
                navController.navController.navigate(Route.NewApplication.route)
            },
        )
    } else if (navBackStackEntry.navBackStackEntry?.destination?.route == Route.ActiveRelays.route) {
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            ExtendedFloatingActionButton(
                text = {
                    Text(stringResource(R.string.edit_relays))
                },
                icon = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.settings),
                        contentDescription = stringResource(R.string.edit_relays),
                        tint = Color.Black,
                    )
                },
                modifier = Modifier
                    .padding(end = 8.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    focusedElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    pressedElevation = 0.dp,
                ),
                onClick = {
                    navController.navController.navigate(Route.DefaultRelays.route)
                },
                shape = RoundedCornerShape(24),
            )
        }
    }
}
