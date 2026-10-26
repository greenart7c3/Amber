pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Nostr Signer"
include(":app")
include(":common")
include(":desktop")
include(":unused-code")
project(":unused-code").projectDir = file("tools/unused-code")
