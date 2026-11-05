package com.greenart7c3.nostrsigner.commonsui

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * [getString] for code that can't suspend and isn't composed (notifications,
 * services, labels built in plain functions). Composables use
 * `stringResource`; coroutines can call [getString] directly. Strings are
 * read from the app's own package and cached, so only the first read of a
 * language waits on I/O.
 */
fun getStringBlocking(resource: StringResource, vararg formatArgs: Any): String = runBlocking {
    if (formatArgs.isEmpty()) getString(resource) else getString(resource, *formatArgs)
}
