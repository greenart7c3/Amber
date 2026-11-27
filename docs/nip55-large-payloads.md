# NIP-55 Large Payloads Extension Draft

Proposed addition to [`nostr-protocol/nips/blob/master/55.md`](https://github.com/nostr-protocol/nips/blob/master/55.md) to support payloads that exceed Android Binder's 1 MB shared transaction buffer and `CursorWindow`'s 2 MB limit (for example large `sign_event` kind 3/30023/30078 events, multi-MB NIP-44 v3 ciphertexts/plaintexts, large PSBTs, and large batch `results`).

---

## Large Payloads

Android Binder transactions share a 1 MB per-process kernel buffer (with UTF-16 string marshaling), and `ContentProvider` `MatrixCursor` windows default to 2 MB. Passing large payloads inline in `Intent` data/extras, `ContentResolver.query()` projections, or `Cursor` columns throws `TransactionTooLargeException` or `SQLiteBlobTooBigException`.

To transfer payloads larger than **128 KB (UTF-8)**, clients and signers use an explicit **signer-hosted streaming authority** backed by `ParcelFileDescriptor` pipes (`openOutputStream` / `openInputStream`), with zero plaintext written to disk and zero client `AndroidManifest.xml` changes.

### Discovery

A signer that supports large-payload streaming exports the `content://<package-name>.PAYLOADS` authority alongside its existing operation authorities. Clients check availability without starting the signer process:

```kotlin
fun supportsPayloadStreams(context: Context, signerPackageName: String): Boolean =
    context.packageManager.resolveContentProvider("$signerPackageName.PAYLOADS", 0) != null
```

Because Android 11+ package visibility applies per package and clients already declare `<queries>` for `nostrsigner:`, no extra `<queries>` entry is needed once the signer package is known.

### Staging a Request Payload (Client -> Signer)

1. The client allocates a single-use upload handle from the signer's `PAYLOADS` authority:
   ```kotlin
   val payloadUri = context.contentResolver.insert(
       Uri.parse("content://$signerPackageName.PAYLOADS"),
       ContentValues(),
   ) ?: error("Signer failed to allocate payload handle")
   ```
   The signer MUST return an opaque URI `content://<package-name>.PAYLOADS/<token>` containing a cryptographically random 128-bit token bound to the caller's UID and `callingPackage`.

2. The client writes the UTF-8 request payload and **MUST close** the output stream before referencing `payloadUri` in any Intent or `query()` call:
   ```kotlin
   context.contentResolver.openOutputStream(payloadUri, "w")!!.use { out ->
       out.write(payload.toByteArray(Charsets.UTF_8))
   }
   ```

3. The client references `payloadUri` explicitly (signers MUST NOT guess whether an ordinary payload string starting with `"content://"` is a handle):
   - **Intents**: set `Intent.data = Uri.parse("nostrsigner:")` (empty payload), `putExtra("payload_uri", payloadUri.toString())`, and `putExtra("accept_result_uri", true)`.
   - **ContentProvider `query()`**: append query parameters `payload_in_uri=1&accept_result_uri=1` to the operation URI (`content://<package-name>.SIGN_EVENT?payload_in_uri=1&accept_result_uri=1`) and pass `payloadUri.toString()` in slot `[0]` in place of the inline payload string.

Clients MAY call `contentResolver.delete(payloadUri, null, null)` to release an unused handle early if they abort before invoking the signer.

### Streaming a Response Payload (Signer -> Client)

When the client indicates support via `accept_result_uri` (`true` on Intents, `accept_result_uri=1` on `query()`) and a response field exceeds the signer's inline threshold (recommended **128 KB UTF-8**), the signer stores that field in a single-use, caller-UID-bound handle and returns the URI in place of the oversized inline value. Small fields (such as a 128-byte signature on `sign_event`) remain inline:

| Path | Inline field | Streamed URI replacement |
| --- | --- | --- |
| Intent single response | `result` / `signature` | `result_uri` |
| Intent single `sign_event` | `event` | `event_uri` |
| Intent batch response | `results` | `results_uri` |
| ContentProvider `Cursor` | `result` / `signature` | `result_uri` column |
| ContentProvider `sign_event` `Cursor` | `event` | `event_uri` column |

Each returned `*_uri` MUST carry its own independent single-use token so clients can read `result_uri` and/or `event_uri` in any order. The client reads the response stream once and closes it:

```kotlin
val responseText = context.contentResolver.openInputStream(Uri.parse(uriString))!!.use { input ->
    input.readBytes().toString(Charsets.UTF_8)
}
```

### `returnType` Optimization for Native `sign_event`

Native clients calling `sign_event` that already hold the unsigned event and only need the signature SHOULD explicitly request signature-only delivery:

- **Intents**: `putExtra("returnType", "signature")`
- **ContentProvider `query()`**: append `return_type=signature` to the operation URI

When `"signature"` is explicitly requested (and the event is NOT an anonymous zap request), the signer SHOULD omit the `event` / `event_uri` output so the response stays ~128 bytes regardless of event size.

### Signer Security & Resource Requirements

- **Caller isolation**: Signers MUST bind every `<token>` to the allocating caller's `Binder.getCallingUid()` and `callingPackage`, and reject `openFile`, `query`, or `delete` from any other UID/package with `SecurityException`.
- **Single-use & wiping**: Each upload and response handle MUST be consumed at most once, evicted after a short TTL (recommended 60 seconds), and wiped from memory upon consumption, cancellation, or expiration.
- **No disk persistence**: Signers SHOULD keep staged payloads in memory using `ParcelFileDescriptor.createReliablePipe()` on background threads (to avoid Linux's 64 KB pipe buffer deadlock) rather than writing unencrypted user data to flash.
- **Quotas**: Signers MUST enforce per-payload (recommended ≥ 16 MB), per-caller, and global memory limits to prevent memory-exhaustion DoS from untrusted apps.
- **Path validation**: Signers MUST strictly validate `<token>` format (e.g. lowercase 32-char hex) and reject path traversal or extra URI segments.

### Web Applications

Web browsers cannot invoke Android `ContentProvider` streams. Web clients MAY continue using `compressionType=gzip` for moderately sized responses, and SHOULD use [NIP-46](https://github.com/nostr-protocol/nips/blob/master/46.md) when payloads exceed browser URL/Intent limits.

### Reference Client Helper (Kotlin)

```kotlin
private const val INLINE_LIMIT_BYTES = 128 * 1024

fun stagePayloadIfLarge(
    resolver: ContentResolver,
    signerPackage: String,
    payload: String,
): Uri? {
    val bytes = payload.toByteArray(Charsets.UTF_8)
    if (bytes.size <= INLINE_LIMIT_BYTES) return null
    val uri = resolver.insert(Uri.parse("content://$signerPackage.PAYLOADS"), ContentValues())
        ?: error("Failed to allocate NIP-55 payload handle")
    try {
        resolver.openOutputStream(uri, "w")!!.use { out ->
            out.write(bytes)
        }
        return uri
    } catch (t: Throwable) {
        resolver.delete(uri, null, null)
        throw t
    }
}

fun readUriOrInline(
    resolver: ContentResolver,
    inlineValue: String?,
    uriValue: String?,
): String? {
    if (!uriValue.isNullOrBlank()) {
        return resolver.openInputStream(Uri.parse(uriValue))!!.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }
    return inlineValue
}
```
