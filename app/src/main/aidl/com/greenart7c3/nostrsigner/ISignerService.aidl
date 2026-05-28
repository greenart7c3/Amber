// ISignerService.aidl
package com.greenart7c3.nostrsigner;

/**
 * Bound-service signing interface — a synchronous alternative to the
 * {@code SignerProvider} ContentProvider.
 *
 * Clients bind to {@code com.greenart7c3.nostrsigner.SignerService} (action
 * {@code com.greenart7c3.nostrsigner.SIGNER_SERVICE}) and call these methods
 * directly instead of issuing {@code content://} queries.
 *
 * Every method returns a {@link android.os.Bundle} that mirrors the columns the
 * ContentProvider used to return:
 *  - {@code "rejected"} (boolean) — present and {@code true} when no auto-accept
 *    rule matched, meaning the request must be confirmed by the user through the
 *    normal {@code nostrsigner:} intent flow.
 *  - {@code "signature"}, {@code "event"}, {@code "result"} (String) — the
 *    operation output, populated on success.
 *
 * A {@code null} return signals an error (unknown account, malformed input,
 * missing permission record, etc.), matching the ContentProvider's {@code null}
 * cursor behaviour.
 *
 * The {@code npub} parameter selects the signing account (hex pubkey or npub).
 */
interface ISignerService {
    Bundle signMessage(String message, String npub);

    Bundle signEvent(String eventJson, String npub);

    Bundle nip04Encrypt(String plaintext, String pubKey, String npub);

    Bundle nip04Decrypt(String ciphertext, String pubKey, String npub);

    Bundle nip44Encrypt(String plaintext, String pubKey, String npub);

    Bundle nip44Decrypt(String ciphertext, String pubKey, String npub);

    Bundle decryptZapEvent(String eventJson, String pubKey, String npub);

    Bundle signPsbt(String psbtHex, String npub);

    Bundle ping(String npub);
}
