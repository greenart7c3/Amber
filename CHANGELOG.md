## Amber 6.2.2

- Show native app icons and NIP-46 client metadata on the request screens and app list
- Persist client metadata on every connect and capture the native app icon and name at connect/accept time
- Add support for event kind 30618
- Add an export/share button to the Logs screen
- Separate default and connection relays in the Active relays screen, and use default relays for new relay-delivered bunker connections
- Stop logging decrypted NIP-46 request/response bodies and store encrypt/decrypt payloads as ciphertext, decrypting on demand
- Gate all logcat output behind BuildConfig.DEBUG
- Force always-ask for null-package (browser) callers
- Flag nsec, ncryptsec and seed words clipboard copies as sensitive and clear them after a delay
- Add explicit backup/data-extraction excludes as defense-in-depth
- Fix a crash from nested scrolling in the Active relays screen
- Fix a LazyColumn duplicate key crash from racy bunker request dedup
- Fix the release update check missing a file subscription on an EOSE race
- Fix the overlapping feedback type selector with long labels
- Warm up the Coil ImageLoader off the main thread
- Update translations

Download it with [Zapstore](https://zapstore.dev/apps/com.greenart7c3.nostrsigner), [Obtainium](https://github.com/ImranR98/Obtainium), [f-droid](https://f-droid.org/packages/com.greenart7c3.nostrsigner) or download it directly in the [releases page](https://github.com/greenart7c3/Amber/releases/tag/v6.2.2)

If you like my work consider making a [donation](https://greenart7c3.com)

## Amber 6.2.1

- Reduce battery drain from relay reconnects and websocket pings
- Drop dead relays from the subscription pool instead of only backing off reconnects
- Do not wake the device when updating the relay notification
- Modernize the settings screen with grouped Material 3 cards and distinct icons
- Fix navigation crash when opening application permissions
- Fix a crash when writing the Bunker connect screen state off the main thread
- Reply with an error for invalid bunker request methods
- Add NIP-46 logout method support
- Add support for event kind 39701 (Public web bookmark)
- Fix a per-account database connection leak by building databases atomically
- Refresh app bar titles when the language changes
- Update Kotlin to 2.4.0 and Gradle to 9.5.1
- Update translations

Download it with [Zapstore](https://zapstore.dev/apps/com.greenart7c3.nostrsigner), [Obtainium](https://github.com/ImranR98/Obtainium), [f-droid](https://f-droid.org/packages/com.greenart7c3.nostrsigner) or download it directly in the [releases page](https://github.com/greenart7c3/Amber/releases/tag/v6.2.1)

If you like my work consider making a [donation](https://greenart7c3.com)

## Verifying the release

In order to verify the release, you'll need to have `gpg` or `gpg2` installed on your system. Once you've obtained a copy (and hopefully verified that as well), you'll first need to import the keys that have signed this release if you haven't done so already:

``` bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 44F0AAEB77F373747E3D5444885822EED3A26A6D
```

Once you have his PGP key you can verify the release (assuming `manifest-v6.2.1.txt` and `manifest-v6.2.1.txt.sig` are in the current directory) with:

``` bash
gpg --verify manifest-v6.2.1.txt.sig manifest-v6.2.1.txt
```

You should see the following if the verification was successful:

``` bash
gpg: Signature made Fri 13 Sep 2024 08:06:52 AM -03
gpg:                using RSA key 44F0AAEB77F373747E3D5444885822EED3A26A6D
gpg: Good signature from "greenart7c3 <greenart7c3@proton.me>"
```

That will verify the signature on the main manifest page which ensures integrity and authenticity of the binaries you've downloaded locally. Next, depending on your operating system you should then re-calculate the sha256 sum of the binary, and compare that with the following hashes:

``` bash
cat manifest-v6.2.1.txt
```

One can use the `shasum -a 256 <file name here>` tool in order to re-compute the `sha256` hash of the target binary for your operating system. The produced hash should be compared with the hashes listed above and they should match exactly.

## Amber 6.2.0

- Add NIP-44 v3 encryption support, including a dedicated approval screen, intent preview, bunker preview, history logging and auto-reject for invalid requests
- Register NIP-44 v3 ContentProvider authorities
- Auto-accept NIP-46 ping requests on connect
- Ignore empty `nostrsigner:` intents so the app can be opened directly
- Simplify the invalid intent screen to only close the app
- Use a segmented toggle for option pickers, with a scrollbar and shrinking segments when they get too narrow to fit the screen
- Remove the `sign_message` signer method
- Remove the 1 minute option from the sign-automatically pickers
- Disable resource shrinking in release builds
- Update translations

Download it with [Zapstore](https://zapstore.dev/apps/com.greenart7c3.nostrsigner), [Obtainium](https://github.com/ImranR98/Obtainium), [f-droid](https://f-droid.org/packages/com.greenart7c3.nostrsigner) or download it directly in the [releases page](https://github.com/greenart7c3/Amber/releases/tag/v6.2.0)

If you like my work consider making a [donation](https://greenart7c3.com)

## Verifying the release

In order to verify the release, you'll need to have `gpg` or `gpg2` installed on your system. Once you've obtained a copy (and hopefully verified that as well), you'll first need to import the keys that have signed this release if you haven't done so already:

``` bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 44F0AAEB77F373747E3D5444885822EED3A26A6D
```

Once you have his PGP key you can verify the release (assuming `manifest-v6.2.0.txt` and `manifest-v6.2.0.txt.sig` are in the current directory) with:

``` bash
gpg --verify manifest-v6.2.0.txt.sig manifest-v6.2.0.txt
```

You should see the following if the verification was successful:

``` bash
gpg: Signature made Fri 13 Sep 2024 08:06:52 AM -03
gpg:                using RSA key 44F0AAEB77F373747E3D5444885822EED3A26A6D
gpg: Good signature from "greenart7c3 <greenart7c3@proton.me>"
```

That will verify the signature on the main manifest page which ensures integrity and authenticity of the binaries you've downloaded locally. Next, depending on your operating system you should then re-calculate the sha256 sum of the binary, and compare that with the following hashes:

``` bash
cat manifest-v6.2.0.txt
```

One can use the `shasum -a 256 <file name here>` tool in order to re-compute the `sha256` hash of the target binary for your operating system. The produced hash should be compared with the hashes listed above and they should match exactly.

## Amber 6.1.0

- Better layout when connecting a new app
- Fix some reported crashes
- Fix signer dialog not closing after accepting a bunker request
- Show name and npub when showing your account
- Add a select/deselect all option in the permissions screen when connecting a new app
- Show a invalid request screen when receiving a invalid request
- Preview missing translation report before sending it
- Add a rate limiting for intents based on app/type/event kind (rate limiting only applies to apps that don't implement sending multiple requests at once)
- Some optimizations when accepting/rejecting intent requests
- Added a stop service in the notification, this force closes the app and you have to manually open it again before using bunker applications
- Added a option to disable the service start on boot
- Only start the profile subscription for the current account
- Always return hex key when logging in to an app to comply with nip 55
- Added a close button in the empty requests screen
- Added loading state to the report screens
- Support for beta releases for the auto updater
- Add a reset button for bunkers
- Fix a connection issue when connecting to a new bunker by @npub1q3sle0kvfsehgsuexttt3ugjd8xdklxfwwkh559wxckmzddywnws6cd26p
- Fix app starting on boot when not enabled
- Support for sign psbt method
- Fix relay auth whitelist when the relay contains port number
- Change selectable options to be a bottom sheet
- Added more options to the automatically sign this for
- Add an encrypted applications backup that can be restored on a new device

Download it with [Zapstore](https://zapstore.dev/apps/com.greenart7c3.nostrsigner), [Obtainium](https://github.com/ImranR98/Obtainium), [f-droid](https://f-droid.org/packages/com.greenart7c3.nostrsigner) or download it directly in the [releases page](https://github.com/greenart7c3/Amber/releases/tag/v6.1.0)

If you like my work consider making a [donation](https://greenart7c3.com)

## Verifying the release

In order to verify the release, you'll need to have `gpg` or `gpg2` installed on your system. Once you've obtained a copy (and hopefully verified that as well), you'll first need to import the keys that have signed this release if you haven't done so already:

``` bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 44F0AAEB77F373747E3D5444885822EED3A26A6D
```

Once you have his PGP key you can verify the release (assuming `manifest-v6.1.0.txt` and `manifest-v6.1.0.txt.sig` are in the current directory) with:

``` bash
gpg --verify manifest-v6.1.0.txt.sig manifest-v6.1.0.txt
```

You should see the following if the verification was successful:

``` bash
gpg: Signature made Fri 13 Sep 2024 08:06:52 AM -03
gpg:                using RSA key 44F0AAEB77F373747E3D5444885822EED3A26A6D
gpg: Good signature from "greenart7c3 <greenart7c3@proton.me>"
```

That will verify the signature on the main manifest page which ensures integrity and authenticity of the binaries you've downloaded locally. Next, depending on your operating system you should then re-calculate the sha256 sum of the binary, and compare that with the following hashes:

``` bash
cat manifest-v6.1.0.txt
```

One can use the `shasum -a 256 <file name here>` tool in order to re-compute the `sha256` hash of the target binary for your operating system. The produced hash should be compared with the hashes listed above and they should match exactly.
