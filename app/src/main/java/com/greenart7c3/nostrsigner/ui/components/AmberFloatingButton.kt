package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.navigation.Route

@Composable
fun AmberFloatingButton(
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
) {
    @Suppress("KotlinConstantConditions")
    if (navBackStackEntry?.destination?.route == Route.Applications.route && BuildConfig.FLAVOR != "offline") {
        NewBunkerFloatingButton(
            onClick = {
                navController.navigate(Route.NewApplication.route)
            },
        )
    } else if (navBackStackEntry?.destination?.route == Route.ActiveRelays.route) {
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
                onClick = {
                    navController.navigate(Route.DefaultRelays.route)
                },
                shape = RoundedCornerShape(24),
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.connect_app),
                    tint = Color.Black,
                )
            }
        }
    }
}
