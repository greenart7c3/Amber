#!/bin/bash
# Session start hook: Configure proxy auth, SSL trust, JetBrains JDK, and Android SDK for Claude Code on the web
set -euo pipefail

# Only run in remote (web) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

# --- Proxy credentials: configure Maven/Gradle if an authenticated proxy is set ---
# Newer Claude Code web containers use transparent TLS-inspecting egress (no $https_proxy),
# so this block is a no-op there. It still handles legacy authenticated-proxy environments.
proxy="${https_proxy:-${HTTPS_PROXY:-}}"
GRADLE_PROXY_PROPS=""
if [ -n "$proxy" ] && echo "$proxy" | grep -q '@'; then
  rest="${proxy#*://}"
  userpass="${rest%@*}"
  hostport="${rest##*@}"
  user="${userpass%%:*}"
  pass="${userpass#*:}"
  host="${hostport%%:*}"
  port="${hostport##*:}"
  port="${port%/}"

  mkdir -p ~/.m2
  cat > ~/.m2/settings.xml << EOF
<settings>
  <proxies>
    <proxy>
      <id>ccw</id><active>true</active><protocol>https</protocol>
      <host>$host</host><port>$port</port>
      <username>$user</username>
      <password><![CDATA[$pass]]></password>
    </proxy>
  </proxies>
</settings>
EOF

  # Force wagon transport for Maven 3.9+ proxy auth compatibility
  cat > ~/.mavenrc << 'MAVENRC'
MAVEN_OPTS="$MAVEN_OPTS -Dmaven.resolver.transport=wagon"
MAVENRC

  GRADLE_PROXY_PROPS=$(cat <<EOF
systemProp.https.proxyHost=$host
systemProp.https.proxyPort=$port
systemProp.https.proxyUser=$user
systemProp.https.proxyPassword=$pass
systemProp.http.proxyHost=$host
systemProp.http.proxyPort=$port
systemProp.http.proxyUser=$user
systemProp.http.proxyPassword=$pass
systemProp.http.nonProxyHosts=localhost|127.0.0.1
systemProp.https.nonProxyHosts=localhost|127.0.0.1
systemProp.jdk.http.auth.tunneling.disabledSchemes=
systemProp.jdk.http.auth.proxying.disabledSchemes=
EOF
)
  echo "Configured Maven/Gradle proxy from HTTPS_PROXY" >&2
fi

# --- Pre-install JetBrains JDK 21 for the Gradle daemon ---
# gradle/gradle-daemon-jvm.properties requires vendor=JETBRAINS, version=21. Gradle's
# auto-provisioning probes the download URL with HEAD, which the egress proxy returns
# 503 for, so auto-download fails. We pre-fetch with GET (which succeeds) and point
# Gradle at the installed JDK via org.gradle.java.installations.paths.
JBR_HOME="/root/.jdks/jbrsdk-21"
if [ ! -x "$JBR_HOME/bin/java" ]; then
  # foojay.io ID 02900abb5c54c47ce623758dd941d5c0 → jbrsdk_jcef-21-linux-x64-b212.1
  JBR_URL="https://d2xrhe97vsfxuc.cloudfront.net/jbrsdk_jcef-21-linux-x64-b212.1.tar.gz"
  echo "Downloading JetBrains JDK 21..." >&2
  TMP_JBR=$(mktemp /tmp/jbrsdk.XXXXXX.tar.gz)
  if curl -fsSL "$JBR_URL" -o "$TMP_JBR"; then
    TMP_EXTRACT=$(mktemp -d)
    tar -xzf "$TMP_JBR" -C "$TMP_EXTRACT"
    mkdir -p "$(dirname "$JBR_HOME")"
    mv "$TMP_EXTRACT"/* "$JBR_HOME"
    rm -rf "$TMP_EXTRACT" "$TMP_JBR"
    echo "Installed JetBrains JDK 21 at $JBR_HOME" >&2
  else
    rm -f "$TMP_JBR"
    echo "WARNING: failed to download JetBrains JDK; Gradle daemon provisioning will be attempted by Gradle" >&2
  fi
fi

# --- Write ~/.gradle/gradle.properties ---
mkdir -p ~/.gradle
{
  # Use Ubuntu's Java trust store (includes Anthropic TLS inspection CA) for all Gradle JVMs.
  # Gradle may use a JDK whose bundled trust store doesn't include the Anthropic CA,
  # causing TLS inspection failures.
  echo "systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
  echo "systemProp.javax.net.ssl.trustStoreType=JKS"
  echo "systemProp.javax.net.ssl.trustStorePassword=changeit"
  if [ -x "$JBR_HOME/bin/java" ]; then
    echo "org.gradle.java.installations.paths=$JBR_HOME"
    echo "org.gradle.java.installations.auto-download=false"
  fi
  if [ -n "$GRADLE_PROXY_PROPS" ]; then
    echo "$GRADLE_PROXY_PROPS"
  fi
} > ~/.gradle/gradle.properties

# --- SSL trust: import Anthropic TLS inspection CA into JVM trust stores ---
ANTHROPIC_CA_PEM=$(python3 -c "
import re, ssl, sys
try:
    with open('/etc/ssl/certs/ca-certificates.crt') as f:
        certs = re.findall(r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----', f.read(), re.DOTALL)
    for cert in certs:
        der = ssl.PEM_cert_to_DER_cert(cert)
        if b'Anthropic' in der and b'sandbox-egress-production' in der:
            print(cert)
            break
except Exception as e:
    sys.stderr.write(f'CA extraction failed: {e}\n')
" 2>/dev/null)

if [ -n "$ANTHROPIC_CA_PEM" ]; then
  TMPCA=$(mktemp /tmp/anthropic-ca.XXXXXX.pem)
  echo "$ANTHROPIC_CA_PEM" > "$TMPCA"
  for cacerts in \
    /usr/lib/jvm/java-21-openjdk-amd64/lib/security/cacerts \
    /root/.jdks/jbrsdk-21/lib/security/cacerts \
    /root/.gradle/jdks/*/lib/security/cacerts; do
    [ -f "$cacerts" ] || continue
    keytool -list -keystore "$cacerts" -storepass changeit \
      -alias anthropic-egress-production-ca >/dev/null 2>&1 && continue
    keytool -import \
      -alias anthropic-egress-production-ca \
      -file "$TMPCA" \
      -keystore "$cacerts" \
      -storepass changeit \
      -noprompt >/dev/null 2>&1 && \
      echo "Imported Anthropic CA into $cacerts" >&2
  done
  rm -f "$TMPCA"
fi

ANDROID_SDK_DIR="/root/android-sdk"
SDK_REPO_BASE="https://dl.google.com/android/repository"

# Install Android SDK packages by downloading directly with curl
# (sdkmanager cannot reach the SDK repository through the proxy)
install_sdk_package() {
  local zip_url="$1"
  local dest_dir="$2"
  local inner_dir="$3"  # top-level dir inside the zip

  if [ -d "$dest_dir" ]; then
    return 0
  fi

  echo "Downloading $zip_url..."
  local TMP_ZIP
  TMP_ZIP=$(mktemp /tmp/sdk-pkg.XXXXXX.zip)
  curl -fsSL "$zip_url" -o "$TMP_ZIP"

  local TMP_DIR
  TMP_DIR=$(mktemp -d)
  unzip -q "$TMP_ZIP" -d "$TMP_DIR"
  rm -f "$TMP_ZIP"

  mkdir -p "$(dirname "$dest_dir")"
  mv "$TMP_DIR/$inner_dir" "$dest_dir"
  rm -rf "$TMP_DIR"
  echo "Installed to $dest_dir"
}

# Install Android platform 36
install_sdk_package \
  "$SDK_REPO_BASE/platform-36_r02.zip" \
  "$ANDROID_SDK_DIR/platforms/android-36" \
  "android-36"

# Install build-tools 36.0.0 (zip uses "android-16" as inner dir name)
install_sdk_package \
  "$SDK_REPO_BASE/build-tools_r36_linux.zip" \
  "$ANDROID_SDK_DIR/build-tools/36.0.0" \
  "android-16"

# Install platform-tools
install_sdk_package \
  "$SDK_REPO_BASE/platform-tools_r37.0.0-linux.zip" \
  "$ANDROID_SDK_DIR/platform-tools" \
  "platform-tools"

# Accept SDK licenses (create license files manually)
echo "Writing SDK license files..."
mkdir -p "$ANDROID_SDK_DIR/licenses"
# android-sdk-license
echo -e "\n24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$ANDROID_SDK_DIR/licenses/android-sdk-license"
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" >> "$ANDROID_SDK_DIR/licenses/android-sdk-license"
# android-sdk-preview-license
echo -e "\n84831b9409646a918e30573bab4c9c91346d8abd" > "$ANDROID_SDK_DIR/licenses/android-sdk-preview-license"
echo -e "\n504667f4c0de7af1a06de9f4b1727b84351f2910" >> "$ANDROID_SDK_DIR/licenses/android-sdk-preview-license"
# intel-android-extra-license
echo -e "\nd975f751698a77b662f1254ddbeed3901e976f5a" > "$ANDROID_SDK_DIR/licenses/intel-android-extra-license"

# Create local.properties if missing
REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || echo "${CLAUDE_PROJECT_DIR:-/home/user/Amber}")"
LOCAL_PROPS="$REPO_ROOT/local.properties"
if [ ! -f "$LOCAL_PROPS" ]; then
  echo "sdk.dir=$ANDROID_SDK_DIR" > "$LOCAL_PROPS"
  echo "Created local.properties with sdk.dir=$ANDROID_SDK_DIR"
fi

# Export ANDROID_HOME for the session
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export ANDROID_HOME=$ANDROID_SDK_DIR" >> "$CLAUDE_ENV_FILE"
  echo "export ANDROID_SDK_ROOT=$ANDROID_SDK_DIR" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=\$PATH:$ANDROID_SDK_DIR/platform-tools" >> "$CLAUDE_ENV_FILE"
fi

cd "$CLAUDE_PROJECT_DIR"
./gradlew --version > /dev/null 2>&1

echo "Android SDK setup complete."
