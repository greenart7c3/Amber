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
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.material.icons.extended)

    implementation(libs.quartz.jvm)
    // Quartz ships jackson-databind but no longer the Kotlin module.
    implementation(libs.jackson.module.kotlin)
    // Native secp256k1 bindings for the JVM (Schnorr signatures + ECDH).
    implementation(libs.secp256k1.jni.jvm)

    // OS credential stores (macOS Keychain, Windows Credential Manager,
    // freedesktop Secret Service) for the keystore password.
    implementation(libs.java.keyring)
    // Cross-platform system tray. Unlike AWT's SystemTray it speaks the
    // freedesktop StatusNotifierItem / AppIndicator protocol, so a tray icon
    // shows on Wayland compositors (Hyprland, Sway, GNOME) via waybar etc.
    implementation(libs.dorkbox.systemtray)
    // Session D-Bus for actionable Linux notifications (Approve/Reject
    // buttons): the daemon sends ActionInvoked only to the connection that
    // posted the notification. Already on the classpath via the two above.
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.native.unixsocket)
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

// `run` is the debug build: BuildVariant keeps its data dir, keyring entry and
// nostrconnect socket apart from a packaged (release) install on the same
// machine. Packaged distributions and runDistributable stay release.
// -PdesktopUriHandler also lets it claim nostrconnect:// links (Linux).
// Compose registers these after this script is evaluated, hence the lazy match.
val debugRunTasks = setOf("run", "hotRun", "hotRunAsync", "hotDev", "hotDevAsync")
tasks.withType<JavaExec>().matching { it.name in debugRunTasks }.configureEach {
    systemProperty("amber.debug", "true")
    if (providers.gradleProperty("desktopUriHandler").isPresent) {
        systemProperty("amber.uriHandler", "true")
    }
}

// Release builds pass the tag's version (-PdesktopVersion=X.Y.Z).
val desktopVersion = providers.gradleProperty("desktopVersion").getOrElse("6.2.3")

compose.desktop {
    application {
        mainClass = "com.greenart7c3.nostrsigner.desktop.MainKt"
        // Lets Main.kt set the X11 WM_CLASS so Linux docks match the desktop entry.
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        // Keep decrypted keys out of files other tools can read: no jcmd/jmap
        // attach (a same-user heap dump in one command), no heap dump on OOM
        // and no core dump on a JVM crash (hs_err logs are still written).
        jvmArgs += listOf("-XX:+DisableAttachMechanism", "-XX:-HeapDumpOnOutOfMemoryError", "-XX:-CreateCoredumpOnCrash")
        // The version crash reports carry (BuildVariant.version).
        jvmArgs += "-Damber.version=$desktopVersion"

        nativeDistributions {
            // Built-in Tor: the prebuilt Arti library of each OS/arch
            // (tools/arti/build.sh); Compose ships the current OS's one.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Amber"
            // dbus-java authenticates to the session bus with UnixSystem
            // (actionable Linux notifications, Secret Service keyring).
            modules("jdk.security.auth")
            packageVersion = desktopVersion
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
