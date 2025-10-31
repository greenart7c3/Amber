Amber Signer Patch – Preserve Bunker Encryption Scheme
======================================================

1. Problem Statement  
   Persisted bunker requests dropped their original encryption scheme (`encryptionType`). Whenever a pending request made a round trip through intents or storage and came back, the signer assumed NIP-44 for the response—even if the request had arrived encrypted with NIP-04. That broke compatibility with clients expecting NIP-04 and risked leaking plaintext.

2. Proposed Solution  
   Extend the custom serializer for `AmberBunkerRequest` to write out the `encryptionType` field, and add a focused unit test to guarantee that a serialized/deserialized request keeps its original scheme (e.g., NIP-04 stays NIP-04).

3. Benefits  
   - Restores protocol compliance and interoperability with NIP-46 clients that still rely on NIP-04.  
   - Prevents accidental downgrade of encryption, preserving end-to-end confidentiality.  
   - Introduces a regression test that protects the behaviour against future changes.

4. Trade-offs  
   - Slightly larger JSON payloads for persisted requests (one extra field).  
   - Requires maintaining the new unit test alongside future refactors.

5. Performance Implications  
   Negligible: the change adds a single string field to serialized JSON and a tiny unit test; runtime cost is effectively zero.

6. If Not Implemented  
   - Responses to NIP-04 sessions would remain mis-encrypted, leading to failed signing flows and possible plaintext exposure.  
   - Remote clients would see Amber as non-compliant with NIP-46, eroding trust and blocking adoption.

7. Diff Summary  
   - Updated `app/src/main/java/com/greenart7c3/nostrsigner/models/AmberBunkerRequest.kt` to serialize `encryptionType`.  
   - Added `app/src/test/java/com/greenart7c3/nostrsigner/models/AmberBunkerRequestTest.kt` to assert round-trip integrity.  
   - Noted the fix in `AMBER-review.MD`.

8. Tests  
   - `GRADLE_USER_HOME=$PWD/.gradle ./gradlew testFreeDebugUnitTest --tests "com.greenart7c3.nostrsigner.models.AmberBunkerRequestTest"` (passes).  
   - Added unit test for serialization round-trip.

```kotlin
// app/src/main/java/com/greenart7c3/nostrsigner/models/AmberBunkerRequest.kt
gen.writeStringField("encryptDecryptResponse", value.encryptDecryptResponse)
gen.writeStringField("encryptionType", value.encryptionType.name)

// app/src/test/java/com/greenart7c3/nostrsigner/models/AmberBunkerRequestTest.kt
@Test
fun `encryption type survives serialization`() {
    val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")
    requireNotNull(relay)
    val request = AmberBunkerRequest(
        request = BunkerRequestConnect(remoteKey = HEX_KEY, secret = "secret", permissions = null),
        localKey = HEX_KEY,
        relays = listOf(relay),
        currentAccount = "npub1test",
        nostrConnectSecret = "secret",
        closeApplication = true,
        name = "Test App",
        signedEvent = null,
        encryptDecryptResponse = null,
        encryptionType = EncryptionType.NIP04,
    )
    val json = request.toJson()
    val roundTrip = AmberBunkerRequest.mapper.readValue(json, AmberBunkerRequest::class.java)
    assertEquals(EncryptionType.NIP04, roundTrip.encryptionType)
}
```
