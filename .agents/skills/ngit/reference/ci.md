# Nostr CI

Read when checking whether CI ran, interpreting a result, or writing a
workflow that uses ngit. Guides: https://ngit.dev/ci (coordinators, results,
trust, secrets) and https://ngit.dev/ci/workflows/ (what the coordinator
accepts, refuses, and adds compared with GitHub Actions).

## Workflows

Nostr CI (ngit-ci) runs workflows from `.ngit/act/workflows/` with
GitHub Actions syntax in Linux containers. `.github/workflows/` is run only by
GitHub Actions on a mirror; the directories are independent, so a check that
must run in both systems needs a file in each. ngit-ci refuses macOS and
Windows `runs-on` labels and job-level `uses:` (reusable workflows); composite
actions in steps work in both systems.

Read the workflow at the commit under investigation and confirm that its
triggers and steps cover the check in question:

```bash
git show <COMMIT>:.ngit/act/workflows/<WORKFLOW>.yaml
```

Install `ngit` and `git-remote-nostr` inside an ngit-ci or GitHub Actions job
with the step-level composite action, which verifies downloads against a
checksum-pinned manifest:

```yaml
- uses: danconwaydev/setup-ngit@v3
  with:
    version: 3.0.0   # optional exact pin; the default `latest` resolves against the action's manifest, not the network
```

Source:
`nostr://npub15qydau2hjma6ngxkl2cyar74wzyjshvl65za5k5rl69264ar2exs5cyejr/relay.ngit.dev/setup-ngit`
(GitHub mirror `DanConwayDev/setup-ngit`).

## Query a result

```bash
ngit ci status <COMMIT|'#prefix'|nevent> --json                    # first query refreshes relays; no target means HEAD
ngit ci status <target> --json --offline                           # later cache-only reads
ngit ci status <target> --require-ci-trust maintainer-directed --json   # exit non-zero unless green at this floor
```

Query the exact commit that introduced the change. A PR target reports only
its latest revision. Read:

- `ci.state`: pending, running, or concluded. `ci.conclusion` counts only
  once the state is concluded.
- `ci.conclusion`: success, failure, cancellation, or another outcome.
  `command_status: "ok"` means only that the query worked.
- `ci.runs[].workflow` and `ci.runs[].jobs`: which workflow and job passed or
  failed.
- `ci.runs[].integrity`: the commit is present locally and the workflow hash
  matches.
- `coverage` and each run's classification and evidence: how completely and
  why the result is trusted. Partial coverage is not success.

If no run appears, report that no matching Nostr CI event was found. Then
check that the workflow existed at that commit, its trigger matched, a
coordinator serves the repository, and the query refreshed the relays before
concluding that CI did not run.

Trust floors are `maintainer-directed` and `operationally-associated`.
`ngit pr merge --require-ci-trust <LEVEL>` applies the same gate to a merge
when the caller wants one.
