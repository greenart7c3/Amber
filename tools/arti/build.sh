#!/usr/bin/env bash
#
# Builds the Arti JNI wrapper (src/lib.rs) and copies the libraries to where
# the apps load them from. The outputs are committed; CI does not build Arti.
#
#   android  -> app/src/tor/jniLibs/<abi>/libamber_arti.so            (cargo-ndk)
#   desktop  -> desktop/appResources/{linux,windows}-<arch>/<lib>     (cargo-zigbuild)
#   macos    -> desktop/appResources/macos-{x64,arm64}/<lib>          (cargo, on a Mac)
#
# Usage: ./build.sh [android] [desktop] [macos]   (default: android desktop)
#
# Prerequisites:
#   - rustup (rust-toolchain.toml pins the compiler; targets are added here)
#   - android: cargo-ndk, Android NDK $NDK_VERSION
#   - desktop: cargo-zigbuild and zig (e.g. `pip install ziglang`); zig cross-
#     compiles Linux and Windows from one host, links Linux against an old
#     glibc ($GLIBC) so the library loads on older distros, and needs no
#     Windows SDK.
#   - macos: a Mac with the Xcode command line tools (the link needs Apple's
#     frameworks, which zig does not have).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$SCRIPT_DIR"

NDK_VERSION="28.2.13676358"
MIN_SDK=26
GLIBC="2.28"

ANDROID_OUT="$ROOT/app/src/tor/jniLibs"
DESKTOP_OUT="$ROOT/desktop/appResources"

# Rust target -> jniLibs ABI. Must match splits.abi in app/build.gradle.kts.
ANDROID_TARGETS=(
    "aarch64-linux-android:arm64-v8a"
    "x86_64-linux-android:x86_64"
    "armv7-linux-androideabi:armeabi-v7a"
    "i686-linux-android:x86"
)

# Rust target (zig glibc suffix for Linux) -> Compose appResources dir : file name.
DESKTOP_TARGETS=(
    "x86_64-unknown-linux-gnu.$GLIBC:linux-x64:libamber_arti.so"
    "aarch64-unknown-linux-gnu.$GLIBC:linux-arm64:libamber_arti.so"
    "x86_64-pc-windows-gnu:windows-x64:amber_arti.dll"
)

MACOS_TARGETS=(
    "x86_64-apple-darwin:macos-x64"
    "aarch64-apple-darwin:macos-arm64"
)

JNI_SYMBOLS=(getVersion setLogCallback initialize startSocksProxy stopSocksProxy isBootstrapped bootstrapProgressPermille destroy)

# Keep host paths out of the binaries.
export CARGO_INCREMENTAL=0
export RUSTFLAGS="${RUSTFLAGS:-} --remap-path-prefix=${CARGO_HOME:-$HOME/.cargo}=/cargo --remap-path-prefix=$SCRIPT_DIR=/arti"

add_target() {
    rustup target list --installed | grep -qx "$1" || rustup target add "$1"
}

check_symbols() {
    local lib="$1" exports
    case "$lib" in
        *.dylib) exports="$(nm -gU "$lib")" ;;   # Mach-O keeps exports in a compressed trie
        *.dll) exports="$(strings -a "$lib")" ;; # PE export table holds the plain names
        *) exports="$(nm -D "$lib")" ;;
    esac
    for sym in "${JNI_SYMBOLS[@]}"; do
        if [[ "$exports" != *"Java_com_greenart7c3_nostrsigner_tor_ArtiNative_$sym"* ]]; then
            echo "Missing JNI symbol $sym in $lib" >&2
            exit 1
        fi
    done
}

find_ndk() {
    local candidate
    for candidate in "${ANDROID_NDK_HOME:-}" "${ANDROID_HOME:-}/ndk/$NDK_VERSION" "${ANDROID_SDK_ROOT:-}/ndk/$NDK_VERSION" \
        "$HOME/Android/Sdk/ndk/$NDK_VERSION" "$HOME/Library/Android/sdk/ndk/$NDK_VERSION"; do
        [ -n "$candidate" ] && [ -f "$candidate/source.properties" ] || continue
        if grep -q "Pkg.Revision *= *$NDK_VERSION" "$candidate/source.properties"; then
            echo "$candidate"
            return
        fi
    done
    echo "Android NDK $NDK_VERSION not found (sdkmanager \"ndk;$NDK_VERSION\")" >&2
    exit 1
}

build_android() {
    command -v cargo-ndk > /dev/null || { echo "cargo-ndk not found (cargo install cargo-ndk)" >&2; exit 1; }
    ANDROID_NDK_HOME="$(find_ndk)"
    export ANDROID_NDK_HOME
    for entry in "${ANDROID_TARGETS[@]}"; do
        local target="${entry%%:*}" abi="${entry##*:}"
        echo "==> Android $abi ($target)"
        add_target "$target"
        cargo ndk -t "$target" --platform "$MIN_SDK" -o "$ANDROID_OUT" build --release --locked
        check_symbols "$ANDROID_OUT/$abi/libamber_arti.so"
    done
}

build_desktop() {
    command -v cargo-zigbuild > /dev/null || { echo "cargo-zigbuild not found (cargo install cargo-zigbuild)" >&2; exit 1; }
    for entry in "${DESKTOP_TARGETS[@]}"; do
        IFS=: read -r zig_target dir file <<< "$entry"
        local target="${zig_target%%.*}"
        echo "==> Desktop $dir ($zig_target)"
        add_target "$target"
        cargo zigbuild --release --locked --target "$zig_target"
        mkdir -p "$DESKTOP_OUT/$dir"
        cp "target/$target/release/$file" "$DESKTOP_OUT/$dir/$file"
        check_symbols "$DESKTOP_OUT/$dir/$file"
    done
}

build_macos() {
    [ "$(uname -s)" = Darwin ] || { echo "The macOS libraries must be built on a Mac" >&2; exit 1; }
    for entry in "${MACOS_TARGETS[@]}"; do
        local target="${entry%%:*}" dir="${entry##*:}"
        echo "==> Desktop $dir ($target)"
        add_target "$target"
        cargo build --release --locked --target "$target"
        mkdir -p "$DESKTOP_OUT/$dir"
        cp "target/$target/release/libamber_arti.dylib" "$DESKTOP_OUT/$dir/libamber_arti.dylib"
        check_symbols "$DESKTOP_OUT/$dir/libamber_arti.dylib"
    done
}

what=("$@")
[ ${#what[@]} -eq 0 ] && what=(android desktop)
for w in "${what[@]}"; do
    case "$w" in
        android) build_android ;;
        desktop) build_desktop ;;
        macos) build_macos ;;
        *) echo "Usage: $0 [android] [desktop] [macos]" >&2; exit 1 ;;
    esac
done

echo
( cd "$ROOT" && find app/src/tor/jniLibs desktop/appResources -type f \( -name '*.so' -o -name '*.dll' -o -name '*.dylib' \) -exec shasum -a 256 {} + 2>/dev/null | sort -k2 ) || true
