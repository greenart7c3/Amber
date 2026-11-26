package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import kotlinx.coroutines.CancellationException

internal suspend fun openSealForPreview(plaintext: String, acc: DesktopAccount): String {
    val node = plaintext.trimStart().takeIf { it.startsWith("{") }
        ?.let { runCatching { JacksonMapper.mapper.readTree(it) }.getOrNull() }
        ?: return ""
    if (node.get("kind")?.takeIf { it.canConvertToInt() }?.asInt() != SealEvent.KIND) return ""
    val content = node.get("content")?.asText().orEmpty()
    return try {
        acc.decrypt(content, acc.hexKey)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        content
    }
}
