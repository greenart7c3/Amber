package com.greenart7c3.nostrsigner.models

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class BunkerResponse(
    val id: String,
    val result: String,
    val error: String?,
)
