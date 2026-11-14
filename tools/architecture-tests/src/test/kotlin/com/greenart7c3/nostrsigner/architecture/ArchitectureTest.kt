package com.greenart7c3.nostrsigner.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layering rules the compiler cannot check. Every allow-list here is a ratchet:
 * an entry has to disappear when the code it excuses is fixed (the test fails
 * when a listed file stops violating the rule), and nothing may be added to it
 * without deleting the offending code.
 *
 * The target layering is described in docs/architecture.md.
 */
class ArchitectureTest {
    private val scope = Konsist.scopeFromProject()

    private val appMain = scope.files.filter { it.moduleName == "app" && it.isProduction() }

    private fun KoFileDeclaration.isProduction(): Boolean = path.replace('\\', '/').contains("/src/main/")

    private fun KoFileDeclaration.relativePath(): String {
        val normalized = path.replace('\\', '/')
        return normalized.substringAfter("/com/greenart7c3/nostrsigner/", normalized)
    }

    private fun assertRatchet(
        rule: String,
        actual: Set<String>,
        allowed: Set<String>,
    ) {
        val added = (actual - allowed).sorted()
        val fixed = (allowed - actual).sorted()
        assertTrue(
            "$rule\n" +
                "New violations (fix the code or move it to an allowed layer, do not extend the list):\n" +
                added.joinToString("\n"),
            added.isEmpty(),
        )
        assertTrue(
            "$rule\n" +
                "These allow-list entries no longer violate the rule, delete them:\n" +
                fixed.joinToString("\n"),
            fixed.isEmpty(),
        )
    }

    @Test
    fun `common stays platform free`() {
        val common = scope.files.filter { it.moduleName == "common" }
        assertTrue("no :common sources were found", common.isNotEmpty())

        assertTrue(
            ":common may not use platform or app APIs:\n" + common
                .flatMap { file ->
                    file.imports.map { it.name }.filterNot { it.isAllowedInCommon() }.map { "${file.name}: $it" }
                }
                .joinToString("\n"),
            common.all { file -> file.imports.all { it.name.isAllowedInCommon() } },
        )
    }

    @Test
    fun `commonsui stays platform free`() {
        val commonsui = scope.files.filter { it.moduleName == "commonsui" }
        assertTrue("no :commonsui sources were found", commonsui.isNotEmpty())

        assertTrue(
            ":commonsui may not use Android platform, AWT or app APIs:\n" + commonsui
                .flatMap { file ->
                    file.imports.map { it.name }.filterNot { it.isAllowedInCommonsui() }.map { "${file.name}: $it" }
                }
                .joinToString("\n"),
            commonsui.all { file -> file.imports.all { it.name.isAllowedInCommonsui() } },
        )
    }

    @Test
    fun `the ui package does not talk to the database`() {
        val actual = appMain
            .filter { it.relativePath().startsWith("ui/") }
            .filter { file -> file.imports.any { it.name.startsWith("com.greenart7c3.nostrsigner.database") } }
            .map { it.relativePath() }
            .toSet()

        assertRatchet("ui/ files must load data through a ViewModel or service, not a Room DAO:", actual, databaseInUi)
    }

    @Test
    fun `Amber instance usage can only shrink`() {
        val actual = appMain
            .filter { it.text.contains("Amber.instance") }
            .map { it.relativePath() }
            .toSet()

        assertRatchet(
            "Amber.instance is the service locator this project is replacing: take the dependency as a parameter instead.",
            actual,
            amberInstanceUsers,
        )
    }

    @Test
    fun `service and database do not render UI`() {
        val actual = appMain
            .filter { file -> file.relativePath().startsWith("service/") || file.relativePath().startsWith("database/") }
            .filter { file -> file.imports.any { it.name.isComposeUiAndNotText() } }
            .map { it.relativePath() }
            .toSet()

        assertRatchet(
            "service/ and database/ must not depend on Compose UI (androidx.compose.ui.text is data, not UI):",
            actual,
            composeUiInService,
        )
    }

    @Test
    fun `desktop core does not depend on the desktop ui`() {
        val violations = scope.files
            .filter { it.moduleName == "desktop" && it.path.replace('\\', '/').contains("/desktop/core/") }
            .flatMap { file ->
                file.imports
                    .map { it.name }
                    .filter { it.startsWith("com.greenart7c3.nostrsigner.desktop.ui") }
                    .map { "${file.relativePath()}: $it" }
            }

        assertTrue(
            "desktop/.../core/ must not import desktop...ui:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `ViewModels are named ViewModel`() {
        val misnamed = scope.classes()
            .filter { koClass -> koClass.hasParentWithName("ViewModel") || koClass.hasParentWithName("AndroidViewModel") }
            .filterNot { it.name.endsWith("ViewModel") }
            .map { "${it.containingFile.moduleName}/${it.containingFile.relativePath()}: ${it.name}" }

        assertTrue("ViewModel subclasses must end in ViewModel:\n" + misnamed.joinToString("\n"), misnamed.isEmpty())
    }

    private fun String.isComposeUiAndNotText(): Boolean = startsWith("androidx.compose.ui.") && !startsWith("androidx.compose.ui.text.")

    private fun String.isAllowedInCommon(): Boolean = when {
        startsWith("android.") -> false
        // @Immutable for the compose compiler, compileOnly: no Compose UI.
        startsWith("androidx.compose.runtime.") -> true
        startsWith("androidx.") -> false
        startsWith("java.awt") || startsWith("javax.swing") || startsWith("javafx.") -> false
        startsWith("com.greenart7c3.nostrsigner.") -> startsWith("com.greenart7c3.nostrsigner.common.")
        else -> true
    }

    private fun String.isAllowedInCommonsui(): Boolean = when {
        startsWith("android.") -> false
        // Compose Multiplatform resolves to androidx.compose on both targets.
        startsWith("androidx.compose.runtime.") -> true
        startsWith("androidx.") -> false
        startsWith("java.awt") || startsWith("javax.swing") || startsWith("javafx.") -> false
        startsWith("com.greenart7c3.nostrsigner.") -> startsWith("com.greenart7c3.nostrsigner.commonsui.")
        else -> true
    }

    private companion object {
        // May only shrink: see the class comment.
        val databaseInUi = setOf(
            "ui/ActivitiesScreen.kt",
            "ui/components/ActivityStatsCard.kt",
            "ui/components/BunkerMultiEventHomeScreen.kt",
            "ui/components/BunkerSingleEventHomeScreen.kt",
            "ui/components/IntentMultiEventHomeScreen.kt",
            "ui/components/IntentSingleEventHomeScreen.kt",
            "ui/EditConfigurationScreen.kt",
            "ui/EditPermission.kt",
            "ui/NewNsecBunkerScreen.kt",
        )

        val amberInstanceUsers = setOf(
            "DataStoreAccess.kt",
            "LocalPreferences.kt",
            "MainActivity.kt",
            "SecureCryptoHelper.kt",
            "SignerActivity.kt",
            "SignerProviderQuery.kt",
            "database/HistoryDao.kt",
            "database/LogDao.kt",
            "database/LogRetentionManager.kt",
            "models/Account.kt",
            "relays/AmberRelayStat.kt",
            "relays/NostrClientLoggerListener.kt",
            "service/AccountExportService.kt",
            "service/AmberUtils.kt",
            "service/ApplicationBackup.kt",
            "service/BootReceiver.kt",
            "service/BunkerRequestUtils.kt",
            "service/ClearLogsWorker.kt",
            "service/ConnectivityService.kt",
            "service/EventNotificationConsumer.kt",
            "service/IntentUtils.kt",
            "service/KillSwitchReceiver.kt",
            "service/LocalBunkerRelayServer.kt",
            "service/NostrConnectUtils.kt",
            "service/NotificationSubscription.kt",
            "service/ProfileSubscription.kt",
            "service/ReconnectReceiver.kt",
            "service/ReportSender.kt",
            "service/StopServiceReceiver.kt",
            "service/TorRestartReceiver.kt",
            "service/TrustScoreService.kt",
            "service/UpdateCheckWorker.kt",
            "service/WebDavService.kt",
            "service/ZapstoreUpdater.kt",
            "service/crashreports/DisplayCrashMessages.kt",
            "ui/AccountScreen.kt",
            "ui/AccountStateViewModel.kt",
            "ui/ActivitiesScreen.kt",
            "ui/ApplicationsBackupScreen.kt",
            "ui/ApplicationsScreen.kt",
            "ui/AuthWhitelistScreen.kt",
            "ui/CrashReportScreen.kt",
            "ui/DefaultProfileRelaysScreen.kt",
            "ui/EditConfigurationScreen.kt",
            "ui/EditPermission.kt",
            "ui/FeedbackScreen.kt",
            "ui/IncomingRequestScreen.kt",
            "ui/LanguageScreen.kt",
            "ui/LoginScreen.kt",
            "ui/LogsScreen.kt",
            "ui/MainScreen.kt",
            "ui/NewApplicationScreen.kt",
            "ui/NewNsecBunkerScreen.kt",
            "ui/RelaysScreen.kt",
            "ui/SecurityScreen.kt",
            "ui/ServiceSettingsScreen.kt",
            "ui/SettingsScreen.kt",
            "ui/SetupPinScreen.kt",
            "ui/TranslationReportScreen.kt",
            "ui/UpdateSettingsScreen.kt",
            "ui/actions/AccountBackupScreen.kt",
            "ui/actions/ActivityScreen.kt",
            "ui/actions/CloudBackupScreen.kt",
            "ui/actions/ConnectOrbotDialog.kt",
            "ui/actions/EditRelaysDialog.kt",
            "ui/actions/ExportAllAccountsScreen.kt",
            "ui/actions/LogoutButton.kt",
            "ui/components/ActivityStatsCard.kt",
            "ui/components/AmberTopAppBar.kt",
            "ui/components/BiometricAuthScreen.kt",
            "ui/components/BunkerConnectRequestScreen.kt",
            "ui/components/BunkerMultiEventHomeScreen.kt",
            "ui/components/BunkerSingleEventHomeScreen.kt",
            "ui/components/EncryptedTagArraySection.kt",
            "ui/components/EventData.kt",
            "ui/components/EventDetailModal.kt",
            "ui/components/IntentMultiEventHomeScreen.kt",
            "ui/components/IntentSingleEventHomeScreen.kt",
            "ui/components/KillSwitchDialog.kt",
            "ui/components/ProfileSubscriptionEffect.kt",
            "ui/components/RandomPinInput.kt",
            "ui/components/SeedWordsPage.kt",
            "ui/theme/Previews.kt",
        )

        val composeUiInService = setOf(
            "service/IntentUtils.kt",
            "service/ReportSender.kt",
        )
    }
}
