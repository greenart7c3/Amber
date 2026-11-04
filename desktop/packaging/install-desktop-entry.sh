#!/bin/sh
# Adds Amber from this unpacked tarball to the app launcher of the current
# user (no root): a desktop entry in $XDG_DATA_HOME/applications that runs
# bin/Amber and uses lib/Amber.png as its icon. StartupWMClass matches the X11
# class Amber sets, so docks show its icon. Amber registers itself as the
# nostrconnect:// handler on its first launch, so the entry does not.
#
#   ./install-desktop-entry.sh               add or refresh the entry
#   ./install-desktop-entry.sh --uninstall   remove it
#
# Run it again after moving the Amber folder. The entry has its own name, so
# it never shadows the .deb/.rpm amber-Amber.desktop or the AppImage's entry.
set -eu

apps="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
entry="$apps/amber-tarball.desktop"

refresh() {
    if command -v update-desktop-database > /dev/null 2>&1; then
        update-desktop-database "$apps" > /dev/null 2>&1 || true
    fi
}

case "${1:-}" in
    "") ;;
    --uninstall)
        rm -f "$entry"
        refresh
        echo "Removed $entry"
        exit 0
        ;;
    *)
        echo "usage: $0 [--uninstall]" >&2
        exit 2
        ;;
esac

here=$(cd "$(dirname "$0")" && pwd -P)
exe="$here/bin/Amber"
icon="$here/lib/Amber.png"
[ -x "$exe" ] || { echo "No Amber binary at $exe" >&2; exit 1; }
case "$here" in
    *"
"*) echo "The Amber folder's path contains a newline; move it first" >&2; exit 1 ;;
esac

# Desktop Entry spec: the Exec argument is double-quoted with " ` $ \
# backslash-escaped and % doubled, and every value (Exec and Icon) then has
# its backslashes escaped once more as a string.
exec_arg=$(printf '%s' "$exe" | sed -e 's/[\\"`$]/\\&/g' -e 's/%/%%/g' -e 's/^/"/' -e 's/$/"/' -e 's/\\/\\\\/g')
icon_value=$(printf '%s' "$icon" | sed -e 's/\\/\\\\/g')

mkdir -p "$apps"
tmp="$entry.tmp.$$"
trap 'rm -f "$tmp"' EXIT
cat > "$tmp" << EOF
[Desktop Entry]
Type=Application
Name=Amber
Comment=Nostr event signer
Exec=$exec_arg %u
Icon=$icon_value
Categories=Network;
Terminal=false
StartupWMClass=amber-Amber
EOF
mv -f "$tmp" "$entry"
refresh
echo "Installed $entry -> $exe"
