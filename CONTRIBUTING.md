# Contributing to Amber

Thanks for your interest in improving Amber. This document covers the
expectations, conventions, and review rules for code, documentation, and
translation contributions.

Amber holds users' private keys and signs on behalf of other apps, so changes
are reviewed with that in mind: a bug here can leak a key or sign something the
user never approved.

By contributing, you agree to license your work under the MIT license. Any
work contributed where you are not the original author must contain its
license header with the original author(s) and source.

- [Ways to contribute](#ways-to-contribute)
- [Proof of testing](#proof-of-testing)
- [Reporting bugs and requesting features](#reporting-bugs-and-requesting-features)
- [Security issues](#security-issues)
- [Development setup](#development-setup)
- [Where code belongs](#where-code-belongs)
- [Workflow](#workflow)
- [Coding standards](#coding-standards)
- [Tests](#tests)
- [Commits](#commits)
- [Pull requests](#pull-requests)
- [Translations](#translations)
- [Releases](#releases)

---

## Ways to contribute

- Fix bugs or implement features.
- Improve documentation in `README.md`, `SECURITY.md`, `VERIFY_RELEASES.md`,
  and `docs/`.
- Add or improve translations (see [Translations](#translations)).
- Report bugs, suggest features, or open issues on
  [gitworkshop.dev](https://gitworkshop.dev/greenart7c3@greenart7c3.com/Amber)
  or [GitHub issues](https://github.com/greenart7c3/Amber/issues).
- Send patches over Nostr with [ngit](https://ngit.dev) or
  [GitStr](https://github.com/fiatjaf/gitstr) (the address is in the README),
  or open a [GitHub pull request](https://github.com/greenart7c3/Amber/pulls).

## Proof of testing

We accept pull requests written by hand and pull requests written with help
from AI coding assistants (Claude Code, Copilot, Cursor, Codex, etc.) under the
same rules:

- **You are the author of record.** You are responsible for understanding,
  testing, and defending every line, including code an assistant wrote for
  you. "The AI did it" is not a valid response to review feedback.
- **It must build.** Don't submit code that calls functions, classes, or
  imports that don't exist in this repo or its dependencies. Run the checks in
  [Tests](#tests) before you push.
- **Disclose substantial AI involvement** with a one-line note in the PR
  description.

If you are **not a regular contributor** to this repository (first PR, or
sporadic enough that the maintainer wouldn't recognize your handle), the PR
description must include proof that you actually ran the change:

- **Code/logic changes:** paste the test output, command, or log lines that
  show the new path running. CI passing is necessary but not sufficient.
- **Signing, permission, or key-storage changes:** say which request path you
  exercised (`nostrsigner://` intent, `ContentProvider`, or NIP-46 bunker), on
  which flavor, and what happened. A NIP-46 request can be sent from a desktop
  with `nak event --sec 'bunker://…'`.
- **UI changes:** attach screenshots or a short recording from a real device
  or emulator (light and dark theme, device and Android version).
- **Build / Gradle / packaging changes:** paste the `./gradlew` command and
  the tail of its output.
- **Translation-only PRs:** say which locale and which strings you touched;
  screenshots are not required.

Once you have a track record, a short test plan is enough. The maintainer may
still ask for screenshots or a device test on sensitive changes.

If you couldn't test something (for example the `offline` flavor, or a locked
device), say so explicitly. An honest "I couldn't test this" beats a silent
guess.

If an AI coding assistant wrote a substantial part of the diff, also read
[`CONTRIBUTING-WITH-AI.md`](CONTRIBUTING-WITH-AI.md). Its main rule: no PR may
be opened until a human has reviewed the whole diff and tested the app by hand
on a device. An agent must never open the PR itself.

## Reporting bugs and requesting features

**Bug reports** should include:

- what happened and what you expected;
- numbered steps to reproduce, starting from a fresh app launch;
- the client app involved (name, version) and how it talks to Amber
  (`nostrsigner://` intent, `ContentProvider`, or NIP-46 bunker);
- device model, Android version, Amber version, and flavor (`free` or
  `offline`);
- screenshots or a recording for anything visual or timing-sensitive, and
  logs if you have them.

**Feature requests** should describe the user-facing outcome. Skip
implementation suggestions unless they matter to the request.

## Security issues

**Do not file security vulnerabilities as public issues.** Use
[GitHub private vulnerability reporting](https://github.com/greenart7c3/Amber/security/advisories/new).
See [SECURITY.md](SECURITY.md) for scope, response times, and the disclosure
policy.

## Development setup

Prerequisites:

1. **JDK 21** (Temurin recommended; CI uses Temurin 21)
2. **Android Studio** or the Android SDK command-line tools (`compileSdk 37`)
3. **An Android 8.0+ (API 26) phone or emulator**

Clone and build:

```bash
git clone https://github.com/greenart7c3/Amber.git
cd Amber
./gradlew installFreeDebug        # install the online debug build
./gradlew installOfflineDebug     # install the offline debug build
```

Debug builds install as `com.greenart7c3.nostrsigner.debug`, next to a release
install, so you can test without touching your real keys. Both debug flavors
share that ID, so installing one replaces the other.

The git hooks in `git-hooks/` (pre-commit: `ktlintCheck lint`; pre-push:
`test lint`) are installed automatically by the first Gradle build.

## Where code belongs

Amber is a single Gradle module, `:app` (package
`com.greenart7c3.nostrsigner`). Before writing a new class or composable,
check whether it already exists; duplicating existing logic is the most
common cause of review churn.

Things to know before touching these areas:

- **Request handling.** External apps reach Amber through three paths: the
  `nostrsigner://` intent (`SignerActivity` → `IntentUtils`), the
  `ContentProvider` (`SignerProvider`), and NIP-46 relay events
  (`EventNotificationConsumer` → `BunkerRequestUtils`). A behavior change to
  one path usually needs the same change in the others, or a reason why not.
- **Authorization.** Automatic signing is governed by the permission database
  (`ApplicationEntity` / `ApplicationPermissionsEntity`). The biometric/PIN
  lock is a UI-only app-launch gate; don't route signing through it.
- **Flavors.** `free` has the network stack; `offline` has none. Guard
  network-only code with `BuildFlavorChecker.isOfflineFlavor()`. CI fails if a
  new dependency adds network permissions to the `offline` manifest.
- **Per-account data.** Each `npub` has its own preferences and databases.
  Don't share state across accounts.

`AGENTS.md` describes the architecture in more detail.

## Workflow

1. **Survey first.** Search the codebase for an existing implementation
   before writing new code.
2. **Keep PRs small.** One feature or one fix per PR. Refactors that support
   the change are fine; unrelated refactors are not.
3. **Branch off `master`.**
4. **Run the checks locally** (see [Tests](#tests)) before pushing.
5. **Open the PR** with a description that explains *why*, not just *what*.

## Coding standards

- Kotlin, formatted with ktlint (`android_studio` style, see
  `.editorconfig`). Run `./gradlew ktlintFormat` instead of formatting by
  hand.
- Android Lint runs with warnings as errors: any new warning fails the build.
- Never use `--no-verify` to skip the git hooks. If a hook fails, fix the
  cause.
- Add a comment only when the *why* is non-obvious (a workaround, a subtle
  invariant, a security reason). Don't restate what the code does.

## Tests

Run these in order before pushing; CI runs the same checks:

```bash
./gradlew ktlintCheck            # style
./gradlew lint                   # Android Lint, warnings fail the build
./gradlew test --no-daemon       # JVM unit tests, all variants
./gradlew assembleDebug          # both flavors compile
```

Run a single test with
`./gradlew :app:testFreeDebugUnitTest --tests "*SomeTest"`.

Add tests when:

- you fix a bug: add a regression test that fails before your fix and passes
  after;
- you change signing, encryption, permission matching, or request parsing;
- you add logic with non-trivial state transitions.

Unit tests with mocks don't prove the app works. For changes on a request path,
also exercise the real path on a device (see [Proof of testing](#proof-of-testing)).

## Commits

- Write the subject as a short imperative sentence describing the change
  (for example `Keep revoked permissions revoked`). Keep it under about 72
  characters.
- Use the body to explain *why* the change was made when it isn't obvious.
- One logical change per commit. Squashing during review is fine.

## Pull requests

A good PR description has:

1. **Summary**: 1–3 sentences on what changed and why.
2. **Test plan**: exactly what you ran, on which device and flavor, and what
   happened. Include screenshots or a recording for UI changes.

GitHub PRs get a [template](.github/PULL_REQUEST_TEMPLATE.md) with these
sections. For ngit/Nostr patches, put the same information in the proposal
description.

## Translations

- Strings live in `app/src/main/res/values/strings.xml` (English) and
  `app/src/main/res/values-<locale>/strings.xml`.
- To improve a translation, edit the locale's `strings.xml` and open a PR.
- When you add or change an English string, add the translated entry to every
  existing locale file in the same change. Escape apostrophes as `\'` where
  the neighboring entries do.

## Releases

Releases are cut by the maintainer. If your change needs to be in a particular
release, say so in the PR description.

To verify a published release, see [VERIFY_RELEASES.md](VERIFY_RELEASES.md).

---

Thanks again for contributing.
