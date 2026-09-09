# Sync, flags, and configuration

Read when syncing refs, choosing flags, or tuning git config.
Guides: https://ngit.dev/configuration and https://ngit.dev/troubleshooting

## Sync

```bash
ngit sync --json                  # make git servers reflect the Nostr state for every ref
ngit sync --ref-name main --json  # one ref
```

## Global flags

These accept any command position. `--offline` is per command; check
`ngit <command> --help`.

| Flag | Description |
| ---- | ----------- |
| `--json` | One JSON document on stdout (ngit commands only) |
| `-d`, `--defaults` | Non-interactive; accept defaults |
| `-q`, `--quiet` | Hide non-essential stderr progress (not combinable with `-v`) |
| `--repo <REMOTE\|NADDR\|NOSTR-URL>` | Select the target repository |
| `--signer <ALIAS\|NPUB\|NAME>` | Use a stored signer for this command |
| `--nsec-file`, `--nbunksec-file <PATH>` | One-shot key or bunker session from a private file (`--nsec`, `--nbunksec` take inline values) |
| `--repo-relay-only` | Publish only to repository relays |
| `-f`, `--force` | Bypass safety guards |

## git config

```bash
ngit --customize                                # list every option
git config nostr.signer alice                   # repository signer, including for git push
git config nostr.signer-alias.alice npub1...    # portable alias-to-npub mapping
git config nostr.secret-storage file            # auto | file | git-config
git config nostr.repo-relay-only true
git config nostr.auto-pr-branches true          # fetch every open and draft PR as a pr/* branch (default false)
git config nostr.http-io-timeout-ms 600000      # allow large grasp pushes
NGIT_CACHE_DIR=/writable/path ngit repo --json  # override the global event-cache directory
```

`nostr.auto-pr-branches` follows normal git config precedence. With the
default `false`, PRs appear as branches only after `ngit pr checkout`; run
`git fetch --prune` once to drop branches fetched by an older version, or use
`git clone --config nostr.auto-pr-branches=true <nostr-url>` to opt in from
the start.

If the global cache directory is unavailable ngit falls back to an in-memory
cache; the repository's git common directory must still be writable.
