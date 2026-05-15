#!/usr/bin/env bash
set -euo pipefail

# Codex Web setup for Amber's Android/Gradle build.
# Configure this in Codex Web as the environment setup script:
#   bash .codex/setup.sh

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export DEBIAN_FRONTEND=noninteractive
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

apt_install() {
  if ! command -v apt-get >/dev/null 2>&1; then
    return 1
  fi

  if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
    apt-get update
    apt-get install -y --no-install-recommends "$@"
  else
    sudo apt-get update
    sudo apt-get install -y --no-install-recommends "$@"
  fi
}

ensure_base_packages() {
  local missing=()
  for tool in curl unzip python3; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      missing+=("$tool")
    fi
  done

  if [[ "${#missing[@]}" -gt 0 ]]; then
    apt_install ca-certificates "${missing[@]}" || {
      echo "Missing required tools (${missing[*]}) and apt-get is unavailable." >&2
      exit 1
    }
  fi
}

ensure_java_21() {
  if command -v java >/dev/null 2>&1; then
    local major
    major="$(java -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)"
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      return 0
    fi
  fi

  apt_install openjdk-21-jdk ca-certificates || {
    echo "Java 21 is required but was not found, and apt-get is unavailable." >&2
    exit 1
  }
}

cmdline_tools_url() {
  python3 - <<'PY'
import urllib.request
import xml.etree.ElementTree as ET

repo = "https://dl.google.com/android/repository/repository2-1.xml"
root = ET.fromstring(urllib.request.urlopen(repo, timeout=60).read())
for package in root.findall("remotePackage"):
    if package.attrib.get("path") != "cmdline-tools;latest":
        continue
    for archive in package.findall("archives/archive"):
        host = archive.findtext("host-os")
        url = archive.findtext("complete/url")
        if host == "linux" and url:
            print("https://dl.google.com/android/repository/" + url)
            raise SystemExit(0)
raise SystemExit("Could not find Linux Android command line tools in repository metadata")
PY
}

ensure_android_sdk() {
  mkdir -p "$ANDROID_HOME/cmdline-tools" "$GRADLE_USER_HOME"

  if ! command -v sdkmanager >/dev/null 2>&1; then
    local tmpdir url
    tmpdir="$(mktemp -d)"
    trap 'rm -rf "$tmpdir"' RETURN
    url="$(cmdline_tools_url)"
    curl -fsSL "$url" -o "$tmpdir/cmdline-tools.zip"
    unzip -q "$tmpdir/cmdline-tools.zip" -d "$tmpdir"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
    mv "$tmpdir/cmdline-tools"/* "$ANDROID_HOME/cmdline-tools/latest/"
  fi

  yes | sdkmanager --licenses >/dev/null || true
  sdkmanager \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0"
}

ensure_base_packages
ensure_java_21
ensure_android_sdk

chmod +x ./gradlew

# Warm Gradle, Android Gradle Plugin, Kotlin, and unit-test dependencies while the
# Codex environment has setup-time internet access. Override with CODEX_PREWARM_TASKS
# in the Codex environment settings if you need a lighter/heavier cache.
read -r -a prewarm_tasks <<< "${CODEX_PREWARM_TASKS:-help :app:compileFreeDebugUnitTestSources}"
./gradlew --no-daemon --stacktrace "${prewarm_tasks[@]}"
