# Amber: Nostr event signer for Android

Amber is a nostr event signer for Android. It allows users to keep their nsec segregated in a single, dedicated app. The goal of Amber is to have your smartphone act as a NIP-46 signing device without any need for servers or additional hardware. "Private keys should be exposed to as few systems as possible as each system adds to the attack surface," as the rationale of said NIP states. In addition to native apps, Amber aims to support all current nostr web applications without requiring any extensions or web servers.

<div align="center">

[![GitHub downloads](https://img.shields.io/github/downloads/greenart7c3/Amber/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/greenart7c3/Amber/releases)
[![Last Version](https://img.shields.io/github/release/greenart7c3/Amber.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/greenart7c3/Amber)
[![CI](https://img.shields.io/github/actions/workflow/status/greenart7c3/Amber/build.yml?labelColor=27303D)](https://github.com/greenart7c3/Amber/actions/workflows/build.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/greenart7c3/Amber?labelColor=27303D&color=0877d2)](/LICENSE)

</div>

# Desktop

Amber also runs on Windows, macOS and Linux as a NIP-46 signer built with Compose for Desktop. It supports the same accounts, permissions and bunker connections as the Android app (minus NIP-55, which is Android-only IPC). See [desktop/README.md](desktop/README.md) for build and usage instructions.

# Current Features

- [x] Offline
- [x] Use nip-46 or make an addendum in nip-46
- [x] Improve the ui (currently its showing a text with the raw json of the event)
- [x] Check if we can use Amber to sign the events of web applications
- [x] Change the sign button to just copy the signature of the event
- [x] Use content provider to sign events in background when you checked the remember my choice option on android
- [x] Support for multiple accounts

## Download and Install

[<img src="./assets/zapstore.svg"
alt="Get it on Zap Store"
height="70">](https://zapstore.dev/apps/naddr1qvzqqqr7pvpzqateqake4lc2fn77lflzq30jfpk8uhvtccalc66989er8cdmljceqqdkxmmd9enhyet9deshyaphvvejumn0wd68yumfvahx2usx8zmj2)
[<img src="./assets/obtainium.png"
alt="Get it on Obtaininum"
height="70">](https://github.com/ImranR98/Obtainium)
[<img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub"
height="70">](https://github.com/greenart7c3/Amber/releases)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="70">](https://f-droid.org/packages/com.greenart7c3.nostrsigner/)

# Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. If an AI coding assistant wrote a large part of your change, also read [CONTRIBUTING-WITH-AI.md](CONTRIBUTING-WITH-AI.md).

Issues can be logged on: [https://gitworkshop.dev/greenart7c3@greenart7c3.com/Amber](https://gitworkshop.dev/greenart7c3@greenart7c3.com/Amber)

[GitHub issues](https://github.com/greenart7c3/Amber/issues) and [pull requests](https://github.com/greenart7c3/Amber/pulls) here are also welcome. Translations can be provided by editing the `strings.xml` files under `app/src/main/res/values-<locale>/` and opening a pull request.

You can also send patches through Nostr using [GitStr](https://github.com/fiatjaf/gitstr) to [this nostr address](https://patch34.pages.dev/naddr1qvzqqqrhnypzqateqake4lc2fn77lflzq30jfpk8uhvtccalc66989er8cdmljceqqz5zmtzv4eqsrpqjs)

By contributing to this repository, you agree to license your work under the MIT license. Any work contributed where you are not the original author must contain its license header with the original author(s) and source.

# Development with Codex Web

This repository includes reusable Codex Web environment scripts so cloud tasks can bootstrap the Android build without manual setup. In the Codex Web environment settings, use:

```bash
bash .codex/setup.sh
```

as the setup script, and optionally use:

```bash
bash .codex/maintenance.sh
```

as the maintenance script for cached containers. The setup script installs or verifies Java 21, bootstraps the Android command-line SDK, installs Android API 36/build-tools 36.0.0, accepts SDK licenses, and warms Gradle dependencies for the default free debug unit-test sources.

# Security and Verification

🔐 **All releases are cryptographically signed with GPG for your security.**

Before installing any APK from our releases, we strongly recommend verifying its authenticity to ensure it hasn't been tampered with.

**[📋 View Release Verification Guide](VERIFY_RELEASES.md)**

The verification process involves:
1. Importing our GPG public key
2. Verifying the release manifest signature
3. Checking file integrity with SHA256 hashes

**GPG Key Details:**
- **Key ID**: `44F0AAEB77F373747E3D5444885822EED3A26A6D`
- **Fingerprint**: `44F0 AAEB 77F3 7374 7E3D  5444 8858 22EE D3A2 6A6D`
- **User ID**: `greenart7c3 <greenart7c3@proton.me>`

**Quick verification:**
```bash
# Import the signing key
gpg --keyserver hkps://keys.openpgp.org --recv-keys 44F0AAEB77F373747E3D5444885822EED3A26A6D

# Verify a release (example for v1.0.0)
gpg --verify manifest-v1.0.0.txt.sig manifest-v1.0.0.txt
```

**⚠️ Security Notice**: Only download releases from this official GitHub repository. If GPG verification fails, **do not install the APK** and report it as a security issue.

# Verifying Reproducibility of Amber

To confirm that the Amber build is reproducible, follow these steps:

1. Run the following command to build the image with no cache and specified version:

``` bash
docker build -t amber-repro --progress=plain --no-cache --build-arg VERSION=v4.0.2 --build-arg APK_TYPE=free-arm64-v8a .
```

2. After the image is built, run the container:

``` bash
docker run --rm amber-repro
```

3. You should see the following message indicating success:

``` bash
APKs match!
```

# Verifying Reproducibility of Amber Desktop

The Linux x86_64 downloads of the desktop app, `Amber-X.Y.Z-linux-x86_64.tar.xz` and `Amber-X.Y.Z-x86_64.AppImage`, can be rebuilt from the tag and compared with the published files:

1. The app ships the Java runtime of the JDK it was built with, so the rebuild must use the same Temurin release. Find it in the published tarball:

``` bash
tar -xJOf Amber-X.Y.Z-linux-x86_64.tar.xz Amber/lib/runtime/lib/server/libjvm.so | grep -a -o -m1 'Server VM ([^)]*)'
```

`Server VM (21.0.12.1+1-LTS)` means `JDK=21.0.12.1_1` (drop `-LTS`, `+` becomes `_`). If you skip this step and the default is wrong, the build stops and tells you which one to use.

2. Build the image with no cache, the release tag and that JDK:

``` bash
docker build -f Dockerfile.desktop -t amber-desktop-repro --progress=plain --no-cache --build-arg VERSION=vX.Y.Z --build-arg JDK=21.0.12.1_1 .
```

3. Run the container:

``` bash
docker run --rm amber-desktop-repro
```

4. You should see the following message twice, once for the tarball and once for the AppImage:

``` bash
Builds match!
```

`desktopdiff.py` compares what is inside the downloads: every path, file, symlink and executable bit must match. Timestamps and owners are not compared, since they depend on the machine that packed the archive. The AppImage is unpacked with `unsquashfs` and never run.

The `.deb`, `.rpm`, `.msi`, `.exe` and `.dmg` installers are not reproducible yet. jpackage builds each of them separately, and the Compose plugin stamps one jar inside them (`skiko-awt-runtime-*.jar`) with the build time. That is fixed in Compose 1.13.

# Certificate fingerprint for AppVerifier

``` bash
com.greenart7c3.nostrsigner
E8:AB:8C:69:33:3B:68:63:6D:D4:6C:E2:42:40:8C:79:55:3A:7F:D9:05:5D:05:4D:61:DA:AB:AB:AD:A5:3B:BF
```

## fdroid

``` bash
com.greenart7c3.nostrsigner
56:DC:63:19:96:A5:5C:22:84:79:04:48:C7:DC:9F:1D:D0:5D:F5:96:B2:CE:48:82:31:36:33:F5:60:2E:5F:E4
```

# Usage

Check [NIP 55](https://github.com/nostr-protocol/nips/blob/master/55.md) and [NIP 46](https://github.com/nostr-protocol/nips/blob/master/46.md) for more information.

# Contributors

<a align="center" href="https://github.com/greenart7c3/amber/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=greenart7c3/amber" />
</a>
