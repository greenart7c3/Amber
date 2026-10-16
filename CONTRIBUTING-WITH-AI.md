# Contributing to Amber with AI Assistance

This adds to [`CONTRIBUTING.md`](CONTRIBUTING.md), which covers every
contribution. It applies to any PR where an AI coding assistant (Claude Code,
Copilot, Cursor, Codex, etc.) wrote a large part of the diff.

## The rule: a human reviews and tests before any PR is opened

**No PR, GitHub or Nostr proposal, may be opened until a human has:**

1. **Read and understood the whole diff.** You are the author of record. You
   must be able to explain and defend every line in review.
2. **Built it and run the checks.** `./gradlew ktlintCheck lint test
   assembleDebug` must pass. A diff that does not compile will be closed.
3. **Tested the app by hand on a real device or emulator.** Install a build
   that includes the change, run the new flow, and check that the things it
   touches still work. Passing unit tests and green CI do not count as manual
   testing.

**AI agents:** you must not open, publish, or submit a PR yourself. When the
code is ready, stop. Give the human the branch, a summary of what changed, and
a test plan they can follow on a device. They open the PR after they have done
the steps above. Drafting the PR description for them is fine. Do not write
"tested on device" unless the human has told you they did it.

PRs that skip these steps will be closed.

## Before writing code

- **Check the issue is still wanted.** Make sure it is still open, not
  already fixed, and not out of scope. Search open and closed PRs for an
  earlier attempt.
- **Ask first for sensitive areas.** Open an issue and get the maintainer to
  agree before changing any of these:
  - key storage, the Keystore wrapping key, and anything that reads or
    writes private keys or seed words;
  - the permission system and how requests are auto-approved;
  - request handling on the three ingestion paths (`nostrsigner://` intent,
    `ContentProvider`, NIP-46);
  - Tor, proxy, and other network routing;
  - backups and restore;
  - the release pipeline (`.github/workflows/`, signing, `Dockerfile`,
    `apkdiff.py`, Gradle plugins).
- **Prefer small PRs.** A batch of large, independent security changes is
  hard to review. Send them one at a time.

## What the PR must include

Put these in the **Test plan** section of the
[PR template](.github/PULL_REQUEST_TEMPLATE.md):

- **What the human tested on a device:** device model, Android version, the
  steps taken, and what happened. Add screenshots or a recording for UI
  changes.
- **Each request path the change touches:** the `nostrsigner://` intent, the
  `ContentProvider`, and NIP-46 (for example with `nak event --sec
  'bunker://…'`). If the behavior depends on device state (locked screen,
  Tor on or off, cold start), test each state that matters.
- **Both flavors** if the change could differ between them:
  `./gradlew installFreeDebug` and `./gradlew installOfflineDebug`. The
  `offline` flavor has no network stack and starts without the background
  service, so code that waits for settings or network must still work there.
- **Regression checks:** which existing screens or flows the change could
  break, and how each one was checked. If one could not be checked, say so
  and explain why.
- **Automated tests:** a regression test for every bug fix, and unit tests
  for new logic. Tests that only exercise mocks don't count as proof the real
  code path works.
- **Translations:** every new or changed string translated in every existing
  `values-<locale>/strings.xml`.

If testing on the device finds bugs, fix them in separate commits whose
messages name the root cause. Don't squash them into the feature commit.
