# Amber: Nostr event signer for Android

Designed to have your keys on only one application

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
