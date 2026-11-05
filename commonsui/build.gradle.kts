// What the Android app (:app) and the desktop app (:desktop) show, from one
// source: every string, as Compose Multiplatform resources
// (src/commonMain/composeResources), and the code describing a request's
// permissions with them (Permission). Both apps read the strings through the
// generated Res.string.<name> accessors: stringResource() in composables,
// getString() / getStringBlocking() elsewhere.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.gradle.ktlint)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.greenart7c3.nostrsigner.commonsui"
        compileSdk = 37
        minSdk = 26
        // Compose resources ship as Android assets.
        androidResources {
            enable = true
        }
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.multiplatform.resources)
            implementation(libs.compose.multiplatform.runtime)
            // runBlocking in getStringBlocking.
            implementation(libs.kotlinx.coroutines.core)
            // Permission's JSON form (both targets are JVMs).
            implementation(libs.jackson.module.kotlin)
        }
        jvmTest.dependencies {
            implementation(libs.junit)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.greenart7c3.nostrsigner.commonsui"
    generateResClass = always
}
