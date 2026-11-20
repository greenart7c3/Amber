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
            // The layer the rule sends data loading to: a ViewModel may expose what it loaded.
            .filterNot { it.relativePath().endsWith("ViewModel.kt") }
            // A DAO call needs no import when Kotlin infers its types, so the calls count too.
            .filter { file -> file.imports.any { it.name.startsWith("com.greenart7c3.nostrsigner.database") } || daoCall.containsMatchIn(file.text) }
            .map { it.relativePath() }
            .toSet()

        assertRatchet("ui/ files must load data through a ViewModel or service, not a Room DAO:", actual, databaseInUi)
    }

    @Test
    fun `Context amber usage can only shrink`() {
        // Root-package files use the extension without an import (and often without a receiver).
        val rootUse = Regex("""\bamber\b""")
        // The same container without the extension: (context.applicationContext as Amber).
        val castToAmber = Regex("""\bas\??\s+(com\.greenart7c3\.nostrsigner\.)?Amber\b""")
        val actual = appMain
            .filter { it.relativePath() != "Amber.kt" }
            .filter { file ->
                file.imports.any { it.name == "com.greenart7c3.nostrsigner.amber" } ||
                    castToAmber.containsMatchIn(file.text) ||
                    (
                        file.packagee?.name == "com.greenart7c3.nostrsigner" &&
                            rootUse.containsMatchIn(file.text.lines().filterNot { it.startsWith("import ") }.joinToString("\n"))
                        )
            }
            .map { it.relativePath() }
            .toSet()

        assertRatchet(
            "Context.amber (or a cast to Amber) hands out the whole container: take SettingsStore, AccountRepository or RelayClient " +
                "(LocalSettingsStore etc. in composables) instead. Entry points the system constructs are the exception.",
            actual,
            contextAmberUsers,
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
        // Opening a Room database or DAO: AccountRepository.dao/getDatabase/getHistoryDatabase/getLogDatabase.
        val daoCall = Regex("""\.dao\(|\bget(History|Log)?Database\(""")

        // May only shrink: see the class comment. The entries without a database import
        // were added when the rule started counting DAO calls; they were there before.
        val databaseInUi = setOf(
            "ui/actions/EditRelaysDialog.kt",
            "ui/actions/LogoutButton.kt",
            "ui/ApplicationsScreen.kt",
            "ui/components/AmberTopAppBar.kt",
            "ui/components/BunkerConnectRequestScreen.kt",
            "ui/components/BunkerMultiEventHomeScreen.kt",
            "ui/EditConfigurationScreen.kt",
            "ui/EditPermission.kt",
            "ui/LogsScreen.kt",
            "ui/NewNsecBunkerScreen.kt",
        )

        val contextAmberUsers = setOf(
            "LocalPreferences.kt",
            "MainActivity.kt",
            "SecureCryptoHelper.kt",
            "service/BackupApplicationsWorker.kt",
            "service/BootReceiver.kt",
            "service/ClearLogsWorker.kt",
            "service/ConnectivityService.kt",
            "service/IntentUtils.kt",
            "service/KillSwitchReceiver.kt",
            "service/ReconnectReceiver.kt",
            "service/StopServiceReceiver.kt",
            "service/TorRestartReceiver.kt",
            "service/UpdateCheckWorker.kt",
            "SignerActivity.kt",
            "SignerProvider.kt",
            "ui/actions/AccountBackupScreen.kt",
            "ui/actions/EditRelaysDialog.kt",
            "ui/ApplicationsBackupScreen.kt",
            "ui/ApplicationsScreen.kt",
            "ui/components/AmberTopAppBar.kt",
            "ui/components/BunkerMultiEventHomeScreen.kt",
            "ui/components/BunkerSingleEventHomeScreen.kt",
            "ui/components/EventData.kt",
            "ui/components/ProfileSubscriptionEffect.kt",
            "ui/CrashReportScreen.kt",
            "ui/FeedbackScreen.kt",
            "ui/LoginScreen.kt",
            "ui/MainScreen.kt",
            "ui/NewNsecBunkerScreen.kt",
            "ui/RelaysScreen.kt",
            "ui/SettingsScreen.kt",
            "ui/TranslationReportScreen.kt",
            "ui/UpdateSettingsScreen.kt",
        )

        val composeUiInService = setOf(
            "service/IntentUtils.kt",
            "service/ReportSender.kt",
        )
    }
}
