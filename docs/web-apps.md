# Usage for Web Applications

Since web applications can't receive a result from the intent, you should add a modal to paste the signature or the event json or create a callback url.

If you send the callback url parameter, Signer Application will send the result to the url.

If you don't send a callback url, Signer Application will copy the result to the clipboard.

You can configure the `returnType` to be **signature** or **event**.

Android intents and browser urls have limitations, so if you are using the `returnType` of **event** consider using the parameter **compressionType=gzip** that will return "Signer1" + Base64 gzip encoded event json

## Methods

- **get_public_key**
  - params:

    ```js
    window.href = `nostrsigner:?compressionType=none&returnType=signature&type=get_public_key&callbackUrl=https://example.com/?event=`;
    ```

- **sign_event**
  - params:

    ```js
    window.href = `nostrsigner:${eventJson}?compressionType=none&returnType=signature&type=sign_event&callbackUrl=https://example.com/?event=`;
    ```

- **nip04_encrypt**
  - params:

    ```js
    window.href = `nostrsigner:${plainText}?pubKey=${hex_pub_key}&compressionType=none&returnType=signature&type=nip04_encrypt&callbackUrl=https://example.com/?event=`;
    ```

- **nip44_encrypt**
  - params:

    ```js
    window.href = `nostrsigner:${plainText}?pubKey=${hex_pub_key}&compressionType=none&returnType=signature&type=nip44_encrypt&callbackUrl=https://example.com/?event=`;
    ```

- **nip04_decrypt**
  - params:

    ```js
    window.href = `nostrsigner:${encryptedText}?pubKey=${hex_pub_key}&compressionType=none&returnType=signature&type=nip04_decrypt&callbackUrl=https://example.com/?event=`;
    ```

- **nip44_decrypt**
  - params:

    ```js
    window.href = `nostrsigner:${encryptedText}?pubKey=${hex_pub_key}&compressionType=none&returnType=signature&type=nip44_decrypt&callbackUrl=https://example.com/?event=`;
    ```

- **decrypt_zap_event**
  - params:

    ```js
    window.href = `nostrsigner:${eventJson}?compressionType=none&returnType=signature&type=decrypt_zap_event&callbackUrl=https://example.com/?event=`;
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
            newAnchor.href = `nostrsigner:${encodedJson}?compressionType=none&returnType=signature&type=sign_event&callbackUrl=https://example.com/?event=`;
            newAnchor.textContent = "Open External Signer";
            document.body.appendChild(newAnchor)
        }
    </script>
</body>
</html>
```
