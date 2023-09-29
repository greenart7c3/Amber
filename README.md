# Amber: Nostr event signer for Android

Amber is a nostr event signer for Android. It allows users to keep their nsec segregated in a single, dedicated app. The goal of Amber is to have your smartphone act as a NIP-46 signing device without any need for servers or additional hardware. "Private keys should be exposed to as few systems as possible as each system adds to the attack surface," as the rationale of said NIP states. In addition to native apps, Amber aims to support all current nostr web applications without requiring any extensions or web servers.

# Current Features

- [x] Offline
- [ ] Use nip-46 or make an addendum in nip-46
- [x] Improve the ui (currently its showing a text with the raw json of the event)
- [x] Check if we can use Amber to sign the events of web applications
- [x] Change the sign button to just copy the signature of the event
- [x] Use content provider to sign events in background when you checked the remember my choice option on android
- [x] Support for multiple accounts

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
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$json"))
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
    SignerType.DECRYPT_ZAP_EVENT -> "decrypt_zap_event"
}
intent.putExtra("type", signerType)
```

* Set the pubkey used for encryption/decryption

```kotlin
intent.putExtra("pubKey", pubKey)
```

* Create an id or set the id as the id of the event so you can control in your application what response you are receiving

```kotlin
intent.putExtra("id", event.id)
```

* Set the current npub of the logged in user, so amber knows what user it has to sign the transaction (not yet implemented)

```kotlin
intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
```

* Start the signer Activity

```kotlin
context.startActivity(intent)
```

## Sign transactions in background

* Create the Nostr Event

```kotlin
val event = TextNoteEvent(id, pubKey, TimeUtils.now(), tags, message, signature = "")
```

* Create the content resolver using the following scheme

```kotlin
"content://com.greenart7c3.nostrsigner.$signerType"
```

* The signer types are

```kotlin
enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT
}
```

* In the projection parameter of the contentResolver.query you must send a list with the following data
    * The event json
    * The hex pub key for encryption/decryption
    * The current logged in user npub

* Example

```kotlin
context.contentResolver.query(
    Uri.parse("content://com.greenart7c3.nostrsigner.$signerType"),
    listOf(event.toJson(), "hex pubKey for encryption/decryption", "logged in user npub"),
    null,
    null,
    null
).use {
    if (it !== null) {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            if (index < 0) {
                Log.d("getDataFromResolver", "column '$columnName' not found")
                return null
            }
            return it.getString(index)
        }
    }
}
```

* If the user did not check the remember my choice option, the npub is not in amber or the signer type is not recognized the contentResolver will return null

* For the SIGN_EVENT type amber returns two columns "signature" and "event". The column event is the signed event json

* For the other types amber returns the column "signature"

# Support for Web Applications

* Here's an index.html with an example of how to open amber to sign an event

* The S.type= can be sign_event, nip04_encrypt, nip44_encrypt, nip04_decrypt, nip44_decrypt, get_public_key or decrypt_zap_event

* You can send a callbackUrl so your user doesn't need to copy the event manually to your application

* For Encryption/Decryption you should also send the hex pubkey that will be used to encrypt/decrypt the data eg (S.pubKey=cb8b8d378690f9a4a4f412c4e295051c4b76c3db24dc3941860fd3980f07d21d)

```js
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Document</title>
</head>
<body>
    <h1>Test</h1>
       
    <script>
        window.onload = function() {
            var url = new URL(window.location.href);
            var params = url.searchParams;
            if (params) {
                var param1 = params.get("event");
                if (param1) alert(param1)
            }
            let json = {
                kind: 1,
                content: "test"
            }
            let encodedJson = encodeURIComponent(JSON.stringify(json))
            var newAnchor = document.createElement("a");
            newAnchor.href = `intent:${encodedJson}#Intent;scheme=nostrsigner;S.type=sign_event;S.callbackUrl=https://example.com/?event=;end`;
            newAnchor.textContent = "Open amber";
            document.body.appendChild(newAnchor)
        }
    </script>
</body>
</html>
```

# Contributing

[Issues](https://github.com/greenart7c3/Amber/issues) and [pull requests](https://github.com/greenart7c3/Amber/pulls) are very welcome.
