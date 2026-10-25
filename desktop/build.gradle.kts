import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
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

// Skiko's native library ships as plain files next to the app's jars
// instead of through skiko-awt-runtime-<os>-<arch>.jar. Compose 1.12 unpacks
// that jar itself and rewrites what is left of it with the build time as
// every entry's timestamp, so no two builds of the same commit, and none of
// the installers, would match (Compose 1.13 keeps the original times). The
// files are copied byte for byte out of the jar: the native library of this
// OS and arch, its .sha256 and, on Windows, icudtl.dat, exactly what the
// plugin unpacks. Non-jar files keep their names in the app folder, the
// skiko.library.path ($APPDIR) the plugin points skiko at.
object SkikoNatives {
    val runtimeJar = Regex("^skiko-awt-runtime-(.+?)-[0-9.]+\\.jar$")

    fun extract(jars: Set<File>, into: File) {
        into.deleteRecursively()
        into.mkdirs()
        for (jar in jars) {
            val target = runtimeJar.find(jar.name)!!.groupValues[1]
            ZipFile(jar).use { zip ->
                for (entry in zip.entries()) {
                    val name = entry.name.removeSuffix(".sha256")
                    val native = name.substringBeforeLast('.').endsWith("-$target")
                    if (entry.isDirectory || '/' in entry.name || !(native || name == "icudtl.dat")) continue
                    zip.getInputStream(entry).use { File(into, entry.name).outputStream().use(it::copyTo) }
                }
            }
        }
    }
}

val skikoRuntimeJars = configurations.runtimeClasspath.get().filter { SkikoNatives.runtimeJar.matches(it.name) }
val skikoNatives = tasks.register("skikoNatives") {
    val jars = skikoRuntimeJars
    val out = layout.buildDirectory.dir("skiko-natives")
    inputs.files(jars)
    outputs.dir(out)
    doLast { SkikoNatives.extract(jars.files, out.get().asFile) }
}

// On macOS the plugin re-zips every jar it packages (to sign the native
// libraries inside), stamping each entry with the build time; that is all
// that differs between two builds there. Set the timestamps in the ZIP
// headers to a fixed time before the DMG is made (it signs the app ad hoc
// over these jars). Only the header fields change, not the compressed data.
object ZipTimestamps {
    // 1980-02-01 00:00, Gradle's fixed time for reproducible archives.
    private const val DOS_TIME = 0
    private const val DOS_DATE = (2 shl 5) or 1

    fun normalize(jar: File) {
        val zip = jar.readBytes()
        val buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        val end = (zip.size - 22 downTo maxOf(0, zip.size - 22 - 0xFFFF)).firstOrNull { buffer.getInt(it) == 0x06054b50 }
            ?: error("$jar: no end of central directory")
        var entry = buffer.getInt(end + 16)
        repeat(buffer.getShort(end + 10).toInt() and 0xFFFF) {
            check(buffer.getInt(entry) == 0x02014b50) { "$jar: bad central directory" }
            val local = buffer.getInt(entry + 42)
            check(buffer.getInt(local) == 0x04034b50) { "$jar: bad local header" }
            buffer.putShort(entry + 12, DOS_TIME.toShort()).putShort(entry + 14, DOS_DATE.toShort())
            buffer.putShort(local + 10, DOS_TIME.toShort()).putShort(local + 12, DOS_DATE.toShort())
            entry += 46 + (buffer.getShort(entry + 28).toInt() and 0xFFFF) +
                (buffer.getShort(entry + 30).toInt() and 0xFFFF) + (buffer.getShort(entry + 32).toInt() and 0xFFFF)
        }
        jar.writeBytes(zip)
    }
}

if (System.getProperty("os.name").startsWith("Mac")) {
    tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>()
        .matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }
        .configureEach {
            val image = destinationDir
            doLast {
                image.get().asFile.walkTopDown().filter { it.isFile && it.extension == "jar" && it.parentFile.name == "app" }
                    .forEach(ZipTimestamps::normalize)
            }
        }
}

// `run` is the debug build: BuildVariant keeps its data dir, keyring entry and
// nostrconnect socket apart from a packaged (release) install on the same
// machine. Packaged distributions and runDistributable stay release.
// -PdesktopUriHandler also lets it claim nostrconnect:// links (Linux).
// Compose registers these after this script is evaluated, hence the lazy match.
val debugRunTasks = setOf("run", "hotRun", "hotRunAsync", "hotDev", "hotDevAsync")
tasks.withType<JavaExec>().matching { it.name in debugRunTasks }.configureEach {
    systemProperty("amber.debug", "true")
    // The packaged classpath has no skiko-awt-runtime jar (see SkikoNatives).
    dependsOn(skikoNatives)
    systemProperty("skiko.library.path", layout.buildDirectory.dir("skiko-natives").get().asFile.absolutePath)
    if (providers.gradleProperty("desktopUriHandler").isPresent) {
        systemProperty("amber.uriHandler", "true")
    }
}

// The desktop app always ships under the Android app's version: both read
// amberVersionName from gradle/libs.versions.toml (bump it only there).
val appVersionName: String = libs.versions.amberVersionName.get()

// Installers need a plain X.Y.Z, so a suffix (6.7.0-pre1) is dropped there.
val desktopPackageVersion: String = appVersionName.substringBefore('-')

compose.desktop {
    application {
        mainClass = "com.greenart7c3.nostrsigner.desktop.MainKt"
        // What from(sourceSets.main) would package, but skiko's natives as
        // plain files instead of skiko-awt-runtime-*.jar (see SkikoNatives).
        disableDefaultConfiguration()
        val jar = tasks.named<Jar>("jar")
        mainJar.set(jar.flatMap { it.archiveFile })
        fromFiles(
            jar.flatMap { it.archiveFile },
            configurations.runtimeClasspath.get().filter { !SkikoNatives.runtimeJar.matches(it.name) },
            files(skikoNatives).asFileTree,
        )
        dependsOn(jar.get(), skikoNatives.get())
        // Lets Main.kt set the X11 WM_CLASS so Linux docks match the desktop entry.
        jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        // Keep decrypted keys out of files other tools can read: no jcmd/jmap
        // attach (a same-user heap dump in one command), no heap dump on OOM
        // and no core dump on a JVM crash (hs_err logs are still written).
        jvmArgs += listOf("-XX:+DisableAttachMechanism", "-XX:-HeapDumpOnOutOfMemoryError", "-XX:-CreateCoredumpOnCrash")
        // The version crash reports carry (BuildVariant.version).
        jvmArgs += "-Damber.version=$appVersionName"

        nativeDistributions {
            // Built-in Tor: the prebuilt Arti library of each OS/arch
            // (tools/arti/build.sh); Compose ships the current OS's one.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("appResources"))
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Amber"
            // dbus-java authenticates to the session bus with UnixSystem
            // (actionable Linux notifications, Secret Service keyring).
            modules("jdk.security.auth")
            packageVersion = desktopPackageVersion
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
