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
    "build-tools;35.0.0" \
    "ndk;28.2.13676358"

# Create working directory
WORKDIR /app

COPY . /app

# Install the verifier before checking out the release: an older tag ships an
# older apkdiff.py, and verification must use the current one.
# Install a copy so the checkout below stays clean (a mode change on the
# tracked file would make git refuse to switch to the tag).
RUN install -m 755 apkdiff.py /usr/local/bin/apkdiff.py

RUN git checkout ${VERSION}

# Ensure gradlew is executable
RUN chmod +x gradlew

# Build the APK using the project's Gradle Wrapper
RUN ./gradlew clean assembleRelease --no-daemon

# Copy the unsigned APK to a known location (APK_TYPE starts with the flavor)
RUN cp app/build/outputs/apk/${APK_TYPE%%-*}/release/app-${APK_TYPE}-release-unsigned.apk /app/built.apk

# Download the official release APK. Since v6.3.0 the free flavor's assets
# no longer carry the "free-" prefix.
RUN mkdir -p /release_apk && \
    URL=https://github.com/greenart7c3/Amber/releases/download/${VERSION} && \
    { wget -q -O /release_apk/official.apk ${URL}/amber-${APK_TYPE}-${VERSION}.apk || \
      wget -q -O /release_apk/official.apk ${URL}/amber-${APK_TYPE#free-}-${VERSION}.apk; }

# Compare the APKs as published, without unzipping or re-zipping them:
# apkdiff.py ignores only the signing files, so an extra or duplicate entry
# in the official APK can't be hidden.
CMD ["python3", "/usr/local/bin/apkdiff.py", "/release_apk/official.apk", "/app/built.apk"]
