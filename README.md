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

# Usage for Android applications

Amber uses Intents and Content Resolvers to communicate between applications.

To be able to use Amber in your application you should add the following package visibility needs:

```xml
<queries>
    <package android:name="com.greenart7c3.nostrsigner"/>
</queries>
```

## Using Intents

To get the result back from Amber you should use registerForActivityResult or rememberLauncherForActivityResult in Kotlin. If you are using another framework check the documentation of your framework or a third party library to get the result.

Create the Intent using the **nostrsigner** scheme:

```kotlin
val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$content"))
```

* Set the Amber package name

```kotlin
intent.`package` = "com.greenart7c3.nostrsigner"
```

### Methods

- **get_public_key**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "get_public_key")
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **npub** in the signature field

      ```kotlin
      val npub = intent.data?.getStringExtra("signature")
      ```

- **sign_event**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$eventJson"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "sign_event")
    // to control the result in your application in case you are not waiting the result before sending another intent
    intent.putExtra("id", event.id)
    // Send the current logged in user npub
    intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
    
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **signature**, **id** and **event** fields

      ```kotlin
      val signature = intent.data?.getStringExtra("signature")
      // the id you sent
      val id = intent.data?.getStringExtra("id")
      val signedEventJson = intent.data?.getStringExtra("event")
      ```

- **nip04_encrypt**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "nip04_encrypt")
    // to control the result in your application in case you are not waiting the result before sending another intent
    intent.putExtra("id", "some_id")
    // Send the current logged in user npub
    intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
    // Send the hex pubKey that will be used for encrypting the data
    intent.putExtra("pubKey", pubKey)
    
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **signature** and **id** fields

      ```kotlin
      val encryptedText = intent.data?.getStringExtra("signature")
      // the id you sent
      val id = intent.data?.getStringExtra("id")
      ```

- **nip44_encrypt**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "nip44_encrypt")
    // to control the result in your application in case you are not waiting the result before sending another intent
    intent.putExtra("id", "some_id")
    // Send the current logged in user npub
    intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
    // Send the hex pubKey that will be used for encrypting the data
    intent.putExtra("pubKey", pubKey)
    
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **signature** and **id** fields

      ```kotlin
      val encryptedText = intent.data?.getStringExtra("signature")
      // the id you sent
      val id = intent.data?.getStringExtra("id")
      ```      

- **nip04_decrypt**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$encryptedText"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "nip04_decrypt")
    // to control the result in your application in case you are not waiting the result before sending another intent
    intent.putExtra("id", "some_id")
    // Send the current logged in user npub
    intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
    // Send the hex pubKey that will be used for decrypting the data
    intent.putExtra("pubKey", pubKey)
    
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **signature** and **id** fields

      ```kotlin
      val plainText = intent.data?.getStringExtra("signature")
      // the id you sent
      val id = intent.data?.getStringExtra("id")
      ```      

- **nip44_decrypt**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$encryptedText"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "nip04_decrypt")
    // to control the result in your application in case you are not waiting the result before sending another intent
    intent.putExtra("id", "some_id")
    // Send the current logged in user npub
    intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
    // Send the hex pubKey that will be used for decrypting the data
    intent.putExtra("pubKey", pubKey)
    
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **signature** and **id** fields

      ```kotlin
      val plainText = intent.data?.getStringExtra("signature")
      // the id you sent
      val id = intent.data?.getStringExtra("id")
      ```        

- **decrypt_zap_event**
  - params:

    ```kotlin
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$eventJson"))
    intent.`package` = "com.greenart7c3.nostrsigner"
    intent.putExtra("type", "decrypt_zap_event")
    // to control the result in your application in case you are not waiting the result before sending another intent
    intent.putExtra("id", "some_id")
    // Send the current logged in user npub
    intent.putExtra("current_user", account.keyPair.pubKey.toNpub())
    context.startActivity(intent)
    ```
  - result:
    - If the user approved intent it will return the **signature** and **id** fields

      ```kotlin
      val eventJson = intent.data?.getStringExtra("signature")
      // the id you sent
      val id = intent.data?.getStringExtra("id")
      ```         

## Using Content Resolver

To get the result back from Amber you should use contentResolver.query in Kotlin. If you are using another framework check the documentation of your framework or a third party library to get the result.

If the user did not check the remember my choice option, the npub is not in amber or the signer type is not recognized the contentResolver will return null

For the SIGN_EVENT type amber returns two columns "signature" and "event". The column event is the signed event json

For the other types amber returns the column "signature"

### Methods

- **get_public_key**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.GET_PUBLIC_KEY"),
        listOf("login"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **npub** in the signature column

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            if (index < 0) return
            val npub = it.getString(index)
        }
      ```

- **sign_event**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.SIGN_EVENT"),
        listOf("$eventJson", "", "${logged_in_user_npub}"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **signature** and the **event** columns

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            val indexJson = it.getColumnIndex("event")
            val signature = it.getString(index)
            val eventJson = it.getString(indexJson)
        }
      ```

- **nip04_encrypt**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.NIP04_ENCRYPT"),
        listOf("$plainText", "${hex_pub_key}", "${logged_in_user_npub}"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **signature** column

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            val encryptedText = it.getString(index)
        }
      ```

- **nip44_encrypt**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.NIP44_ENCRYPT"),
        listOf("$plainText", "${hex_pub_key}", "${logged_in_user_npub}"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **signature** column

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            val encryptedText = it.getString(index)
        }
      ```    

- **nip04_decrypt**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.NIP04_DECRYPT"),
        listOf("$encryptedText", "${hex_pub_key}", "${logged_in_user_npub}"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **signature** column

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            val encryptedText = it.getString(index)
        }
      ```    

- **nip44_decrypt**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.NIP44_DECRYPT"),
        listOf("$encryptedText", "${hex_pub_key}", "${logged_in_user_npub}"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **signature** column

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            val encryptedText = it.getString(index)
        }
      ```      

- **decrypt_zap_event**
  - params:

    ```kotlin
    val result = context.contentResolver.query(
        Uri.parse("content://com.greenart7c3.nostrsigner.DECRYPT_ZAP_EVENT"),
        listOf("$eventJson", "", "${logged_in_user_npub}"),
        null,
        null,
        null
    )
    ```
  - result:
    - Will return the **signature** column

      ```kotlin
        if (result == null) return

        if (result.moveToFirst()) {
            val index = it.getColumnIndex("signature")
            val eventJson = it.getString(index)
        }
      ```    

# Usage for Web Applications

Since web applications can't receive a result from the intent you should add a modal to paste the signature or the event json or create a callback url.

If you send the callback url parameter Amber will send the result to the url.

If you don't send a callback url Amber will copy the result to the clipboard.

You can configure the returnType to be **signature** or **event**.

Android intents and browsers url has limitations, so if you are using the returnType of **event** consider using the parameter **compressionType=gzip** that will return "Signer1" + Base 64 gzip encoded event json

## Methods

- **get_public_key**
  - params:

    ```js
    const intent = `intent:#Intent;scheme=nostrsigner;S.compressionType=none;S.returnType=signature;S.type=get_public_key;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ```

- **sign_event**
  - params:

    ```js
    const intent = `intent:${eventJson}#Intent;scheme=nostrsigner;S.compressionType=none;S.returnType=signature;S.type=sign_event;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ``` 

- **nip04_encrypt**
  - params:

    ```js
    const intent = `intent:${plainText}#Intent;scheme=nostrsigner;S.pubKey=${hex_pub_key};S.compressionType=none;S.returnType=signature;S.type=nip04_encrypt;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ``` 

- **nip44_encrypt**
  - params:

    ```js
    const intent = `intent:${plainText}#Intent;scheme=nostrsigner;S.pubKey=${hex_pub_key};S.compressionType=none;S.returnType=signature;S.type=nip44_encrypt;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ```   

- **nip04_decrypt**
  - params:

    ```js
    const intent = `intent:${encryptedText}#Intent;scheme=nostrsigner;S.pubKey=${hex_pub_key};S.compressionType=none;S.returnType=signature;S.type=nip44_encrypt;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ```     

- **nip44_decrypt**
  - params:

    ```js
    const intent = `intent:${encryptedText}#Intent;scheme=nostrsigner;S.pubKey=${hex_pub_key};S.compressionType=none;S.returnType=signature;S.type=nip44_decrypt;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ```     

- **decrypt_zap_event**
  - params:

    ```js
    const intent = `intent:${eventJson}#Intent;scheme=nostrsigner;S.compressionType=none;S.returnType=signature;S.type=decrypt_zap_event;S.callbackUrl=https://example.com/?event=;end`;

    window.href = intent;
    ```          

## Example

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
            newAnchor.href = `intent:${encodedJson}#Intent;scheme=nostrsigner;S.compressionType=none;S.returnType=signature;S.type=sign_event;S.callbackUrl=https://example.com/?event=;end`;
            newAnchor.textContent = "Open amber";
            document.body.appendChild(newAnchor)
        }
    </script>
</body>
</html>
```

# Contributing

[Issues](https://github.com/greenart7c3/Amber/issues) and [pull requests](https://github.com/greenart7c3/Amber/pulls) are very welcome.

# Contributors

<a align="center" href="https://github.com/vitorpamplona/amethyst/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=vitorpamplona/amethyst" />
</a>
