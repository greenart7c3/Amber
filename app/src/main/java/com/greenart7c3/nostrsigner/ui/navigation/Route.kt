package com.greenart7c3.nostrsigner.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R

@Immutable
sealed class Route(
    val title: String,
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    data object Home : Route(
        title = NostrSigner.getInstance().getString(R.string.home),
        route = "Home",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Default.Home,
    )

    data object Permissions : Route(
        route = "Permissions",
        title = NostrSigner.getInstance().getString(R.string.permissions),
        icon = Icons.AutoMirrored.Outlined.ViewList,
        selectedIcon = Icons.AutoMirrored.Default.ViewList,
    )

    data object Settings : Route(
        title = NostrSigner.getInstance().getString(R.string.settings),
        route = "Settings",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Permission : Route(
        title = NostrSigner.getInstance().getString(R.string.permissions),
        route = "Permission/{packageName}",
        icon = Icons.AutoMirrored.Outlined.ViewList,
        selectedIcon = Icons.AutoMirrored.Default.ViewList,
    )

    data object AccountBackup : Route(
        title = "",
        route = "AccountBackup",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Logs : Route(
        title = "",
        route = "Logs",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object ActiveRelays : Route(
        title = "",
        route = "ActiveRelays",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Language : Route(
        title = "",
        route = "Language",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object NotificationType : Route(
        title = "",
        route = "NotificationType",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object DefaultRelays : Route(
        title = "",
        route = "DefaultRelays",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object SignPolicy : Route(
        title = "",
        route = "SignPolicy",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Security : Route(
        title = "",
        route = "Security",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )
}
