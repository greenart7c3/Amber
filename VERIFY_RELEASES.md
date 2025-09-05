# Verifying Amber Releases

## Overview

All Amber releases are cryptographically signed using GPG to ensure integrity and authenticity. This guide explains how to verify that the releases you download are genuine and haven't been tampered with.

## Prerequisites

You'll need to have `gpg` or `gpg2` installed on your system:

- **Ubuntu/Debian**: `sudo apt install gnupg`
- **macOS**: `brew install gnupg`
- **Windows**: Install [Gpg4win](https://www.gpg4win.org/)

## Step 1: Import the Signing Key

First, import the GPG key used to sign Amber releases:

```bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys C00C563A025C327C95DF036AEE1FA70568414E2A
```

You should see output similar to:
```
gpg: key EE1FA70568414E2A: public key "auggie (this) <auggie@pm.me>" imported
gpg: Total number processed: 1
gpg:               imported: 1
```

## Step 2: Verify the Key Fingerprint

**IMPORTANT**: Always verify the key fingerprint matches exactly:

```bash
gpg --fingerprint C00C563A025C327C95DF036AEE1FA70568414E2A
```

The output should show:
```
pub   rsa3072 2025-09-04 [SC]
      C00C 563A 025C 327C 95DF  036A EE1F A705 6841 4E2A
uid           [unknown] auggie (this) <auggie@pm.me>
sub   rsa3072 2025-09-04 [E]
```

**Verify the fingerprint**: `C00C 563A 025C 327C 95DF 036A EE1F A705 6841 4E2A`

If the fingerprint doesn't match exactly, **DO NOT PROCEED** - the key may be compromised.

## Step 3: Download Release Files

For each release, you need to download:
1. The APK/AAB files you want to use
2. The manifest file (e.g., `manifest-v1.0.0.txt`)
3. The signature file (e.g., `manifest-v1.0.0.txt.sig`)

Example for version v1.0.0:
```bash
# Download the files you need
wget https://github.com/yourusername/Amber/releases/download/v1.0.0/amber-free-universal-v1.0.0.apk
wget https://github.com/yourusername/Amber/releases/download/v1.0.0/manifest-v1.0.0.txt
wget https://github.com/yourusername/Amber/releases/download/v1.0.0/manifest-v1.0.0.txt.sig
```

## Step 4: Verify the Manifest Signature

Verify that the manifest file is authentic:

```bash
gpg --verify manifest-v1.0.0.txt.sig manifest-v1.0.0.txt
```

You should see output like:
```
gpg: Signature made Wed 04 Sep 2025 12:00:00 PM UTC
gpg:                using RSA key C00C563A025C327C95DF036AEE1FA70568414E2A
gpg: Good signature from "auggie (this) <auggie@pm.me>"
gpg: WARNING: This key is not certified with a trusted signature!
gpg:          There is no indication that the signature belongs to the owner.
Primary key fingerprint: C00C 563A 025C 327C 95DF  036A EE1F A705 6841 4E2A
```

The **"Good signature"** message is what you're looking for. The warning about the key not being certified is normal unless you've explicitly trusted the key.

## Step 5: Verify File Integrity

Check that your downloaded files match the checksums in the manifest:

```bash
# View the manifest contents
cat manifest-v1.0.0.txt

# Check the SHA256 hash of your downloaded file
sha256sum amber-free-universal-v1.0.0.apk
```

Compare the output hash with the corresponding hash in the manifest file. They must match exactly.

### Automated Verification Script

You can use this script to automate the verification process:

```bash
#!/bin/bash
# verify-amber.sh

VERSION=${1:-"v1.0.0"}
FILE=${2:-"amber-free-universal-${VERSION}.apk"}

echo "Verifying Amber release ${VERSION}..."

# Download manifest and signature if not present
if [ ! -f "manifest-${VERSION}.txt" ]; then
    wget "https://github.com/yourusername/Amber/releases/download/${VERSION}/manifest-${VERSION}.txt"
fi

if [ ! -f "manifest-${VERSION}.txt.sig" ]; then
    wget "https://github.com/yourusername/Amber/releases/download/${VERSION}/manifest-${VERSION}.txt.sig"
fi

# Verify signature
echo "Verifying GPG signature..."
if gpg --verify "manifest-${VERSION}.txt.sig" "manifest-${VERSION}.txt"; then
    echo "✓ GPG signature is valid"
else
    echo "✗ GPG signature verification failed!"
    exit 1
fi

# Verify file hash if file exists
if [ -f "$FILE" ]; then
    echo "Verifying file integrity..."
    EXPECTED_HASH=$(grep "$FILE" "manifest-${VERSION}.txt" | cut -d' ' -f1)
    ACTUAL_HASH=$(sha256sum "$FILE" | cut -d' ' -f1)
    
    if [ "$EXPECTED_HASH" = "$ACTUAL_HASH" ]; then
        echo "✓ File integrity verified: $FILE"
    else
        echo "✗ File integrity check failed for: $FILE"
        echo "Expected: $EXPECTED_HASH"
        echo "Actual:   $ACTUAL_HASH"
        exit 1
    fi
else
    echo "File $FILE not found - please download it first"
fi

echo "✓ All verification checks passed!"
```

Make it executable and use it:
```bash
chmod +x verify-amber.sh
./verify-amber.sh v1.0.0 amber-free-universal-v1.0.0.apk
```

## Trusting the Key (Optional)

If you want to suppress the "key not certified" warning and you've verified the key fingerprint through a trusted channel, you can sign the key locally:

```bash
gpg --sign-key C00C563A025C327C95DF036AEE1FA70568414E2A
```

## Security Notes

- **Always verify the key fingerprint** before trusting any signatures
- **Only download releases from the official GitHub repository**
- **Verify both the GPG signature AND the file hashes**
- **Keep your GPG software up to date**
- If verification fails at any step, **do not use the file** and report it as a potential security issue

## Troubleshooting

### "gpg: Can't check signature: No public key"
You need to import the signing key first (see Step 1).

### "gpg: BAD signature"
The file has been tampered with or corrupted. **Do not use it**.

### Hash mismatch
The downloaded file doesn't match the expected checksum. Re-download the file or try a different mirror.

For more help, please open an issue on the GitHub repository.