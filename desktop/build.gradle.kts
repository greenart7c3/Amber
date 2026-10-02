import java.io.File
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

// Tests must never see the developer machine's real desktop state: AppDirs.dataDir
// resolves from XDG_DATA_HOME *before* user.home, so a real ~/.local/share/amber
// with a passphrase-locked master.key.enc freezes into the suite and fails every
// DesktopKeyStore operation with LockedException. Scrub XDG_DATA_HOME from the
// worker env, point user.home at a pristine dir under build/, and wipe it per run.
tasks.test {
    useJUnit()
    val testHome = layout.buildDirectory.dir("desktop-test-home").get().asFile
    // Gradle cannot remove env vars for workers (null becomes the literal string
    // "null", which AppDirs happily uses as a directory name), so point
    // XDG_DATA_HOME at the pristine home instead.
    environment("XDG_DATA_HOME", File(testHome, "xdg-data").absolutePath)
    systemProperty("user.home", testHome.absolutePath)
    doFirst {
        testHome.deleteRecursively()
        testHome.mkdirs()
    }
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
    // Cross-platform system tray. Unlike AWT's SystemTray it speaks the
    // freedesktop StatusNotifierItem / AppIndicator protocol, so a tray icon
    // shows on Wayland compositors (Hyprland, Sway, GNOME) via waybar etc.
    implementation(libs.dorkbox.systemtray)
    // Windows registry access for start-on-boot (HKCU\...\Run).
    implementation(libs.jna.platform)
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
        // Lets Main.kt set the X11 WM_CLASS so Linux docks match amber.desktop.
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Amber"
            packageVersion = "6.2.3"
            description = "Amber - Nostr event signer"
            vendor = "greenart7c3"
            copyright = "© greenart7c3. Distributed under the MIT license."

            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                menuGroup = "Network"
            }
            windows {
                iconFile.set(project.file("icons/amber.ico"))
                // Start Menu entry + desktop shortcut; without these the MSI
                // only adds an uninstall entry and Amber.exe is hard to find.
                menu = true
                menuGroup = "Amber"
                shortcut = true
            }
            macOS {
                bundleID = "com.greenart7c3.nostrsigner"
                iconFile.set(project.file("icons/amber.icns"))
                infoPlist {
                    // Claim nostrconnect:// so LaunchServices routes those
                    // links to Amber (delivered via Desktop.setOpenURIHandler).
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>Nostr Connect</string>
                                <key>CFBundleURLSchemes</key>
                                <array>
                                    <string>nostrconnect</string>
                                </array>
                            </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }

        buildTypes.release.proguard {
            // Reflection-heavy stack (Jackson, OkHttp, JNI) — ship unshrunk.
            isEnabled.set(false)
        }
    }
}
