#!/bin/bash
# Builds the distro-independent Linux bundles from the jpackage app image
# (./gradlew :desktop:createDistributable):
#
#   Amber-<version>-linux-x86_64.tar.xz   unpack anywhere, run Amber/bin/Amber
#   Amber-<version>-x86_64.AppImage       single self-contained executable
#
#   desktop/packaging/linux.sh <version> <out-dir>
#
# Needs curl (to fetch appimagetool) and xz. FUSE is not needed: appimagetool
# runs with APPIMAGE_EXTRACT_AND_RUN.
set -euo pipefail

version=${1:?usage: linux.sh <version> <out-dir>}
out=${2:?usage: linux.sh <version> <out-dir>}
here=$(cd "$(dirname "$0")" && pwd)
desktop=$(cd "$here/.." && pwd)
image="$desktop/build/compose/binaries/main/app/Amber"
work="$desktop/build/linux-bundles"

[ -x "$image/bin/Amber" ] || { echo "No app image at $image; run :desktop:createDistributable first" >&2; exit 1; }
mkdir -p "$out"
out=$(cd "$out" && pwd)
rm -rf "$work"
mkdir -p "$work"

tar -C "$(dirname "$image")" -cJf "$out/Amber-$version-linux-x86_64.tar.xz" Amber

appdir="$work/Amber.AppDir"
mkdir -p "$appdir/usr/lib"
cp -a "$image" "$appdir/usr/lib/amber"
cp "$desktop/src/main/resources/icon.png" "$appdir/amber.png"
ln -s amber.png "$appdir/.DirIcon"
# StartupWMClass matches the X11 class the app sets
# (AppImageIntegration.WINDOW_CLASS), so docks show this icon.
cat > "$appdir/amber.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=Amber
Comment=Nostr event signer
Exec=Amber %u
Icon=amber
Categories=Network;
Terminal=false
StartupWMClass=amber-Amber
MimeType=x-scheme-handler/nostrconnect;
EOF
cat > "$appdir/AppRun" << 'EOF'
#!/bin/sh
here=$(dirname "$(readlink -f "$0")")
exec "$here/usr/lib/amber/bin/Amber" "$@"
EOF
chmod +x "$appdir/AppRun"

tool="$work/appimagetool"
# Pinned release (not the moving `continuous` tag) with a verified checksum:
# the hash was computed from a freshly downloaded copy of this exact asset on
# 2026-10-20 (the project publishes no official checksums).
APPIMAGETOOL_VERSION=1.9.1
APPIMAGETOOL_SHA256=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
curl -fsSL -o "$tool" "https://github.com/AppImage/appimagetool/releases/download/${APPIMAGETOOL_VERSION}/appimagetool-x86_64.AppImage"
echo "$APPIMAGETOOL_SHA256  $tool" | sha256sum -c -
chmod +x "$tool"
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$tool" --no-appstream "$appdir" "$out/Amber-$version-x86_64.AppImage"

ls -l "$out"
