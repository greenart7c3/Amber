package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.service.model.AmberEvent

interface EncryptedDataKind {
    val result: String
}

class ClearTextEncryptedDataKind(val text: String, override val result: String) : EncryptedDataKind

class TagArrayEncryptedDataKind(val tagArray: Array<Array<String>>, override val result: String) : EncryptedDataKind

class EventEncryptedDataKind(val event: AmberEvent, val sealEncryptedDataKind: EncryptedDataKind?, override val result: String) : EncryptedDataKind

class PrivateZapEncryptedDataKind(override val result: String) : EncryptedDataKind
