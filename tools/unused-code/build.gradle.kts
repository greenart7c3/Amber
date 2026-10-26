// Finds code nothing uses, from source, across :app, :desktop and :common:
// non-private declarations no source references, and catch parameters the
// catch block never reads. Run through the root `unusedCode` task.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.gradle.ktlint) version libs.versions.ktlint.get()
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
}

dependencies {
    // Only its parser: files are read as syntax trees, never compiled.
    implementation(libs.kotlin.compiler.embeddable)

    testImplementation(libs.junit)
}
