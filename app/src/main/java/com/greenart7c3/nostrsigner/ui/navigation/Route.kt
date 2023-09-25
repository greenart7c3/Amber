package com.greenart7c3.nostrsigner.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
sealed class Route(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Home : Route(
        route = "Home",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Default.Home
    )

    data object Permissions : Route(
        route = "Permissions",
        icon = Icons.Outlined.ViewList,
        selectedIcon = Icons.Default.ViewList
    )

    data object Settings : Route(
        route = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings
    )

    data object Permission : Route(
        route = "Permission/{packageName}",
        icon = Icons.Outlined.ViewList,
        selectedIcon = Icons.Default.ViewList
    )
}
