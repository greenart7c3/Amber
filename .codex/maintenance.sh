#!/usr/bin/env bash
set -euo pipefail

# Codex Web maintenance script for cached environments. Configure this as the
# optional maintenance script so resumed Codex containers refresh Gradle metadata.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

chmod +x ./gradlew
./gradlew --no-daemon --stacktrace help
