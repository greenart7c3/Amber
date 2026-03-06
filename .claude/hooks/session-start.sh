#!/bin/bash
set -euo pipefail

# Only run in remote (Claude Code on the web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

ANDROID_SDK_ROOT="${HOME}/android-sdk"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

# ── 1. Install Android SDK command-line tools if missing ──────────────────────
if [ ! -f "${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[session-start] Installing Android SDK command-line tools..."
  mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
  TMP_ZIP=$(mktemp /tmp/sdk-tools-XXXXXX.zip)
  wget -q "${CMDLINE_TOOLS_URL}" -O "${TMP_ZIP}"
  unzip -q "${TMP_ZIP}" -d "${ANDROID_SDK_ROOT}/cmdline-tools"
  rm "${TMP_ZIP}"
  # The zip unpacks as "cmdline-tools/"; rename to "latest" as sdkmanager expects
  mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" \
     "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  echo "[session-start] Command-line tools installed."
fi

export ANDROID_HOME="${ANDROID_SDK_ROOT}"
export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/35.0.0:${PATH}"

# ── 2. Install required SDK components if missing ─────────────────────────────
if [ ! -d "${ANDROID_SDK_ROOT}/platforms/android-36" ]; then
  echo "[session-start] Accepting SDK licenses and installing SDK components..."
  yes | sdkmanager --licenses > /dev/null 2>&1 || true
  sdkmanager \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;35.0.0"
  echo "[session-start] SDK components installed."
fi

# ── 3. Persist env vars for the session ──────────────────────────────────────
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=${ANDROID_SDK_ROOT}" >> "${CLAUDE_ENV_FILE}"
  echo "export PATH=${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/35.0.0:\$PATH" >> "${CLAUDE_ENV_FILE}"
fi

# ── 4. Warm up Gradle dependency cache ───────────────────────────────────────
cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"
echo "[session-start] Warming up Gradle dependency cache..."
./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
echo "[session-start] Setup complete."
