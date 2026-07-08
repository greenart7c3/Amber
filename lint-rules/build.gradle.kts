plugins {
    // No version: AGP's built-in Kotlin already puts the plugin on the build classpath
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.gradle.ktlint) version libs.versions.ktlint.get()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.lint.api)
}

tasks.jar {
    manifest {
        attributes("Lint-Registry-v2" to "com.greenart7c3.nostrsigner.lint.AmberIssueRegistry")
    }
}
