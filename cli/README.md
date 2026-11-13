# Amber CLI

A command-line NIP-46 client. It lets any script, app or agent on your computer
ask Amber to sign a Nostr event and get the signed event back.

It **holds no accounts and no keys of yours**: it only keeps the connections you
paired with Amber, and Amber (the desktop app on this computer, or the app on
your phone) decides every request with its normal approval and permission flow.

## Install

Every [release](../../releases) carries a self-contained archive for Linux,
macOS and Windows that needs **no Java installed**:

```bash
tar xzf amber-cli-<version>-linux-x86_64.tar.gz     # or the .zip on Windows
./amber-cli/bin/amber --version
# optional, to run it as `amber`: keep the directory, link the launcher
mkdir -p ~/.local/opt ~/.local/bin
mv amber-cli ~/.local/opt/amber-cli
ln -s ~/.local/opt/amber-cli/bin/amber ~/.local/bin/amber
```

The launcher runs the Java runtime bundled beside it in `lib/`, so a copy of
`bin/amber` on its own cannot start; a symlink works, because the launcher
follows it back into the directory.

macOS: the app is ad-hoc signed and not notarized, so a browser-downloaded copy
is quarantined; `xattr -dr com.apple.quarantine amber-cli.app` (or use the
binary inside `amber-cli.app/Contents/MacOS/amber`) clears it.

From source: `./gradlew :cli:packageCli` builds the same archive into
`cli/build/distributions/`, and `./gradlew :cli:run --args="help"` runs it
without packaging.

## Pairing (once, needs a human)

**With a bunker URI** — in Amber, open the connection's details, copy the
`bunker://` URI, then:

```bash
amber login 'bunker://<connection-pubkey>?relay=wss://...&secret=...'
```

The URI contains the connection's secret. On a shared machine, pass `-` and
pipe it in instead, so the secret stays out of the process list and your shell
history:

```bash
amber login - < bunker-uri.txt
```

**Without one** — let the CLI print a `nostrconnect://` URI and a QR code, then
paste or scan it into Amber and approve the connection there:

```bash
amber connect --qr
# ask up front for what the scripts need, so later requests do not prompt:
amber connect --perms 'sign_event:1,sign_event:30023,nip44_encrypt'
```

Both save the connection and print the hex public key Amber signs with. Use
`--profile NAME` (or `AMBER_PROFILE`) to keep several connections apart, e.g. one
for work scripts and one for an agent.

## Commands

| Command | What it does |
| --- | --- |
| `amber connect [--relay URL]... [--name N] [--perms LIST] [--qr] [--open]` | print a `nostrconnect://` URI/QR, wait for Amber's approval, save the connection |
| `amber login <bunker://…>` | pair with a bunker URI from Amber |
| `amber sign-event [JSON \| -]` | sign an event, print the signed event |
| `amber get-public-key` | print the hex key Amber signs with |
| `amber nip44-encrypt\|nip44-decrypt\|nip04-encrypt\|nip04-decrypt <pubkey> [TEXT \| -]` | NIP-44 / NIP-04 with anyone |
| `amber ping` | check that Amber answers |
| `amber status` | the saved connection and whether it answers right now |
| `amber logout` | NIP-46 logout and delete the saved connection |
| `amber help [command]` | the full reference, also written for agents |

Global flags: `--profile NAME`, `--timeout SECONDS` (default 120: an approval
may need a human), `--json` (one result object), `--verbose`.

```bash
amber sign-event '{"kind":1,"content":"hello","tags":[]}'
echo '{"kind":30023,"content":"# post","tags":[]}' | amber sign-event -
amber --json get-public-key            # {"ok":true,"result":"<hex>"}
```

Results go to stdout and nothing else; instructions, warnings and QR codes go to
stderr, so `amber sign-event ... | jq .` just works.

## Exit codes

| Code | Meaning |
| --- | --- |
| 0 | success |
| 1 | error: invalid answer, broken saved file, I/O |
| 2 | bad usage: unknown command or option, missing input, invalid JSON |
| 3 | not paired for this profile (`login` or `connect` first) |
| 4 | refused: rejected in Amber, or Amber has no grant for it |
| 5 | timeout: Amber offline, or still waiting for a human |
| 6 | unreachable: no relay accepted a connection |

## Where state lives

`$AMBER_CLI_HOME`, or `~/.config/amber-cli` (`%APPDATA%\amber-cli` on Windows),
holding `connections.json` encrypted with AES-256-GCM. The key lives in the OS
credential store — macOS Keychain, Windows Credential Manager, a freedesktop
Secret Service — and, on a host with none running, in a `0600` key file beside
it. Another app cannot read the saved client keys; and those keys alone cannot
sign anything, because Amber approves every request.

Relays for `connect` default to the same list Amber itself uses
(`wss://auth.nostr1.com/`, `wss://bucket.coracle.social/`,
`wss://nrs.primal.net/`, `wss://relay.nip46.com/`), plus the local relay of a
running Amber (`ws://127.0.0.1:7777`, or `:7778` for a debug build) when that
port answers, so pairing and signing work with no public relay at all.

## Troubleshooting

- **Code 5 / "a human may need to approve"** — open Amber, approve the pending
  request (or grant it "always"), then run the command again. Pairing with
  `--perms` and remembering approvals in Amber avoids the prompts.
- **Code 4** — the request was rejected, or the connection was deleted in Amber.
  Pair again with `amber login` / `amber connect --force`.
- **Code 6** — no relay answered. Check `--relay` values, and that Amber is
  running with its relays connected.
- **`pure virtual method called` on stderr on some Linux hosts** — a message
  from the JDK's `jpackage` launcher on certain distributions, printed before
  the CLI runs; the command still works and exits with its real code.
