// Logic shared by the Android app (:app) and the desktop app (:desktop):
// plain Kotlin/JVM with no platform APIs, so both can depend on it.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.gradle.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
}

dependencies {
    // Each app ships its own Quartz build (quartz-android or quartz-jvm, which
    // share this API), so this module only compiles against it.
    compileOnly(libs.quartz.jvm)
    // StateFlow for LocalRelayServer.status; both apps get coroutines through Quartz.
    compileOnly(libs.kotlinx.coroutines.core)
    // @Immutable for the Android app's Compose compiler; not needed at runtime.
    compileOnly(libs.compose.runtime.annotation)

    testImplementation(libs.quartz.jvm)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    // Native secp256k1 for the JVM, which Quartz's crypto runs on in tests.
    testImplementation(libs.secp256k1.jni.jvm)
}

kover {
    reports {
        total {
            verify {
                rule {
                    // :common's own unit tests cover ~85% of its lines, LocalRelayServer
                    // included; :desktop's tests exercise more of it through the bunker
                    // flow, which the merged report at build/reports/kover counts.
                    // A ratchet: raise it when coverage improves, never lower it to
                    // make a change pass.
                    minBound(85)
                }
            }
        }
    }
}
