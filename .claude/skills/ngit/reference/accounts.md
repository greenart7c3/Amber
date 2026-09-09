# Accounts — identity, login, secrets

Read when managing accounts, logins, or credential storage.
Guide: https://ngit.dev/accounts (storage modes, pairing a remote signer for
CI, rotation).

```bash
ngit account whoami --json --offline            # every usable signer with npub, aliases, scope, active state; `account list` is an alias
ngit account login                              # interactive; the secret goes to the OS credential store or ngit's user-only file store
ngit account login alice                        # make a stored identity the global default (alias, npub, or exact profile name)
ngit account login --local alice                # …this repository's default, including for git push
ngit account login --nsec-file /private/key --alias alice
ngit account login --nbunksec-file /private/connection --alias alice   # store an established NIP-46 session
ngit account login --bunker-url bunker://...    # pair a remote signer
ngit account login --local -i --alias alice     # pair a fresh signer and assign or replace this alias
ngit account login --secret-storage file        # bypass the OS store; `git-config` stores plaintext and must be explicit
ngit account create --name "Alice" --json
ngit account export-keys --secret               # print only the nsec or nbunksec; --json adds the npub
ngit account logout --json                      # keeps stored keys; add --forget to delete the secret
ngit account forget-keys <entry> --json
ngit --signer alice issue create --subject "Bug" --body "Details" --json   # one ngit command as alice
git -c nostr.signer=alice push origin pr/topic  # one git command as alice
ngit --nsec-file /private/key <command>         # one-shot key for CI; --nbunksec-file for a bunker session
```

**Storage.** `auto` (OS store, then the file store), `file`, or `git-config`,
selected with `--secret-storage`, `NGIT_SECRET_STORAGE`, or
`nostr.secret-storage`. Git config holds the credential entry name, not the
secret. Existing plaintext values keep working.

**Selection.** `--signer` and `nostr.signer` accept an alias, an npub, or an
exact cached profile name. A profile name must match exactly one account that
holds stored credentials, and only the resolved npub is persisted. Selection
fails closed when the selector is missing, ambiguous, or backed by invalid
credentials.

**Several NIP-46 connections for one npub.** The first connection stays the
default selected by the bare npub; ngit refuses to replace it with another
unaliased connection. Log in with `--alias` to keep an extra connection and
select it by alias. Older ngit versions ignore the exact-session binding and
select the identity's default connection.

**nbunksec** is a portable established connection: remote-signer pubkey,
client secret, relays, and optional pairing secret. It holds no npub, so
one-shot use resolves the identity from the signer. The `--nbunksec-file` and
`--nsec-file` forms keep secrets out of process arguments. A fresh pairing
needs interactive approval, so unattended runs use a stored connection.
