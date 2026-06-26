package com.greenart7c3.nostrsigner.signer;

// Implemented by the Kotlin CryptoPayload class. Carries a bulk byte payload
// either inline (small) or via a file descriptor (SharedMemory / pipe) so that
// large encrypt/decrypt/sign payloads never hit the ~1MB binder transaction buffer.
parcelable CryptoPayload;
