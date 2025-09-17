FROM eclipse-temurin:21-jdk

ARG VERSION
ARG APK_TYPE
ENV VERSION=${VERSION:-v4.0.2}
ENV APK_TYPE=${APK_TYPE:-free-arm64-v8a}
ENV GRADLE_OPTS="-Xmx2048m -Dorg.gradle.daemon=false"

ENV ANDROID_SDK_ROOT=/sdk
ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/34.0.0:${PATH}"

# Install required packages
RUN apt-get update && apt-get install -y \
    git curl unzip zip wget zipalign python3 python3-pip ca-certificates \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Android SDK command-line tools
RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools && \
    cd ${ANDROID_SDK_ROOT}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O sdk-tools.zip && \
    unzip sdk-tools.zip && rm sdk-tools.zip && \
    mv cmdline-tools latest

# Accept licenses and install build tools + platform
RUN yes | ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --licenses

RUN ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;35.0.0"

# Create working directory
WORKDIR /app

COPY . /app

RUN git checkout ${VERSION}

RUN chmod +x apkdiff.py && cp apkdiff.py /usr/local/bin/apkdiff.py

# Ensure gradlew is executable
RUN chmod +x gradlew

# Build the APK using the project's Gradle Wrapper
RUN ./gradlew clean assembleRelease --no-daemon

# Create a self-signed release keystore
RUN keytool -genkeypair \
    -alias amberkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -keystore /app/release.keystore \
    -storepass password \
    -keypass password \
    -dname "CN=Amber, OU=Dev, O=AmberProject, L=Internet, S=None, C=US"

# Copy unsigned APK to known location
RUN cp app/build/outputs/apk/free/release/app-${APK_TYPE}-release-unsigned.apk /app/built.apk

# Sign the APK
RUN ${ANDROID_SDK_ROOT}/build-tools/35.0.0/apksigner sign \
    --ks /app/release.keystore \
    --ks-key-alias amberkey \
    --ks-pass pass:password \
    --key-pass pass:password \
    --out /app/signed.apk \
    /app/built.apk

# Download official release APK
RUN mkdir -p /release_apk && \
    wget -q -O /release_apk/Amber-${VERSION}.apk \
    https://github.com/greenart7c3/Amber/releases/download/${VERSION}/amber-${APK_TYPE}-${VERSION}.apk

# Strip META-INF signature and LICENSE files from both APKs
RUN mkdir -p /tmp/signed /tmp/release && \
    unzip -q /app/signed.apk -d /tmp/signed && \
    unzip -q /release_apk/Amber-${VERSION}.apk -d /tmp/release && \
    find /tmp/signed -name "*.RSA" -delete && \
    find /tmp/signed -name "*.SF" -delete && \
    find /tmp/signed -name "*.DSA" -delete && \
    find /tmp/signed -name "*LICENSE*" -delete && \
    find /tmp/release -name "*.RSA" -delete && \
    find /tmp/release -name "*.SF" -delete && \
    find /tmp/release -name "*.DSA" -delete && \
    find /tmp/release -name "*LICENSE*" -delete && \
    cd /tmp/signed && zip -qr /app/signed-stripped.apk . && \
    cd /tmp/release && zip -qr /release_apk/official-stripped.apk .

# Compare the stripped APKs
CMD sh -c "python3 /usr/local/bin/apkdiff.py /release_apk/official-stripped.apk /app/signed-stripped.apk"
