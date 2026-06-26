package com.greenart7c3.nostrsigner.signer;

import com.greenart7c3.nostrsigner.signer.CryptoPayload;

// All private-key cryptographic operations run inside the isolated ":signer"
// process. The main process never holds the decrypted key; it forwards the raw
// primitive over this interface, keyed by npub (account identity key) or by a
// connection private key (NIP-46 per-app "bunker" key). Bulk data travels as a
// CryptoPayload (inline or FD-backed) to stay clear of the binder size limit.
//
// Errors are surfaced as ServiceSpecificException with the codes in RemoteSigner.
interface ISignerService {
    // Returns the signed event as JSON; the caller re-parses to the concrete type.
    // tagsJson is the tag array serialized as JSON (AIDL has no String[][]).
    CryptoPayload signEvent(String npub, long createdAt, int kind, String tagsJson, in CryptoPayload content);

    CryptoPayload nip04Encrypt(String npub, in CryptoPayload plainText, String toPublicKey);
    CryptoPayload nip04Decrypt(String npub, in CryptoPayload cipherText, String fromPublicKey);
    CryptoPayload nip44Encrypt(String npub, in CryptoPayload plainText, String toPublicKey);
    CryptoPayload nip44Decrypt(String npub, in CryptoPayload cipherText, String fromPublicKey);

    // Generic decrypt (auto NIP-04/NIP-44 detection).
    CryptoPayload decrypt(String npub, in CryptoPayload cipherText, String fromPublicKey);

    // NIP-44 v3: plaintext/ciphertext are raw bytes.
    CryptoPayload nip44v3Encrypt(String npub, in CryptoPayload plainText, String toPublicKey, int kind, String scope);
    CryptoPayload nip44v3Decrypt(String npub, in CryptoPayload cipherText, String fromPublicKey, int kind, String scope);

    CryptoPayload signPsbt(String npub, in CryptoPayload psbtHex);

    // Returns the decrypted zap request as JSON; throws CODE_NULL_RESULT when the
    // operation yields null (mirrors Account.decryptZapEvent's null contract).
    CryptoPayload decryptZapEvent(String npub, in CryptoPayload eventJson);

    String nip49Encrypt(String npub, String password);

    // Returns the gift-wrapped events to publish, as newline-delimited event JSON.
    CryptoPayload createMessageNIP17(String npub, long createdAt, int kind, String tagsJson, in CryptoPayload content);

    // Sensitive: the plaintext secret is deliberately returned to the main process
    // because the UI must display/copy it (backup & export screens, user-gated).
    String getNsec(String npub);
    String seedWords(String npub);

    // Connection-scoped (NIP-46 localKey) variants — the per-connection bunker key
    // also stays out of the main process.
    CryptoPayload connSignEvent(String connPrivKeyHex, long createdAt, int kind, String tagsJson, in CryptoPayload content);
    CryptoPayload connNip04Encrypt(String connPrivKeyHex, in CryptoPayload plainText, String toPublicKey);
    CryptoPayload connNip44Encrypt(String connPrivKeyHex, in CryptoPayload plainText, String toPublicKey);
    CryptoPayload connDecrypt(String connPrivKeyHex, in CryptoPayload cipherText, String fromPublicKey);

    // Lifecycle: drop a cached in-memory signer (logout / account removal).
    void evict(String npub);
    void evictAll();
}
