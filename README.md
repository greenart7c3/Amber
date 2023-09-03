# Amber: Nostr event signer for Android

Amber is a nostr event signer for Android. It allows users to keep their nsec segregated in a single, dedicated app. The goal of Amber is to have your smartphone act as a NIP-46 signing device without any need for servers or additional hardware. "Private keys should be exposed to as few systems as possible as each system adds to the attack surface," as the rationale of said NIP states. In addition to native apps, Amber aims to support all current nostr web applications without requiring any extensions or web servers.

# Current Features

- [x] Offline
- [ ] Use nip-46 or make an addendum in nip-46
- [ ] Improve the ui (currently its showing a text with the raw json of the event)
- [x] Check if we can use Amber to sign the events of web applications
- [x] Change the sign button to just copy the signature of the event
- [ ] Use a service or another approach to not open the app always to get the signed event on android

# Adding Amber support for your application

* Add a package querie in your AndroidManifest.xml

```xml
<queries>
    <package android:name="com.greenart7c3.nostrsigner"/>
</queries>
```

* Create the Nostr Event

```kotlin
val event = TextNoteEvent(id, pubKey, TimeUtils.now(), tags, message, signature = "")
```

* Convert the event to json

```kotlin
val json = event.toJson()
```

* Create the intent using the **nostrsigner** scheme

```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$json;name=Your Application name"))
```

* Set the package name of the signer app for the intent

```kotlin
intent.`package` = "com.greenart7c3.nostrsigner"
```

* Set the type eg (sign_event, nip04_encrypt, nip04_decrypt, nip44_encrypt, nip44_decrypt, get_public_key)

```kotlin
val signerType = when (type) {
    SignerType.SIGN_EVENT -> "sign_event"
    SignerType.NIP04_ENCRYPT -> "nip04_encrypt"
    SignerType.NIP04_DECRYPT -> "nip04_decrypt"
    SignerType.NIP44_ENCRYPT -> "nip44_encrypt"
    SignerType.NIP44_DECRYPT -> "nip44_decrypt"
    SignerType.GET_PUBLIC_KEY -> "get_public_key"
}
intent.putExtra("type", signerType)
```

* Set the pubkey if using nip04_decrypt

```kotlin
intent.putExtra("pubKey", pubKey)
```

* Start the signer Activity

```kotlin
context.startActivity(intent)
```

# Support for Web Applications

* Create the Nostr Event

* Convert the event to json

```js
const json = JSON.stringify(note)
```

* Create the intent

```js
const intent = `nostrsigner:${json};name=Your Application name`
```

* Send the intent

```js
window.location.href = intent;
```

# Contributing

[Issues](https://github.com/greenart7c3/Amber/issues) and [pull requests](https://github.com/greenart7c3/Amber/pulls) are very welcome.
