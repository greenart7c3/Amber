import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.jetbrainsComposeCompiler)
    alias(libs.plugins.gradle.ktlint) version libs.versions.ktlint.get()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.quartz.jvm)
    // Native secp256k1 bindings for the JVM (Schnorr signatures + ECDH).
    implementation(libs.secp256k1.jni.jvm)

    // OS credential stores (macOS Keychain, Windows Credential Manager,
    // freedesktop Secret Service) for the keystore password.
    implementation(libs.java.keyring)
    runtimeOnly(libs.slf4j.nop)

    // Argon2id for the optional passphrase lock.
    implementation(libs.bouncycastle)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.collections.immutable)

    // QR code generation (pure Java)
    implementation(libs.core)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.greenart7c3.nostrsigner.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Amber"
            packageVersion = "6.2.3"
            description = "Amber - Nostr event signer"
            vendor = "greenart7c3"
            copyright = "© greenart7c3. Distributed under the MIT license."

            linux {
                iconFile.set(rootProject.file("assets/android-icon-hires.png"))
                menuGroup = "Network"
            }
            macOS {
                bundleID = "com.greenart7c3.nostrsigner"
            }
        }

        buildTypes.release.proguard {
            // Reflection-heavy stack (Jackson, OkHttp, JNI) — ship unshrunk.
            isEnabled.set(false)
        }
    }
}
