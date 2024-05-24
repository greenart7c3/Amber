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
    const intent = `intent:${encryptedText}#Intent;scheme=nostrsigner;S.pubKey=${hex_pub_key};S.compressionType=none;S.returnType=signature;S.type=nip04_decrypt;S.callbackUrl=https://example.com/?event=;end`;

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