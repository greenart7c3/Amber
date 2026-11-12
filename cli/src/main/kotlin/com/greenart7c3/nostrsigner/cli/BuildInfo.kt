package com.greenart7c3.nostrsigner.cli

import java.util.Properties

/**
 * The build's version, from the properties file the Gradle build generates, so
 * `amber --version` matches amberVersionName in gradle/libs.versions.toml like
 * the Android and desktop apps do.
 */
object BuildInfo {
    val version: String = Properties().apply {
        BuildInfo::class.java.getResourceAsStream("/amber-cli.properties")?.use { load(it) }
    }.getProperty("version", "dev")

    val userAgent: String = "amber-cli/$version"
}
