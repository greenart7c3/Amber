package com.greenart7c3.nostrsigner.ui.navigation

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.R

@Immutable
sealed class Route(
    @param:StringRes val titleResId: Int?,
    val route: String,
    @param:DrawableRes val icon: Int,
) {
    data object TorSettings : Route(
        titleResId = R.string.connect_via_tor_short,
        route = "TorSettings",
        icon = R.drawable.incoming_request,
    )

    data object Login : Route(
        titleResId = null,
        route = "login",
        icon = R.drawable.incoming_request,
    )

    data object IncomingRequest : Route(
        titleResId = R.string.incoming_request,
        route = "IncomingRequest",
        icon = R.drawable.incoming_request,
    )

    data object Applications : Route(
        route = "Applications",
        titleResId = R.string.applications,
        icon = R.drawable.applications,
    )

    data object Settings : Route(
        titleResId = R.string.settings,
        route = "Settings",
        icon = R.drawable.settings,
    )

    data object Permission : Route(
        titleResId = R.string.permissions,
        route = "Permission/{packageName}",
        icon = R.drawable.settings,
    )

    data object AccountBackup : Route(
        titleResId = R.string.account_backup,
        route = "AccountBackup",
        icon = R.drawable.settings,
    )

    data object ExportAllAccounts : Route(
        titleResId = R.string.export_all_accounts_title,
        route = "ExportAllAccounts",
        icon = R.drawable.settings,
    )

    data object Logs : Route(
        titleResId = R.string.logs,
        route = "Logs",
        icon = R.drawable.settings,
    )

    data object QrCode : Route(
        titleResId = R.string.qrCode,
        route = "qrcode/{content}",
        icon = R.drawable.settings,
    )

    data object ActiveRelays : Route(
        titleResId = R.string.relays,
        route = "ActiveRelays",
        icon = R.drawable.relays,
    )

    data object Language : Route(
        titleResId = R.string.language,
        route = "Language",
        icon = R.drawable.settings,
    )

    data object DefaultRelays : Route(
        titleResId = R.string.default_relays,
        route = "DefaultRelays",
        icon = R.drawable.settings,
    )

    data object SignPolicy : Route(
        titleResId = R.string.sign_policy,
        route = "SignPolicy",
        icon = R.drawable.settings,
    )

    data object Security : Route(
        titleResId = R.string.security,
        route = "Security",
        icon = R.drawable.settings,
    )

    data object Accounts : Route(
        titleResId = R.string.accounts,
        route = "Accounts",
        icon = R.drawable.settings,
    )

    data object NewApplication : Route(
        titleResId = R.string.add_a_new_application,
        route = "NewApplication",
        icon = R.drawable.settings,
    )

    data object NewNsecBunker : Route(
        titleResId = R.string.add_a_nsecbunker,
        route = "NewNsecBunker",
        icon = R.drawable.settings,
    )

    data object NSecBunkerCreated : Route(
        titleResId = R.string.add_a_nsecbunker,
        route = "NewNsecBunkerCreated/{key}",
        icon = R.drawable.settings,
    )

    data object Activity : Route(
        titleResId = R.string.activity_title,
        route = "Activity/{key}",
        icon = R.drawable.settings,
    )

    data object RelayLogScreen : Route(
        titleResId = R.string.logs,
        route = "RelayLogScreen/{url}",
        icon = R.drawable.settings,
    )

    data object EditConfiguration : Route(
        titleResId = R.string.edit_configuration,
        route = "EditConfiguration/{key}",
        icon = R.drawable.settings,
    )

    data object SetupPin : Route(
        titleResId = R.string.setup_pin,
        route = "SetupPin",
        icon = R.drawable.settings,
    )

    data object ConfirmPin : Route(
        titleResId = R.string.confirm_pin,
        route = "ConfirmPin/{pin}",
        icon = R.drawable.settings,
    )

    data object SeeDetails : Route(
        titleResId = R.string.incoming_request,
        route = "SeeDetails",
        icon = R.drawable.settings,
    )

    data object RelaysScreen : Route(
        titleResId = R.string.relays,
        route = "RelaysScreen",
        icon = R.drawable.settings,
    )

    data object DefaultProfileRelaysScreen : Route(
        titleResId = R.string.default_profile_relays,
        route = "DefaultProfileRelaysScreen",
        icon = R.drawable.settings,
    )

    data object EditProfile : Route(
        titleResId = R.string.edit_profile,
        route = "EditProfile/{key}",
        icon = R.drawable.settings,
    )

    data object Feedback : Route(
        titleResId = R.string.give_us_feedback,
        route = "Feedback",
        icon = R.drawable.settings,
    )

    data object Activities : Route(
        titleResId = R.string.activities,
        route = "Activities",
        icon = R.drawable.incoming_request,
    )

    data object CrashReport : Route(
        titleResId = R.string.crashreport_found,
        route = "CrashReport",
        icon = R.drawable.settings,
    )

    data object TranslationReport : Route(
        titleResId = R.string.translation_report_title,
        route = "TranslationReport",
        icon = R.drawable.settings,
    )

    data object AuthWhitelist : Route(
        titleResId = R.string.auth_whitelist,
        route = "AuthWhitelist",
        icon = R.drawable.settings,
    )

    data object UpdateSettings : Route(
        titleResId = R.string.update_settings,
        route = "UpdateSettings",
        icon = R.drawable.settings,
    )

    data object CloudBackup : Route(
        titleResId = R.string.cloud_backup_title,
        route = "CloudBackup",
        icon = R.drawable.settings,
    )

    data object ServiceSettings : Route(
        titleResId = R.string.service_settings,
        route = "ServiceSettings",
        icon = R.drawable.settings,
    )

    data object ApplicationsBackup : Route(
        titleResId = R.string.applications_backup,
        route = "ApplicationsBackup",
        icon = R.drawable.settings,
    )
}

/**
 * Resolves the route title against the supplied context so it always reflects the
 * current locale. Returns an empty string when the route has no title.
 */
fun Route.title(context: Context): String = titleResId?.let { context.getString(it) } ?: ""

val routes = listOf(
    Route.IncomingRequest,
    Route.Applications,
    Route.Settings,
    Route.Permission,
    Route.AccountBackup,
    Route.ExportAllAccounts,
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
    Route.TorSettings,
    Route.EditProfile,
    Route.Feedback,
    Route.Activities,
    Route.CrashReport,
    Route.TranslationReport,
    Route.UpdateSettings,
    Route.CloudBackup,
    Route.ServiceSettings,
    Route.ApplicationsBackup,
)
