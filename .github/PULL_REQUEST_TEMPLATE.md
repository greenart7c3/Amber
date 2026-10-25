<!--
Thanks for contributing to Amber! Before opening this PR, please skim
CONTRIBUTING.md, especially "Proof of testing" if you are not a regular
contributor to this repo.

Delete any section below that doesn't apply.
-->

## Summary

<!-- 1–3 sentences. What changed and why. Not "what files changed": the diff
already shows that. -->

## Test plan

<!-- Required. What did you actually run, on which device and flavor, and
what happened? "CI is green" is necessary but not sufficient: show the new
path running.

For signing / permission / key changes: say which request paths you exercised
(nostrsigner:// intent, ContentProvider, NIP-46 bunker) and in which states
(locked screen, Tor on/off, cold start) if they matter.
For UI changes: attach screenshots (light + dark) or a short recording, plus
device model and Android version.
For build/packaging changes: paste the ./gradlew command and the tail of the
output.

If you are NOT a regular contributor, this section is required however small
the change is. See CONTRIBUTING.md § Proof of testing. -->

- [ ] `./gradlew ktlintCheck detektAll lint` passes
- [ ] `./gradlew test` passes
- [ ] `./gradlew assembleDebug` builds both flavors
- [ ] Tested by hand on a device or emulator (notes below)
- [ ] Checked the `offline` flavor, or the change can't affect it
- [ ] New or changed strings are translated in every locale

Device / Android version / flavor:

Notes / screenshots:

<!-- paste here -->

## AI assistance

<!-- We accept AI-assisted PRs under the same rules as hand-written ones. -->

- [ ] Drafted with AI assistance, then fully reviewed and tested by hand
- [ ] Written by hand

If "Drafted with AI assistance" is ticked, a human must have reviewed the
whole diff and tested the app by hand on a device before this PR was opened.
See [`CONTRIBUTING-WITH-AI.md`](https://github.com/greenart7c3/Amber/blob/master/CONTRIBUTING-WITH-AI.md).

## License

- [ ] By submitting this PR, I agree to license my contribution under the
      MIT license. Any code I did not write personally carries its original
      license header.
