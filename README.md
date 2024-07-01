# Amber: Nostr event signer for Android

Amber is a nostr event signer for Android. It allows users to keep their nsec segregated in a single, dedicated app. The goal of Amber is to have your smartphone act as a NIP-46 signing device without any need for servers or additional hardware. "Private keys should be exposed to as few systems as possible as each system adds to the attack surface," as the rationale of said NIP states. In addition to native apps, Amber aims to support all current nostr web applications without requiring any extensions or web servers.

<div align="center">

[![GitHub downloads](https://img.shields.io/github/downloads/greenart7c3/Amber/total?label=Downloads&labelColor=27303D&color=0D1117&logo=github&logoColor=FFFFFF&style=flat)](https://github.com/greenart7c3/Amber/releases)
[![Last Version](https://img.shields.io/github/release/greenart7c3/Amber.svg?maxAge=3600&label=Stable&labelColor=06599d&color=043b69)](https://github.com/greenart7c3/Amber)
[![CI](https://img.shields.io/github/actions/workflow/status/greenart7c3/Amber/build.yml?labelColor=27303D)](https://github.com/greenart7c3/Amber/actions/workflows/build.yml)
[![License: Apache-2.0](https://img.shields.io/github/license/greenart7c3/Amber?labelColor=27303D&color=0877d2)](/LICENSE)

</div>

# Current Features

- [x] Offline
- [x] Use nip-46 or make an addendum in nip-46
- [x] Improve the ui (currently its showing a text with the raw json of the event)
- [x] Check if we can use Amber to sign the events of web applications
- [x] Change the sign button to just copy the signature of the event
- [x] Use content provider to sign events in background when you checked the remember my choice option on android
- [x] Support for multiple accounts

## Download and Install

[<img src="./docs/obtainium.png"
alt="Get it on Obtaininum"
height="70">](https://github.com/ImranR98/Obtainium)
[<img src="https://github.com/machiav3lli/oandbackupx/raw/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Get it on GitHub"
height="70">](https://github.com/greenart7c3/Amber/releases)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="70">](https://f-droid.org/packages/com.greenart7c3.nostrsigner/)

# Contributing

Issues can be logged on: [https://gitworkshop.dev/repo/Amber](https://gitworkshop.dev/repo/Amber)

[GitHub issues](https://github.com/greenart7c3/Amber/issues) and [pull requests](https://github.com/greenart7c3/Amber/pulls) here are also welcome. Translations can be provided via [Crowdin](https://crowdin.com/project/amber-nostr-signer)

You can also send patches through Nostr using [GitStr](https://github.com/fiatjaf/gitstr) to [this nostr address](https://patch34.pages.dev/naddr1qvzqqqrhnypzqateqake4lc2fn77lflzq30jfpk8uhvtccalc66989er8cdmljceqqz5zmtzv4eqsrpqjs)

By contributing to this repository, you agree to license your work under the MIT license. Any work contributed where you are not the original author must contain its license header with the original author(s) and source.

# Usage for Android applications

Check [Android.md](docs/Android.md)

# Usage for Web Applications

Check [web-apps.md](docs/web-apps.md)

# Contributors

<a align="center" href="https://github.com/greenart7c3/amber/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=greenart7c3/amber" />
</a>
