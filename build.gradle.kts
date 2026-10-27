import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.gradle.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
}

tasks.register<Copy>("installGitHook") {
    description = "Installs the pre-commit and pre-push hooks from git-hooks/."
    from(File(rootProject.rootDir, "git-hooks/pre-commit"))
    from(File(rootProject.rootDir, "git-hooks/pre-push"))
    into(File(rootProject.rootDir, ".git/hooks"))
    filePermissions {
        unix(0b111_111_111)
    }
}

// Non-private declarations nothing references and unused catch parameters,
// across every module's sources (tools/unused-code): detekt sees one file at a
// time, so it can only report private code.
val unusedCodeTool by configurations.creating

dependencies {
    unusedCodeTool(project(":unused-code"))
}

val unusedCodeModules = listOf("app", "common", "desktop", "tools/unused-code")

// Identifiers the JNI side calls by name (ArtiLogCallback.onLogLine).
val unusedCodeTokenDirs = listOf("tools/arti/src")

val unusedCode = tasks.register<JavaExec>("unusedCode") {
    group = "verification"
    description = "Fails on non-private declarations nothing references and on unused catch parameters."
    classpath = unusedCodeTool
    mainClass.set("com.greenart7c3.nostrsigner.tools.unused.MainKt")
    val root = rootDir
    args(listOf(root.absolutePath) + unusedCodeModules + "--" + unusedCodeTokenDirs)
    inputs.files(
        (unusedCodeModules + unusedCodeTokenDirs).map { dir ->
            fileTree(dir) {
                include("**/*.kt", "**/*.xml", "**/*.pro", "**/*.kts", "**/*.rs")
                exclude("**/build/**")
            }
        },
    ).withPropertyName("sources").withPathSensitivity(PathSensitivity.RELATIVE)
    val marker = layout.buildDirectory.file("unused-code/ok")
    outputs.file(marker).withPropertyName("marker")
    doLast {
        marker.get().asFile.writeText("")
    }
}

// Unused code (config/detekt/detekt.yml) in every flavor's sources and tests.
// These are detekt's type-resolving tasks: the plain `detekt` task can't
// resolve types and misses unused variables, properties and functions.
tasks.register("detektAll") {
    group = "verification"
    description = "Checks the app, desktop and common sources for unused code."
    dependsOn(
        ":app:detektFreeDebug",
        ":app:detektOfflineDebug",
        ":app:detektFreeDebugUnitTest",
        ":app:detektOfflineDebugUnitTest",
        ":app:detektFreeDebugAndroidTest",
        ":common:detektMain",
        ":common:detektTest",
        ":desktop:detektMain",
        ":desktop:detektTest",
        ":unused-code:detektMain",
        ":unused-code:detektTest",
    )
    dependsOn(unusedCode)
}

project(":app") {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("installGitHook"))
    }
}

subprojects {
    // `src` covers every source set, including the app's flavor directories.
    plugins.withId("dev.detekt") {
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            source.setFrom("src")
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            // Deprecations, needless null checks, unused expressions and the like
            // fail the build; suppress one at its site when it is deliberate.
            allWarningsAsErrors.set(true)
            val buildDirPath = project.layout.buildDirectory.get().asFile.absolutePath
            if (project.findProperty("composeCompilerReports") == "true") {
                freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                        buildDirPath + "/compose_compiler",
                )
            }
            if (project.findProperty("composeCompilerMetrics") == "true") {
                freeCompilerArgs.addAll(
                    "-P",
                    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                        buildDirPath + "/compose_compiler",
                )
            }
        }
    }
}
