import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.tasks.bundling.Compression
import org.gradle.process.ExecOperations

// Amber CLI: a NIP-46 client any local app or agent can call.
// It holds no accounts and no nsec, only saved bunker:// and nostrconnect://
// connections; Amber (desktop or phone) keeps the keys.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.gradle.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
    application
}

kotlin {
    jvmToolchain(21)
}

base {
    archivesName.set("amber-cli")
}

application {
    // Also the folder installDist writes (build/install/amber-cli), which is
    // the jar set jpackage packages.
    applicationName = "amber-cli"
    mainClass.set("com.greenart7c3.nostrsigner.cli.MainKt")
}

tasks.test {
    useJUnit()
}

dependencies {
    // The local relay's default ports, RELAY_AUTH_KIND and the query-param
    // decoding the apps share.
    implementation(project(":common"))
    implementation(libs.quartz.jvm)
    // Coroutines for Quartz's relay client and signer (runBlocking, flows).
    implementation(libs.kotlinx.coroutines.core)
    // Quartz ships jackson-databind but no longer the Kotlin module.
    implementation(libs.jackson.module.kotlin)
    // Native secp256k1 bindings for the JVM (Schnorr signatures + ECDH).
    implementation(libs.secp256k1.jni.jvm)
    implementation(libs.okhttp)
    // The connections file's AES key: the OS credential store (macOS Keychain,
    // Windows Credential Manager, freedesktop Secret Service), like the desktop
    // app's keystore password. The D-Bus pieces are its Linux backend.
    implementation(libs.java.keyring)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.native.unixsocket)
    // java-keyring calls the Windows Credential Manager through JNA, and
    // dbus-java's credentials path uses it on Linux. Declared here (not just at
    // runtime) so the catalog's pinned 5.19.1 wins over the older jna those two
    // declare, instead of pulling a second, unverified copy in.
    implementation(libs.jna.platform)
    // java-keyring and dbus-java log through slf4j; without a provider the
    // startup warning would land on stderr next to the CLI's own output.
    runtimeOnly(libs.slf4j.nop)
    // QR codes for the pairing URI (pure Java, zxing core only).
    implementation(libs.core)

    testImplementation(libs.junit)
}

// The version `amber --version` prints, the amberVersionName the Android app
// and the desktop app ship.
val appVersion: String = libs.versions.amberVersionName.get()

// jpackage needs a plain X.Y.Z: the pre-release suffix is dropped.
val packageCliVersion: String = appVersion.substringBefore('-')

val generateVersionResource = tasks.register("generateVersionResource") {
    val output = layout.buildDirectory.dir("generated/version")
    val version = appVersion
    // An input, or a version bump leaves the task up to date with the old version.
    inputs.property("version", version)
    outputs.dir(output)
    doLast {
        val file = output.get().asFile.resolve("amber-cli.properties")
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}

sourceSets.main {
    resources.srcDir(generateVersionResource)
}

// A self-contained app image: a native launcher plus its own trimmed Java
// runtime, built with the JDK 21 toolchain's own jpackage. Nothing to install.
val rawOsName: String = providers.systemProperty("os.name").get().lowercase()
val isWindows = rawOsName.contains("win")
val isMac = rawOsName.contains("mac")

// "Windows Server 2022" and "Mac OS X" have spaces and change per release:
// the archive name must be stable and URL-safe.
val osName: String = when {
    isWindows -> "windows"
    isMac -> "macos"
    else -> "linux"
}
val osArch: String = when (val arch = providers.systemProperty("os.arch").get().lowercase()) {
    "amd64", "x86_64", "x86-64" -> "x86_64"
    "aarch64", "arm64" -> "arm64"
    else -> arch
}

// Modules jlink bundles beyond what jdeps finds in the jars: java.logging and
// java.naming for OkHttp/Jackson, jdk.crypto.ec for the TLS cipher suites every
// wss:// relay needs, and jdk.unsupported for the sun.misc.Unsafe calls in the
// JNI and serialization stacks.
val runtimeModules = "java.base,java.logging,java.naming,jdk.crypto.ec,jdk.unsupported,jdk.net,jdk.security.auth"

// jpackage builds only for the OS it runs on, so the image and the archive are
// this machine's. Every name inside the image derives from --name, which is why
// the produced directory is renamed afterwards instead.
abstract class JPackageImage : DefaultTask() {
    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val imageDir: DirectoryProperty

    @get:Input
    abstract val jpackageExecutable: Property<String>

    @get:Input
    abstract val jdepsExecutable: Property<String>

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val mainJar: Property<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val modules: Property<String>

    @get:Input
    abstract val windows: Property<Boolean>

    @get:Input
    abstract val mac: Property<Boolean>

    @get:Input
    abstract val linux: Property<Boolean>

    @get:Input
    abstract val imageName: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    /** The module dependencies jdeps finds in the jars, or empty when it cannot tell. */
    private fun detectedModules(): Set<String> {
        val jars = inputDir.get().asFile
        val classpath = jars.listFiles().orEmpty().joinToString(File.pathSeparator) { it.absolutePath }
        val output = ByteArrayOutputStream()
        return runCatching {
            execOperations.exec {
                commandLine(
                    jdepsExecutable.get(),
                    "--print-module-deps",
                    "--ignore-missing-deps",
                    "-cp",
                    classpath,
                    File(jars, mainJar.get()).absolutePath,
                )
                standardOutput = output
                errorOutput = ByteArrayOutputStream()
            }
            output.toString(Charsets.UTF_8).trim().lines().last()
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    }

    @TaskAction
    fun packageImage() {
        val destination = imageDir.get().asFile
        destination.deleteRecursively()
        val staging = File(destination, "staging").apply { mkdirs() }
        val allModules = (modules.get().split(',') + detectedModules()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        val producedName = if (mac.get()) "amber.app" else "amber"
        execOperations.exec {
            commandLine(
                listOf(
                    jpackageExecutable.get(),
                    "--type", "app-image",
                    "--name", "amber",
                    "--app-version", appVersion.get(),
                    "--dest", staging.absolutePath,
                    "--input", inputDir.get().asFile.absolutePath,
                    "--main-jar", mainJar.get(),
                    "--main-class", mainClass.get(),
                    "--add-modules", allModules.joinToString(","),
                    "--description", "Amber - Nostr event signer (NIP-46 client)",
                    "--vendor", "greenart7c3",
                    "--copyright", "\u00a9 greenart7c3. Distributed under the MIT license.",
                ) + if (windows.get()) listOf("--win-console") else emptyList(),
            )
        }
        val produced = File(staging, producedName)
        check(produced.isDirectory) { "jpackage did not produce ${produced.path}" }
        val image = File(destination, imageName.get())
        produced.renameTo(image) || error("could not rename ${produced.path}")
        if (linux.get()) replaceLauncher(image, staging, allModules)
        staging.deleteRecursively()
    }

    /**
     * On Linux the JDK's jpackage launcher prints "pure virtual method called /
     * terminate called without an active exception" on stderr on some
     * distributions (an ABI quirk in libapplauncher, before the app starts).
     * Running the same jars on a jlink runtime's own `java` is silent, so the
     * image gets a jlink runtime that keeps its `java` and a two-line shell
     * launcher in place of the C++ one. Windows keeps the .exe; macOS keeps
     * jpackage's ad-hoc signed bundle launcher.
     */
    private fun replaceLauncher(
        image: File,
        staging: File,
        modules: List<String>,
    ) {
        val jlink = File(jpackageExecutable.get()).parentFile.resolve("jlink").absolutePath
        val fullRuntime = File(staging, "runtime")
        execOperations.exec {
            commandLine(
                listOf(
                    jlink,
                    "--add-modules",
                    modules.joinToString(","),
                    "--output",
                    fullRuntime.absolutePath,
                    "--strip-debug",
                    "--no-header-files",
                    "--no-man-pages",
                ),
            )
        }
        check(fullRuntime.isDirectory) { "jlink did not produce ${fullRuntime.path}" }
        val runtime = File(image, "lib/runtime")
        runtime.deleteRecursively() || error("could not replace ${runtime.path}")
        fullRuntime.renameTo(runtime) || error("could not move ${fullRuntime.path}")
        val launcher = File(image, "bin/amber")
        launcher.delete()
        // Escaped with \$ so Kotlin leaves the shell's own variables alone.
        launcher.writeText(
            listOf(
                "#!/bin/sh",
                "# The amber CLI: runs the bundled runtime directly, because the JDK's",
                "# jpackage launcher writes \"pure virtual method called\" to stderr on",
                "# some Linux distributions before the app starts.",
                "self=\$0",
                "while [ -L \"\$self\" ]; do",
                "  link=\$(readlink \"\$self\")",
                "  case \$link in",
                "    /*) self=\$link ;;",
                "    *) self=\$(dirname \"\$self\")/\$link ;;",
                "  esac",
                "done",
                "dir=\$(cd \"\$(dirname \"\$self\")\" && pwd) || exit 1",
                "exec \"\$dir/../lib/runtime/bin/java\" -cp \"\$dir/../lib/app/*\" " +
                    mainClass.get() + " \"\$@\"",
            ).joinToString("\n") + "\n",
        )
        launcher.setExecutable(true, false)
    }
}

val jpackageCli = tasks.register<JPackageImage>("jpackageCli") {
    group = "distribution"
    description = "Builds the self-contained amber CLI app image (native launcher + bundled runtime)."
    val launcher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    val home = launcher.map { it.metadata.installationPath }
    jpackageExecutable.set(home.map { it.file("bin/jpackage").asFile.absolutePath })
    jdepsExecutable.set(home.map { it.file("bin/jdeps").asFile.absolutePath })
    inputDir.set(layout.buildDirectory.dir("install/amber-cli/lib"))
    imageDir.set(layout.buildDirectory.dir("cli-image"))
    appVersion.set(packageCliVersion)
    mainJar.set("amber-cli.jar")
    mainClass.set("com.greenart7c3.nostrsigner.cli.MainKt")
    modules.set(runtimeModules)
    windows.set(isWindows)
    mac.set(isMac)
    linux.set(!isWindows && !isMac)
    imageName.set(if (isMac) "amber-cli.app" else "amber-cli")
    dependsOn(tasks.named("installDist"))
}

// Not "archiveBaseName": inside a task block that name is the task's own property.
val cliArchiveBaseName = "amber-cli-$appVersion-$osName-$osArch"

if (isWindows) {
    val zipCli = tasks.register<Zip>("archiveCli") {
        group = "distribution"
        description = "Zips the amber CLI app image for Windows."
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        archiveFileName.set("$cliArchiveBaseName.zip")
        from(jpackageCli.map { it.imageDir })
        isReproducibleFileOrder = true
    }
    tasks.register("packageCli") {
        group = "distribution"
        description = "Bundles the CLI and its runtime into a self-contained archive."
        dependsOn(zipCli)
    }
} else {
    val tarCli = tasks.register<Tar>("archiveCli") {
        group = "distribution"
        description = "Archives the amber CLI app image (.tar.gz) for Linux and macOS."
        destinationDirectory.set(layout.buildDirectory.dir("distributions"))
        archiveFileName.set("$cliArchiveBaseName.tar.gz")
        compression = Compression.GZIP
        from(jpackageCli.map { it.imageDir })
        // Gradle's archives do not carry the image's Unix modes over: the
        // launcher and the runtime's own helper binaries must stay executable.
        filesMatching("**/bin/**") { permissions { unix("rwxr-xr-x") } }
        filesMatching("**/lib/runtime/lib/jexec") { permissions { unix("rwxr-xr-x") } }
        filesMatching("**/lib/runtime/lib/jspawnhelper") { permissions { unix("rwxr-xr-x") } }
        filesMatching("**/*.so") { permissions { unix("rwxr-xr-x") } }
        filesMatching("**/*.dylib") { permissions { unix("rwxr-xr-x") } }
        isReproducibleFileOrder = true
        isPreserveFileTimestamps = false
    }
    tasks.register("packageCli") {
        group = "distribution"
        description = "Bundles the CLI and its runtime into a self-contained archive."
        dependsOn(tarCli)
    }
}
