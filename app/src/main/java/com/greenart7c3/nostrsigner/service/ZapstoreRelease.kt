package com.greenart7c3.nostrsigner.service

data class ZapstoreRelease(
    val version: String,
    val url: String,
    val hash: String?,
    val createdAt: Long,
)
