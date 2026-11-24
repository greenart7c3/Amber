// Amber's own detekt rules (the "amber" rule set, config/detekt/amber.yml),
// loaded into every other module's detekt tasks by the root build script.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.gradle.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
}

dependencies {
    // detekt supplies its API and the Kotlin compiler's PSI when it loads the rules.
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.api)
    testImplementation(libs.junit)
}
