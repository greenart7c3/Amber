package com.greenart7c3.nostrsigner.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account_backup
import com.greenart7c3.nostrsigner.commonsui.accounts
import com.greenart7c3.nostrsigner.commonsui.activities
import com.greenart7c3.nostrsigner.commonsui.activity_title
import com.greenart7c3.nostrsigner.commonsui.add_a_new_application
import com.greenart7c3.nostrsigner.commonsui.add_a_nsecbunker
import com.greenart7c3.nostrsigner.commonsui.applications
import com.greenart7c3.nostrsigner.commonsui.applications_backup
import com.greenart7c3.nostrsigner.commonsui.auth_whitelist
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_title
import com.greenart7c3.nostrsigner.commonsui.confirm_pin
import com.greenart7c3.nostrsigner.commonsui.connect_via_tor_short
import com.greenart7c3.nostrsigner.commonsui.crashreport_found
import com.greenart7c3.nostrsigner.commonsui.default_profile_relays
import com.greenart7c3.nostrsigner.commonsui.default_relays
import com.greenart7c3.nostrsigner.commonsui.edit_configuration
import com.greenart7c3.nostrsigner.commonsui.edit_profile
import com.greenart7c3.nostrsigner.commonsui.export_all_accounts_title
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.give_us_feedback
import com.greenart7c3.nostrsigner.commonsui.incoming_request
import com.greenart7c3.nostrsigner.commonsui.language
import com.greenart7c3.nostrsigner.commonsui.logs
import com.greenart7c3.nostrsigner.commonsui.permissions
import com.greenart7c3.nostrsigner.commonsui.qrCode
import com.greenart7c3.nostrsigner.commonsui.relays
import com.greenart7c3.nostrsigner.commonsui.security
import com.greenart7c3.nostrsigner.commonsui.service_settings
import com.greenart7c3.nostrsigner.commonsui.settings
import com.greenart7c3.nostrsigner.commonsui.setup_pin
import com.greenart7c3.nostrsigner.commonsui.sign_policy
import com.greenart7c3.nostrsigner.commonsui.translation_report_title
import com.greenart7c3.nostrsigner.commonsui.update_settings
import org.jetbrains.compose.resources.StringResource

@Immutable
sealed class Route(
    val titleResId: StringResource?,
    val route: String,
    @param:DrawableRes val icon: Int,
) {
    data object TorSettings : Route(
        titleResId = Res.string.connect_via_tor_short,
        route = "TorSettings",
        icon = R.drawable.incoming_request,
    )

    data object Login : Route(
        titleResId = null,
        route = "login",
        icon = R.drawable.incoming_request,
    )

    data object IncomingRequest : Route(
        titleResId = Res.string.incoming_request,
        route = "IncomingRequest",
        icon = R.drawable.incoming_request,
    )

    data object Applications : Route(
        route = "Applications",
        titleResId = Res.string.applications,
        icon = R.drawable.applications,
    )

    data object Settings : Route(
        titleResId = Res.string.settings,
        route = "Settings",
        icon = R.drawable.settings,
    )

    data object Permission : Route(
        titleResId = Res.string.permissions,
        route = "Permission/{packageName}",
        icon = R.drawable.settings,
    )

    data object AccountBackup : Route(
        titleResId = Res.string.account_backup,
        route = "AccountBackup",
        icon = R.drawable.settings,
    )

    data object ExportAllAccounts : Route(
        titleResId = Res.string.export_all_accounts_title,
        route = "ExportAllAccounts",
        icon = R.drawable.settings,
    )

    data object Logs : Route(
        titleResId = Res.string.logs,
        route = "Logs",
        icon = R.drawable.settings,
    )

    data object QrCode : Route(
        titleResId = Res.string.qrCode,
        route = "qrcode/{content}",
        icon = R.drawable.settings,
    )

    data object ActiveRelays : Route(
        titleResId = Res.string.relays,
        route = "ActiveRelays",
        icon = R.drawable.relays,
    )

    data object Language : Route(
        titleResId = Res.string.language,
        route = "Language",
        icon = R.drawable.settings,
    )

    data object DefaultRelays : Route(
        titleResId = Res.string.default_relays,
        route = "DefaultRelays",
        icon = R.drawable.settings,
    )

    data object SignPolicy : Route(
        titleResId = Res.string.sign_policy,
        route = "SignPolicy",
        icon = R.drawable.settings,
    )

    data object Security : Route(
        titleResId = Res.string.security,
        route = "Security",
        icon = R.drawable.settings,
    )

    data object Accounts : Route(
        titleResId = Res.string.accounts,
        route = "Accounts",
        icon = R.drawable.settings,
    )

    data object NewApplication : Route(
        titleResId = Res.string.add_a_new_application,
        route = "NewApplication",
        icon = R.drawable.settings,
    )

    data object NewNsecBunker : Route(
        titleResId = Res.string.add_a_nsecbunker,
        route = "NewNsecBunker",
        icon = R.drawable.settings,
    )

    data object NSecBunkerCreated : Route(
        titleResId = Res.string.add_a_nsecbunker,
        route = "NewNsecBunkerCreated/{key}",
        icon = R.drawable.settings,
    )

    data object Activity : Route(
        titleResId = Res.string.activity_title,
        route = "Activity/{key}",
        icon = R.drawable.settings,
    )

    data object RelayLogScreen : Route(
        titleResId = Res.string.logs,
        route = "RelayLogScreen/{url}",
        icon = R.drawable.settings,
    )

    data object EditConfiguration : Route(
        titleResId = Res.string.edit_configuration,
        route = "EditConfiguration/{key}",
        icon = R.drawable.settings,
    )

    data object SetupPin : Route(
        titleResId = Res.string.setup_pin,
        route = "SetupPin",
        icon = R.drawable.settings,
    )

    data object ConfirmPin : Route(
        titleResId = Res.string.confirm_pin,
        route = "ConfirmPin/{pin}",
        icon = R.drawable.settings,
    )

    data object SeeDetails : Route(
        titleResId = Res.string.incoming_request,
        route = "SeeDetails",
        icon = R.drawable.settings,
    )

    data object RelaysScreen : Route(
        titleResId = Res.string.relays,
        route = "RelaysScreen",
        icon = R.drawable.settings,
    )

    data object DefaultProfileRelaysScreen : Route(
        titleResId = Res.string.default_profile_relays,
        route = "DefaultProfileRelaysScreen",
        icon = R.drawable.settings,
    )

    data object EditProfile : Route(
        titleResId = Res.string.edit_profile,
        route = "EditProfile/{key}",
        icon = R.drawable.settings,
    )

    data object Feedback : Route(
        titleResId = Res.string.give_us_feedback,
        route = "Feedback",
        icon = R.drawable.settings,
    )

    data object Activities : Route(
        titleResId = Res.string.activities,
        route = "Activities",
        icon = R.drawable.incoming_request,
    )

    data object CrashReport : Route(
        titleResId = Res.string.crashreport_found,
        route = "CrashReport",
        icon = R.drawable.settings,
    )

    data object TranslationReport : Route(
        titleResId = Res.string.translation_report_title,
        route = "TranslationReport",
        icon = R.drawable.settings,
    )

    data object AuthWhitelist : Route(
        titleResId = Res.string.auth_whitelist,
        route = "AuthWhitelist",
        icon = R.drawable.settings,
    )

    data object UpdateSettings : Route(
        titleResId = Res.string.update_settings,
        route = "UpdateSettings",
        icon = R.drawable.settings,
    )

    data object CloudBackup : Route(
        titleResId = Res.string.cloud_backup_title,
        route = "CloudBackup",
        icon = R.drawable.settings,
    )

    data object ServiceSettings : Route(
        titleResId = Res.string.service_settings,
        route = "ServiceSettings",
        icon = R.drawable.settings,
    )

    data object ApplicationsBackup : Route(
        titleResId = Res.string.applications_backup,
        route = "ApplicationsBackup",
        icon = R.drawable.settings,
    )
}

/**
 * Resolves the route title against the supplied context so it always reflects the
 * current locale. Returns an empty string when the route has no title.
 */
fun Route.title(): String = titleResId?.let { getStringBlocking(it) } ?: ""

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

/**
 * The only routes another app may open through the exported activities'
 * "route" extra: the ones Amber's own notifications send. Anything else would
 * let any app jump straight to an internal screen, with arguments of its
 * choosing (e.g. QrCode/{content}, NewNsecBunkerCreated/{key}).
 */
private val EXTERNAL_ROUTES = setOf(Route.IncomingRequest.route, Route.UpdateSettings.route)

fun externalRoute(route: String?): String? = route?.takeIf { it in EXTERNAL_ROUTES }
