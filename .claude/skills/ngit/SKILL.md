---
name: ngit
description: Commands and workflows for NIP-34 git collaboration over Nostr with the ngit CLI and git-remote-nostr. Use in any repository with a nostr:// remote for generic collaboration requests (open an Issue, create or review a Pull Request (PR), comment, merge, clone) and whenever a task involves nostr:// URLs, ngit commands, Grasp servers, gitworkshop.dev, Nostr CI status and workflows, software releases and Zapstore publication, OCI container images, or nsite static sites published through Blossom.
license: CC-BY-SA-4.0
metadata:
  version: "1.17"
---

# ngit — Nostr Plugin for Git

ngit makes `git clone`, `fetch`, and `push` work with `nostr://` URLs and adds
a CLI for pull requests, issues, membership, CI results, releases, OCI
containers, and nsites. Repository state (which commit each ref points to) is
published as signed Nostr events and is the source of truth; git objects live
on ordinary git servers, so servers are interchangeable. A grasp server bundles
a relay and a git server and creates the repository automatically when an
announcement lists it. Explanation: https://ngit.dev/how-it-works

## Where to look

- **This skill documents ngit v3.** Run `ngit --version` first; the commands
  here need 3.0.0 or later. If ngit is missing or older, report that and
  offer an install or update: `curl -fsSL https://ngit.dev/install.sh | bash`
  installs or replaces `ngit` and `git-remote-nostr`, and `ngit update` works
  on v3 or later. Other methods: https://ngit.dev/install
- **`ngit <command> --help`** is the authority for the installed version's
  flags and defaults.
- **https://ngit.dev** holds the guides. Any page is available as raw
  Markdown at `https://ngit.dev/markdown/<route>.md`; the index is
  https://ngit.dev/llms.txt. Web UI: https://gitworkshop.dev

## Rules

- **Preserve user and repository choices.** Examples here show syntax, not
  policy. Carry through the selected signer, target, hosting, CI, and
  replication settings; do not add gates, waits, workflow edits, or
  configuration changes that the task or repository did not choose.
- **PR branches MUST start with `pr/`** (e.g. `pr/my-feature`). Any other
  branch name is a plain push and never creates a PR.
- **Read ngit output with `--json`.** It is a global option and works at any
  position. Stdout is exactly one JSON document; progress and diagnostics go
  to stderr. `git` commands have no `--json`. Top-level `command_status` is
  `ok` for exit 0 and `error` otherwise; it never describes a nested domain
  result (`ngit ci status --json` reports `ok` with
  `ci.conclusion: "failure"` unless a gate was requested).
- **Add `--offline` after the first network read** in a session, on commands
  that support it. `git fetch origin` also refreshes the cache.
- **Identifiers.** `<ID|nevent>` accepts `nevent1…`, a 64-char hex ID, or a
  unique hex prefix with an optional `#` (quote it: `'#deadbeef'`). JSON `id`
  and `reply_to` fields are already `nevent1…`; container publication instead
  returns a raw-hex `event_id` plus the repository `naddr`. Reference events
  inside `--body` text as `nostr:nevent1…` or `nostr:naddr1…`, never as raw
  hex. Never construct a NIP-05 address (`user@domain`); use `npub1…` unless
  a NIP-05 address was given to you.
- **Multiline text.** `ngit` options such as `--body` and `--description`
  accept real newlines: `--body "$(cat note.md)"`. Git push options cannot
  carry newlines: write literal `\n` in a short inline `-o description=…`, and
  never convert a file into a push option (open the PR with `ngit send`
  instead).
- **Signers.** `--signer <alias|npub|profile-name>` selects a stored identity
  for one `ngit` command; `git -c nostr.signer=<alias|npub|profile-name>
  push …` does the same for one git command. Neither changes the configured
  login. Never export or pass an nsec merely to switch between configured
  accounts.
- **CI.** A successful push says nothing about CI. Nostr CI runs workflows
  from `.ngit/act/workflows/`; another provider's directory is not evidence.
  When CI matters, query the exact commit or PR with
  `ngit ci status <target> --json` and read `ci.state` and `ci.conclusion`,
  not `command_status`.
- **Target repository.** With several `nostr://` remotes, pass global
  `--repo <REMOTE|NADDR|NOSTR-URL>`; a configured remote name, an naddr, and
  a nostr:// URL are all accepted. Without it ngit
  infers the target from config and branch tracking and fails rather than
  guesses. Before a signing command, check the
  `target repository: <naddr> (source: …)` line on stderr.

## Detecting a nostr repo

```bash
git remote -v | grep -q 'nostr://'   # primary check, no cache needed
ngit repo --json --offline            # full metadata when needed
```

`ngit repo` always exits 0, and `is_nostr_repo: false` can be a cold-cache
false negative: if a remote shows `nostr://`, run `git fetch origin` and
retry. The output includes the roster (`members`, `lead_source`, `lead_path`,
`pending_actions`, `health`); read `reference/repositories.md` before
changing membership or hosting.

## nostr:// URLs

```
nostr://<npub>/<identifier>
nostr://<npub>/<relay-hint>/<identifier>   # relay-hint is a bare domain, e.g. relay.ngit.dev
nostr://<user>@<domain>/<identifier>       # NIP-05, only when explicitly provided
nostr://<domain>/<repository-path>         # NIP-AD: the full /path is sent URL-encoded to /.well-known/nostr.json?path=
```

Standard git commands accept these URLs directly.

## Task index

Read the bundled reference before performing that slice of work; the guide
adds tutorials and background.

| Task | Bundled reference | ngit.dev guide |
| ---- | ----------------- | -------------- |
| Publish, clone, host repositories; maintainers, moderators | `reference/repositories.md` | `/repositories`, `/maintainers` |
| Open, update, stack, review, merge PRs | `reference/prs.md` | `/pull-requests` |
| Issues | `reference/issues.md` | `/issues` |
| CI results, trust, workflows, ngit in CI jobs | `reference/ci.md` | `/ci`, `/ci/workflows/` |
| Accounts, login, signers, secrets | `reference/accounts.md` | `/accounts` |
| Sync, global flags, git config | `reference/sync-config.md` | `/configuration`, `/troubleshooting` |
| Publish nsites (static sites) | `reference/nsites.md` | `/releases/nsites` |
| Publish OCI containers | `reference/containers.md` | `/releases` |
| Software releases | `ngit release --help` | `/releases` |
| Automation contract, machine-readable docs | this file | `/agents/` |
