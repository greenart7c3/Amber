package com.greenart7c3.nostrsigner.desktop.core

/**
 * Debug vs release, the desktop counterpart of the Android `debug` build type
 * (`.debug` applicationId suffix, "Amber Debug" label).
 *
 * `./gradlew :desktop:run` sets the `amber.debug` system property; packaged
 * distributions never do. A debug run keeps everything that lives outside the
 * process apart from a release install on the same machine: the data dir (keys,
 * accounts, single-instance lock), the OS credential store entry, the
 * nostrconnect socket and forward file, and the nostrconnect:// handler entry.
 */
object BuildVariant {
    val isDebug: Boolean = System.getProperty("amber.debug").toBoolean()

    /** Window, tray and notification name. */
    val appName: String = if (isDebug) "Amber Debug" else "Amber"

    /** Appended to OS-level identifiers (file names, keyring service, ...). */
    val suffix: String = if (isDebug) "-debug" else ""
}
