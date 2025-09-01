// INostrSigner.aidl
package com.greenart7c3.nostrsigner;

// Declare any non-default types here with import statements

interface INostrSigner {
    String getPublicKey();
    String signEvent(String unsigned_event);
    String nip44Encrypt(String current_user_public_key, String public_key, String plaintext);
    String nip44Decrypt(String current_user_public_key, String public_key, String ciphertext);
}
