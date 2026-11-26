package com.greenart7c3.nostrsigner.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
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
            .filterNot { it.relativePath().endsWith("ViewModel.kt") }
            .filter { file -> file.imports.any { it.name.startsWith("com.greenart7c3.nostrsigner.database") } || databaseOrDaoCallEvenWithoutImport.containsMatchIn(file.text) }
            .map { it.relativePath() }
            .toSet()

        assertRatchet("ui/ files must load data through a ViewModel or service, not a Room DAO:", actual, databaseInUi)
    }

    @Test
    fun `Context amber usage can only shrink`() {
        val unimportedAmberExtensionNotInAHyphenatedName = Regex("""(?<![\w-])amber(?![\w-])""")
        val containerCastWithoutTheExtension = Regex("""\bas\??\s+(com\.greenart7c3\.nostrsigner\.)?Amber\b""")
        val actual = appMain
            .filter { it.relativePath() != "Amber.kt" }
            .filter { file ->
                file.imports.any { it.name == "com.greenart7c3.nostrsigner.amber" } ||
                    containerCastWithoutTheExtension.containsMatchIn(file.text) ||
                    (
                        file.packagee?.name == "com.greenart7c3.nostrsigner" &&
                            unimportedAmberExtensionNotInAHyphenatedName.containsMatchIn(file.text.lines().filterNot { it.startsWith("import ") }.joinToString("\n"))
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
    fun `every application write evicts the permission cache`() {
        val skipping = writeInterfaces()
            .flatMap { (writeDao, cachingDao) ->
                writeDao.functions()
                    .filterNot { it.name.endsWith("Raw") || it.isSelect() }
                    .filterNot { function ->
                        cachingDao.functions().any { it.signature() == function.signature() && it.evictsApplication() }
                    }
                    .map { "${writeDao.name}.${it.name}" }
            }
            .toSet()

        assertRatchet(
            "Each write of ApplicationWriteDao, PermissionWriteDao and PermissionMaintenanceDao must be overridden in its Caching " +
                "wrapper and evict the application it changes (cache.invalidateApp or cache.invalidateAll), or the permission " +
                "cache keeps serving the old grants and expiry:",
            skipping,
            writesThatKeepTheCache,
        )
    }

    @Test
    fun `application writes go through the caching DAOs`() {
        val writeDaos = writeInterfaces()
        val passThroughWrites = writeDaos
            .flatMap { (writeDao, cachingDao) ->
                writeDao.functions()
                    .filter { it.name.endsWith("Raw") && !it.isSelect() }
                    .filterNot { function -> cachingDao.functions().any { it.signature() == function.signature() && it.evictsApplication() } }
                    .map { it.name }
            }
            .toSet()
        assertTrue("no pass-through writes were found: did the write DAOs change?", passThroughWrites.isNotEmpty())

        val cacheLayer = writeDaos.flatMap { (writeDao, cachingDao) -> listOf(writeDao.text, cachingDao.text) } +
            scope.classes().filter { it.name == "ApplicationDaos" && it.containingFile.path in appSourcePaths }.map { it.text } +
            scope.classes()
                .filter { it.name == "AccountDatabases" && it.containingFile.path in appSourcePaths }
                .flatMap { databases -> databases.functions().filter { it.name == "daos" }.map { it.text } }
        assertTrue("the cache layer was not found", cacheLayer.size == writeDaos.size * 2 + 2)

        val passThroughCall = Regex("""::(${passThroughWrites.joinToString("|")})\b|\b(${passThroughWrites.joinToString("|")})\(""")
        val actual = appSources
            .flatMap { file ->
                val rest = file.textWithout(cacheLayer)
                passThroughCall.findAll(rest).map { "${file.relativePath()}: ${it.groupValues[1].ifEmpty { it.groupValues[2] }}" } +
                    uncachedDaos.findAll(rest).map { "${file.relativePath()}: ${it.value.trim('.', '(', ')', ':')}" }
            }
            .toSet()

        assertRatchet(
            "Write applications and permissions through AccountRepository.daos(npub), whose Caching DAOs evict the permission " +
                "cache. The uncached DAOs (AppDatabase.applicationWriteDao() etc., ApplicationDaos.of) and the *Raw writes the " +
                "wrappers pass through leave stale grants and expiry in it:",
            actual,
            passThroughCallers,
        )
    }

    @Test
    fun `only the write DAOs write the application tables`() {
        val writeDaos = writeInterfaces().map { it.first }
        val writeDaoTexts = writeDaos.map { it.text }
        val sqlOutside = appSources
            .flatMap { file ->
                val rest = file.textWithout(writeDaoTexts + file.migrations())
                applicationTableWrite.findAll(rest).map { "${file.relativePath()}: ${it.value.trim()}" }
            }
        val roomWritesOutside = (scope.interfaces() + scope.classes())
            .filter { it.containingFile.path in appSourcePaths && it.hasAnnotationWithName("Dao") }
            .filterNot { dao -> writeDaos.any { it.name == dao.name } }
            .flatMap { dao ->
                dao.functions()
                    .filter { function -> function.annotations.any { it.name in roomWrites } && applicationEntity.containsMatchIn(function.text) }
                    .map { "${dao.containingFile.relativePath()}: ${dao.name}.${it.name}" }
            }
        val violations = sqlOutside + roomWritesOutside

        assertTrue(
            "Only ApplicationWriteDao, PermissionWriteDao and PermissionMaintenanceDao (whose Caching wrappers evict the permission " +
                "cache) and the Room migrations may write the application and applicationPermission tables:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `raw SQLite access stays in the migrations`() {
        val actual = appSources
            .flatMap { file ->
                val rest = file.textWithout(file.migrations()).lines().filterNot { it.startsWith("import ") }.joinToString("\n")
                rawSqlite.findAll(rest).map { "${file.relativePath()}: ${it.value}" }
            }
            .toSet()

        assertRatchet(
            "A raw SQLite handle (openHelper, execSQL, @RawQuery, clearAllTables, …) writes past the Room DAOs and the permission " +
                "cache; outside a Room migration, add a DAO method instead:",
            actual,
            rawSqliteUsers,
        )
    }

    private val appSources by lazy {
        scope.files.filter { file ->
            val path = file.path.replace('\\', '/')
            file.moduleName == "app" && "/src/test/" !in path && "/src/androidTest/" !in path
        }
    }

    private val appSourcePaths by lazy { appSources.map { it.path }.toSet() }

    private fun writeInterfaces() = writeDaoNames.map { name ->
        val writeDao = scope.interfaces().singleOrNull { it.name == name && it.containingFile.path in appSourcePaths }
        val cachingDao = scope.classes().singleOrNull { it.name == "Caching$name" && it.containingFile.path in appSourcePaths }
        assertTrue("$name and Caching$name were not found", writeDao != null && cachingDao != null)
        writeDao!! to cachingDao!!
    }

    private fun KoFunctionDeclaration.isSelect(): Boolean = annotations.any { it.name == "Query" && selectQuery.containsMatchIn(it.text) }

    private fun KoFunctionDeclaration.signature(): String = name + parameters.joinToString(prefix = "(", postfix = ")") { it.type.text }

    private fun KoFunctionDeclaration.evictsApplication(): Boolean = evictsApplication.containsMatchIn(kDoc?.let { text.replace(it.text, "") } ?: text)

    private fun KoFileDeclaration.migrations(): List<String> = migration.findAll(text).map { start ->
        val open = text.indexOf('{', start.range.last)
        var depth = 0
        var end = open
        do {
            when (text[end]) {
                '{' -> depth++
                '}' -> depth--
            }
            end++
        } while (depth > 0 && end < text.length)
        text.substring(start.range.first, end)
    }.toList()

    private fun KoFileDeclaration.textWithout(excluded: List<String>): String = excluded.fold(text) { text, declaration -> text.replace(declaration, "") }

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
        isComposeRuntimeCompileOnlyForImmutable() -> true
        startsWith("androidx.") -> false
        startsWith("java.awt") || startsWith("javax.swing") || startsWith("javafx.") -> false
        startsWith("com.greenart7c3.nostrsigner.") -> startsWith("com.greenart7c3.nostrsigner.common.")
        else -> true
    }

    private fun String.isComposeRuntimeCompileOnlyForImmutable(): Boolean = startsWith("androidx.compose.runtime.")

    private fun String.isComposeMultiplatformRuntimeOnBothTargets(): Boolean = startsWith("androidx.compose.runtime.")

    private fun String.isAllowedInCommonsui(): Boolean = when {
        startsWith("android.") -> false
        isComposeMultiplatformRuntimeOnBothTargets() -> true
        startsWith("androidx.") -> false
        startsWith("java.awt") || startsWith("javax.swing") || startsWith("javafx.") -> false
        startsWith("com.greenart7c3.nostrsigner.") -> startsWith("com.greenart7c3.nostrsigner.commonsui.")
        else -> true
    }

    private companion object {
        val databaseOrDaoCallEvenWithoutImport = Regex("""\.(\w*D|d)aos?\(|\bget(History|Log)?Database\(""")

        val databaseInUi = emptySet<String>()

        val contextAmberUsers = setOf(
            "MainActivity.kt",
            "service/BackupApplicationsWorker.kt",
            "service/BootReceiver.kt",
            "service/ClearLogsWorker.kt",
            "service/ConnectivityService.kt",
            "service/KillSwitchReceiver.kt",
            "service/ReconnectReceiver.kt",
            "service/StopServiceReceiver.kt",
            "service/TorRestartReceiver.kt",
            "service/UpdateCheckWorker.kt",
            "SignerActivity.kt",
            "SignerProvider.kt",
        )

        val composeUiInService = emptySet<String>()

        val writeDaoNames = listOf("ApplicationWriteDao", "PermissionWriteDao", "PermissionMaintenanceDao")

        val evictsApplication = Regex("""\bcache(\.|::)invalidate(App|All)\b""")

        val selectQuery = Regex("""@Query\(\s*"+\s*SELECT\b""", RegexOption.IGNORE_CASE)

        val uncachedDaos = Regex(
            """(?<!fun )\b(applicationWriteDao|permissionWriteDao|permissionMaintenanceDao)\(\)|::(applicationWriteDao|permissionWriteDao|permissionMaintenanceDao)\b|""" +
                """\bApplicationDaos(\.Companion)?(\.|::)of\b|\bApplicationDaos\.Companion\.of\b""",
        )

        val migration = Regex("""object\s*:\s*Migration\(""")

        val applicationTableWrite = Regex(
            """\b(UPDATE(\s+OR\s+\w+)?|DELETE\s+FROM|INSERT(\s+OR\s+\w+)?\s+INTO|REPLACE\s+INTO)\s+(main\.)?[`"\[]?(application|applicationPermission)\b""",
            RegexOption.IGNORE_CASE,
        )

        val rawSqlite = Regex("""\b(openHelper|execSQL|compileStatement|clearAllTables|RawQuery|SimpleSQLiteQuery|SupportSQLiteDatabase)\b""")

        val rawSqliteUsers = setOf(
            "database/LogRetentionManager.kt: SupportSQLiteDatabase",
            "database/LogRetentionManager.kt: execSQL",
            "database/LogRetentionManager.kt: openHelper",
        )

        val roomWrites = setOf("Insert", "Update", "Delete", "Upsert")

        val applicationEntity = Regex("""\bApplication(Entity|PermissionsEntity|WithPermissions)\b""")

        val writesThatKeepTheCache = setOf("ApplicationWriteDao.updateLastUsed")

        val passThroughCallers = setOf(
            "SecureCryptoHelper.kt: replaceLocalKeyRaw",
            "SecureCryptoHelper.kt: replaceSecretRaw",
        )
    }
}
