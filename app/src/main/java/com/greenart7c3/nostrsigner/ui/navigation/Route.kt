package com.greenart7c3.nostrsigner.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Person
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
    data object IncomingRequest : Route(
        title = NostrSigner.getInstance().getString(R.string.incoming_request),
        route = "IncomingRequest",
        icon = Icons.AutoMirrored.Outlined.List,
        selectedIcon = Icons.AutoMirrored.Filled.List,
    )

    data object Applications : Route(
        route = "Applications",
        title = NostrSigner.getInstance().getString(R.string.applications),
        icon = Icons.Outlined.Apps,
        selectedIcon = Icons.Default.Apps,
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
        title = NostrSigner.getInstance().getString(R.string.account_backup),
        route = "AccountBackup",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Logs : Route(
        title = NostrSigner.getInstance().getString(R.string.logs),
        route = "Logs",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object ActiveRelays : Route(
        title = NostrSigner.getInstance().getString(R.string.relays),
        route = "ActiveRelays",
        icon = Icons.Outlined.Hub,
        selectedIcon = Icons.Default.Hub,
    )

    data object Language : Route(
        title = NostrSigner.getInstance().getString(R.string.language),
        route = "Language",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object NotificationType : Route(
        title = NostrSigner.getInstance().getString(R.string.notification_type),
        route = "NotificationType",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object DefaultRelays : Route(
        title = NostrSigner.getInstance().getString(R.string.default_relays),
        route = "DefaultRelays",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object SignPolicy : Route(
        title = NostrSigner.getInstance().getString(R.string.sign_policy),
        route = "SignPolicy",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Security : Route(
        title = NostrSigner.getInstance().getString(R.string.security),
        route = "Security",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Accounts : Route(
        title = NostrSigner.getInstance().getString(R.string.accounts),
        route = "Accounts",
        icon = Icons.Outlined.Person,
        selectedIcon = Icons.Default.Person,
    )

    data object NewApplication : Route(
        title = NostrSigner.getInstance().getString(R.string.add_a_new_application),
        route = "NewApplication",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object NewNsecBunker : Route(
        title = NostrSigner.getInstance().getString(R.string.add_a_nsecbunker),
        route = "NewNsecBunker",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object NSecBunkerCreated : Route(
        title = NostrSigner.getInstance().getString(R.string.add_a_nsecbunker),
        route = "NewNsecBunkerCreated/{key}",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object Activity : Route(
        title = NostrSigner.getInstance().getString(R.string.activity_title),
        route = "Activity/{key}",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object RelayLogScreen : Route(
        title = NostrSigner.getInstance().getString(R.string.logs),
        route = "RelayLogScreen/{url}",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )

    data object EditConfiguration : Route(
        title = NostrSigner.getInstance().getString(R.string.edit_configuration),
        route = "EditConfiguration/{key}",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Default.Settings,
    )
}

val routes = listOf(
    Route.IncomingRequest,
    Route.Applications,
    Route.Settings,
    Route.Permission,
    Route.AccountBackup,
    Route.Logs,
    Route.ActiveRelays,
    Route.Language,
    Route.NotificationType,
    Route.DefaultRelays,
    Route.SignPolicy,
    Route.Security,
    Route.Accounts,
    Route.NewApplication,
    Route.NewNsecBunker,
    Route.NSecBunkerCreated,
    Route.Activity,
    Route.RelayLogScreen,
    Route.EditConfiguration,
)
