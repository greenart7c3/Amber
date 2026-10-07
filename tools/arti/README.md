# Arti (built-in Tor)

Amber's built-in Tor runs [Arti](https://gitlab.torproject.org/tpo/core/arti)
(Tor in Rust) in-process through a small JNI wrapper, `src/lib.rs`. The
wrapper starts a Tor client, puts a SOCKS5 proxy on `127.0.0.1` in front of it
and reports bootstrap progress; Kotlin's `TorManager` drives it through
`com.greenart7c3.nostrsigner.tor.ArtiNative`.

The wrapper is adapted from Amethyst's `tools/arti-build`
(<https://github.com/vitorpamplona/amethyst>, © 2025 Vitor Pamplona, MIT License).

## Prebuilt libraries

The libraries are built once with `build.sh` and committed; CI does not build Rust.

| App | Path | Built with |
|---|---|---|
| Android (`free`, `benchmark`) | `app/src/tor/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}/libamber_arti.so` | `./build.sh android` (cargo-ndk, NDK pinned in `build.sh`) |
| Desktop Linux | `desktop/appResources/linux-{x64,arm64}/libamber_arti.so` (glibc ≥ 2.28) | `./build.sh desktop` (cargo-zigbuild) |
| Desktop Windows | `desktop/appResources/windows-x64/amber_arti.dll` | `./build.sh desktop` (cargo-zigbuild) |
| Desktop macOS | `desktop/appResources/macos-{x64,arm64}/libamber_arti.dylib` | `./build.sh macos`, on a Mac |

The Android ABIs must match `splits.abi` in `app/build.gradle.kts`. On the
desktop, Compose ships the current OS's directory as app resources
(`appResourcesRootDir`), and `ArtiNative` loads the library from
`compose.application.resources.dir`.

## Rebuilding

Rebuild after changing `src/lib.rs`, `Cargo.toml` or `Cargo.lock`, and commit
the regenerated libraries together with the source change. Every library has
to come from the same source, so rebuild all of them.

Prerequisites: rustup (the compiler is pinned in `rust-toolchain.toml`; the
script adds the targets), plus

- android: `cargo install cargo-ndk` and the NDK revision in `build.sh`
- desktop: `cargo install cargo-zigbuild` and zig (`pip install ziglang` is enough)
- macos: a Mac with the Xcode command line tools (the link needs Apple's
  frameworks, which zig cannot provide)

```bash
./build.sh android desktop   # on Linux
./build.sh macos             # on a Mac, from a copy of this repository
```

The script checks that every library exports the JNI entry points and prints
their SHA-256 hashes.

## Reproducibility

Rebuilding from a fresh checkout (any path: `build.sh` remaps host paths out
of the binaries) gives byte-identical libraries with the pinned toolchain and
`Cargo.lock`. The APK packages `libamber_arti.so` unstripped
(`packaging.jniLibs.keepDebugSymbols` in `app/build.gradle.kts`), so the copy in
a release is the committed file itself:

```bash
unzip -p amber-arm64-v8a-vX.Y.Z.apk lib/arm64-v8a/libamber_arti.so | sha256sum
sha256sum app/src/tor/jniLibs/arm64-v8a/libamber_arti.so
```

AGP still strips the app's other native libraries with the NDK pinned in
`app/build.gradle.kts` (`ndkVersion`), which the reproducibility `Dockerfile`
installs too.

## Patched dependency: `saturating-time`

`Cargo.toml` replaces `saturating-time` 0.5.0 (used by `tor-netdoc`) with the
copy in `patches/saturating-time`. Upstream's `find_limit` halves its step down
to 1ns and only stops on `None`, but Windows' `SystemTime` ticks in 100ns, so a
1-99ns `checked_sub` returns the same time instead of `None` and the loop never
ends: Arti then hangs at 15% ("fetching a consensus") with one core pinned the
first time it parses a consensus. The patch treats "no progress" like `None`
(see the "Amber patch" comment). Drop it once a fixed release is out.

## Updating Arti

1. Bump `arti-client` and `tor-rtcompat` in `Cargo.toml` (and `version`, which
   tracks the Arti release: arti 2.7.0 = crates 0.47.0).
2. `cargo update -p arti-client -p tor-rtcompat` to refresh `Cargo.lock`.
3. Rebuild every library as above.

## JNI surface

| Kotlin (`ArtiNative`) | Purpose |
|---|---|
| `initialize(dataDir)` | Create the Tor client (state + cache under `dataDir`) and start the directory download in the background |
| `isBootstrapped()` / `bootstrapProgressPermille()` | Readiness and download progress, polled by `TorManager` |
| `startSocksProxy(port)` / `stopSocksProxy()` | Bind / release the SOCKS5 listener; the client stays alive |
| `destroy()` | Drop the client so the next `initialize` starts over (used by `restart`) |
| `setLogCallback(cb)` / `getVersion()` | Logging into Amber's log and version string |

The JNI symbol names are derived from the Kotlin package and class
(`Java_com_greenart7c3_nostrsigner_tor_ArtiNative_*`), so the Android and
desktop copies of `ArtiNative` must keep that package and name.
