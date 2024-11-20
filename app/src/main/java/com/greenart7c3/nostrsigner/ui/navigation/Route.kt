package com.greenart7c3.nostrsigner.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R

@Immutable
sealed class Route(
    val title: String,
    val route: String,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    data object IncomingRequest : Route(
        title = NostrSigner.getInstance().getString(R.string.incoming_request),
        route = "IncomingRequest",
        icon = R.drawable.incoming_request,
        selectedIcon = R.drawable.incoming_request,
    )

    data object Applications : Route(
        route = "Applications",
        title = NostrSigner.getInstance().getString(R.string.applications),
        icon = R.drawable.applications,
        selectedIcon = R.drawable.applications,
    )

    data object Settings : Route(
        title = NostrSigner.getInstance().getString(R.string.settings),
        route = "Settings",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object Permission : Route(
        title = NostrSigner.getInstance().getString(R.string.permissions),
        route = "Permission/{packageName}",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object AccountBackup : Route(
        title = NostrSigner.getInstance().getString(R.string.account_backup),
        route = "AccountBackup",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object Logs : Route(
        title = NostrSigner.getInstance().getString(R.string.logs),
        route = "Logs",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object ActiveRelays : Route(
        title = NostrSigner.getInstance().getString(R.string.relays),
        route = "ActiveRelays",
        icon = R.drawable.relays,
        selectedIcon = R.drawable.relays,
    )

    data object Language : Route(
        title = NostrSigner.getInstance().getString(R.string.language),
        route = "Language",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object DefaultRelays : Route(
        title = NostrSigner.getInstance().getString(R.string.default_relays),
        route = "DefaultRelays",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object SignPolicy : Route(
        title = NostrSigner.getInstance().getString(R.string.sign_policy),
        route = "SignPolicy",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object Security : Route(
        title = NostrSigner.getInstance().getString(R.string.security),
        route = "Security",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object Accounts : Route(
        title = NostrSigner.getInstance().getString(R.string.accounts),
        route = "Accounts",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object NewApplication : Route(
        title = NostrSigner.getInstance().getString(R.string.add_a_new_application),
        route = "NewApplication",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object NewNsecBunker : Route(
        title = NostrSigner.getInstance().getString(R.string.add_a_nsecbunker),
        route = "NewNsecBunker",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object NSecBunkerCreated : Route(
        title = NostrSigner.getInstance().getString(R.string.add_a_nsecbunker),
        route = "NewNsecBunkerCreated/{key}",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object Activity : Route(
        title = NostrSigner.getInstance().getString(R.string.activity_title),
        route = "Activity/{key}",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object RelayLogScreen : Route(
        title = NostrSigner.getInstance().getString(R.string.logs),
        route = "RelayLogScreen/{url}",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object EditConfiguration : Route(
        title = NostrSigner.getInstance().getString(R.string.edit_configuration),
        route = "EditConfiguration/{key}",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object SetupPin : Route(
        title = NostrSigner.getInstance().getString(R.string.setup_pin),
        route = "SetupPin",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object ConfirmPin : Route(
        title = NostrSigner.getInstance().getString(R.string.confirm_pin),
        route = "ConfirmPin/{pin}",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object SeeDetails : Route(
        title = NostrSigner.getInstance().getString(R.string.incoming_request),
        route = "SeeDetails",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object RelaysScreen : Route(
        title = NostrSigner.getInstance().getString(R.string.relays),
        route = "RelaysScreen",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
    )

    data object DefaultProfileRelaysScreen : Route(
        title = NostrSigner.getInstance().getString(R.string.default_profile_relays),
        route = "DefaultProfileRelaysScreen",
        icon = R.drawable.settings,
        selectedIcon = R.drawable.settings,
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
    Route.SetupPin,
    Route.ConfirmPin,
    Route.SeeDetails,
    Route.RelaysScreen,
    Route.DefaultProfileRelaysScreen,
)
