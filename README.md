# Amber: Nostr event signer for Android

Designed to have your keys on only one application

# Adding Amber support for your application

* Add a package querie in your AndroidManifest.xml

```xml
<queries>
    <package android:name="com.greenart7c3.nostrsigner"/>
</queries>
```

* Create the Nostr Event

```kotlin
val event = TextNoteEvent(noteId = "", pubKey, TimeUtils.now(), tags, message, signature = "")
```

* Convert the event to json

```kotlin
val json = event.toJson()
```

* Create the intent using the **nostrsigner** scheme

```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$json"))
```

* Add an extra information with the write relays separated by commas of the current user

```kotlin
intent.putExtra("relays", "wss://relay.damus.io,wss://nostr.wine")
```

* Set the package name of the signer app for the intent

```kotlin
intent.`package` = "com.greenart7c3.nostrsigner"
```

* Start the signer Activity

```kotlin
context.startActivity(intent)
```

* In Amber you will see 3 buttons
  
  - The left one you can select the relays to post the Event
  - The button in the middle is to sign the event and copy the raw event json to the clipboard
  - The button on the right is to sign the event and send to the choosen relays

# Contributing

[Issues](https://github.com/greenart7c3/Amber/issues) and [pull requests](https://github.com/greenart7c3/Amber/pulls) are very welcome.
