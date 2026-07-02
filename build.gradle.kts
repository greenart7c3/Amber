import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.kotlin.ksp) version libs.versions.ksp.get() apply false
    alias(libs.plugins.gradle.ktlint) version libs.versions.ktlint.get() apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
}

tasks.register<Copy>("installGitHook") {
    from(File(rootProject.rootDir, "git-hooks/pre-commit"))
    from(File(rootProject.rootDir, "git-hooks/pre-push"))
    into(File(rootProject.rootDir, ".git/hooks"))
    filePermissions {
        unix(0b111_111_111)
    }
}

project(":app") {
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(rootProject.tasks.named("installGitHook"))
    }
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
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
