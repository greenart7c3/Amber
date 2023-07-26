package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Stable

@Stable
data class ImmutableListOfLists<T>(val lists: List<List<T>> = emptyList())

fun List<List<String>>.toImmutableListOfLists(): ImmutableListOfLists<String> {
    return ImmutableListOfLists(this)
}
