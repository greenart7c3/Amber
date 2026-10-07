package com.greenart7c3.nostrsigner.tor

import java.io.File

/**
 * JNI bridge to the Arti (Tor in Rust) wrapper built from `tools/arti`.
 *
 * Desktop copy of the Android app's `ArtiNative`: same package and members,
 * because the JNI symbol names in `tools/arti/src/lib.rs` are derived from
 * them. The per-OS libraries are committed under `desktop/appResources/` and
 * shipped as Compose app resources, so the library is loaded from
 * `compose.application.resources.dir` (set for packaged apps and `:desktop:run`).
 */
object ArtiNative {
    init {
        val name = System.mapLibraryName("amber_arti")
        val bundled = System.getProperty("compose.application.resources.dir")?.let { File(it, name) }
        if (bundled != null && bundled.isFile) {
            System.load(bundled.absolutePath)
        } else {
            System.loadLibrary("amber_arti")
        }
    }

    external fun getVersion(): String

    external fun setLogCallback(callback: ArtiLogCallback)

    /**
     * Creates the Tor client and starts the directory download in the
     * background. Returns as soon as the client exists.
     * @return 0 on success, negative on error.
     */
    external fun initialize(dataDir: String): Int

    /** 1 when Tor can carry traffic, 0 while still bootstrapping, -1 without a client. */
    external fun isBootstrapped(): Int

    /** Directory-download progress in permille (0..1000), or -1 without a client. */
    external fun bootstrapProgressPermille(): Int

    /**
     * Binds the SOCKS5 proxy on 127.0.0.1:[port], replacing any previous listener.
     * @return 0 on success, negative on error (-3: the port could not be bound).
     */
    external fun startSocksProxy(port: Int): Int

    /** Stops the SOCKS5 listener; the Tor client stays alive. */
    external fun stopSocksProxy(): Int

    /** Drops the Tor client so the next [initialize] starts over (fresh circuits). */
    external fun destroy(): Int
}

/** Receives Arti's log lines from the native layer (called by name from Rust). */
fun interface ArtiLogCallback {
    fun onLogLine(line: String)
}
