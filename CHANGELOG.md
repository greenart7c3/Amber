# Changelog

## Amber 7.0.0-pre5

- New: let `amber login -` read the bunker URI from stdin, so the connection secret stays out of the process list and shell history
- New: send crash reports anonymously if you prefer, and report out-of-memory crashes and silent deaths that were lost before
- Make `amber status` exit with the real cause when it cannot reach the signer or is refused, and write `"ok":false` in `--json` while offline or waiting for approval
- Keep the CLI's key store backend once chosen, so a Secret Service starting later no longer locks out saved connections, and let it be set explicitly
- Publish error replies to NIP-46 senders Amber has not verified only once
- Fix `amber connect` timing out when the signer approves before it subscribed for the answer
- Fix the desktop local bunker relay forwarding duplicate ephemeral events twice
- Rate limit `nip44v3_encrypt`, `nip44v3_decrypt` and `sign_psbt` intents like the other request types
- Keep the CLI's key store marker file private (0600)

Download it with [Zapstore](https://zapstore.dev/apps/com.greenart7c3.nostrsigner), [Obtainium](https://github.com/ImranR98/Obtainium), [f-droid](https://f-droid.org/packages/com.greenart7c3.nostrsigner) or download it directly in the [releases page](https://github.com/greenart7c3/Amber/releases/tag/v7.0.0-pre5)

If you like my work consider making a [donation](https://greenart7c3.com)

## Verifying the release

In order to verify the release, you'll need to have `gpg` or `gpg2` installed on your system. Once you've obtained a copy (and hopefully verified that as well), you'll first need to import the keys that have signed this release if you haven't done so already:

``` bash
gpg --keyserver hkps://keys.openpgp.org --recv-keys 44F0AAEB77F373747E3D5444885822EED3A26A6D
```

Once you have his PGP key you can verify the release (assuming `manifest-v7.0.0-pre5.txt` and `manifest-v7.0.0-pre5.txt.sig` are in the current directory) with:

``` bash
gpg --verify manifest-v7.0.0-pre5.txt.sig manifest-v7.0.0-pre5.txt
```

You should see the following if the verification was successful:

``` bash
gpg: Signature made Fri 13 Sep 2024 08:06:52 AM -03
gpg:                using RSA key 44F0AAEB77F373747E3D5444885822EED3A26A6D
gpg: Good signature from "greenart7c3 <greenart7c3@proton.me>"
```

That will verify the signature on the main manifest page which ensures integrity and authenticity of the binaries you've downloaded locally. Next, depending on your operating system you should then re-calculate the sha256 sum of the binary, and compare that with the following hashes:

``` bash
cat manifest-v7.0.0-pre5.txt
```

## Older versions

Release notes for previous versions live in [docs/changelogs](docs/changelogs/README.md).
