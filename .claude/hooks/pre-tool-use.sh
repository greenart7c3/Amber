#!/bin/bash
# Auto-configure Maven/Gradle proxy auth and SSL trust in Claude Code environments.
# Runs as a PreToolUse hook for Bash. No-ops when not needed.

[ "$CLAUDECODE" = "1" ] || exit 0

proxy="${https_proxy:-$HTTPS_PROXY}"
[ -z "$proxy" ] && exit 0
echo "$proxy" | grep -q '@' || exit 0

rest="${proxy#*://}"
userpass="${rest%@*}"
hostport="${rest##*@}"
user="${userpass%%:*}"
pass="${userpass#*:}"
host="${hostport%%:*}"
port="${hostport##*:}"
port="${port%/}"

# --- Proxy credentials (always update; JWT tokens rotate) ---
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

mkdir -p ~/.gradle
cat > ~/.gradle/gradle.properties << EOF
systemProp.https.proxyHost=$host
systemProp.https.proxyPort=$port
systemProp.https.proxyUser=$user
systemProp.https.proxyPassword=$pass
systemProp.http.proxyHost=$host
systemProp.http.proxyPort=$port
systemProp.http.proxyUser=$user
systemProp.http.proxyPassword=$pass
# Override nonProxyHosts: route all external traffic (incl. *.google.com) through proxy
systemProp.http.nonProxyHosts=localhost|127.0.0.1
systemProp.https.nonProxyHosts=localhost|127.0.0.1
EOF

echo "Configured Maven/Gradle proxy from HTTPS_PROXY" >&2

# --- SSL trust: import Anthropic TLS inspection CA into JVM trust stores ---
# The proxy does SSL interception; JVMs need to trust the Anthropic CA.
# Extract the CA cert from the system bundle (it's pre-installed there).
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

    # Import into all available JVM trust stores
    for cacerts in \
        /usr/lib/jvm/java-21-openjdk-amd64/lib/security/cacerts \
        /root/.gradle/jdks/*/lib/security/cacerts; do
        [ -f "$cacerts" ] || continue
        # Check if already imported
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

# --- Android SDK: create local.properties if SDK is installed ---
ANDROID_SDK_DIR="/root/android-sdk"
LOCAL_PROPS="$(git -C "$(dirname "$0")/../.." rev-parse --show-toplevel 2>/dev/null)/local.properties"
if [ -d "$ANDROID_SDK_DIR/platforms" ] && [ -n "$LOCAL_PROPS" ] && [ ! -f "$LOCAL_PROPS" ]; then
    echo "sdk.dir=$ANDROID_SDK_DIR" > "$LOCAL_PROPS"
    echo "Created local.properties with sdk.dir=$ANDROID_SDK_DIR" >&2
fi
