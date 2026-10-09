package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fasterxml.jackson.databind.JsonNode
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContent
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContentType
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.text.DateFormat
import java.util.Date

/** The parts of an event the details dialog shows, read from any event JSON. */
internal data class DetailEvent(
    val kind: Int,
    val pubKey: String,
    val createdAt: Long,
    val content: String,
    val tags: List<List<String>>,
) {
    companion object {
        fun parse(json: String): DetailEvent? {
            val node = json.trimStart().takeIf { it.startsWith("{") }
                ?.let { runCatching { JacksonMapper.mapper.readTree(it) }.getOrNull() }
                ?: return null
            val kind = node.get("kind")?.takeIf { it.canConvertToInt() } ?: return null
            return DetailEvent(
                kind = kind.asInt(),
                pubKey = node.get("pubkey")?.asText().orEmpty(),
                createdAt = node.get("created_at")?.asLong() ?: 0,
                content = node.get("content")?.asText().orEmpty(),
                tags = node.get("tags").toTags(),
            )
        }
    }
}

private fun JsonNode?.toTags(): List<List<String>> = this?.takeIf { it.isArray }?.map { tag -> tag.map { it.asText() } }.orEmpty()

private fun List<List<String>>.format(): String = joinToString(", ") { tag -> tag.joinToString(", ", "[", "]") { "\"$it\"" } }

/**
 * What a request card and its details dialog show, mirroring the Android
 * `BunkerRequestCard`: the event a sign/decrypt is about, a tag list, or text.
 */
internal sealed interface RequestDetails {
    data class OfEvent(val event: DetailEvent) : RequestDetails

    data class OfTags(val tags: List<List<String>>) : RequestDetails

    data class OfText(val text: String) : RequestDetails

    /** The short text under the request's description on the card. */
    val summary: String
        get() = when (this) {
            // An auth event says nothing in its content: show the relay instead.
            is OfEvent -> if (event.kind == 22242) {
                event.tags.firstOrNull { it.size > 1 && it[0] == "relay" }?.get(1) ?: event.content
            } else {
                event.content
            }
            is OfTags -> tags.format()
            is OfText -> text
        }

    companion object {
        /** Null when there is nothing to detail (connect, get_public_key, ping…). */
        fun of(req: PendingBunkerRequest): RequestDetails? = when {
            req.type == SignerType.SIGN_EVENT ->
                (DetailEvent.parse(req.result) ?: DetailEvent.parse(req.preview))?.let(::OfEvent)
            req.type == SignerType.DECRYPT_ZAP_EVENT -> DetailEvent.parse(req.preview)?.let(::OfEvent)
            req.type in contentScopedSignerTypes || req.type in nip44v3SignerTypes -> {
                val content = req.encryptedContent ?: EncryptedContent.classify(req.preview)
                when (content.type) {
                    EncryptedContentType.EVENT -> DetailEvent.parse(req.preview)?.let(::OfEvent)
                    EncryptedContentType.TAG_ARRAY -> runCatching { JacksonMapper.mapper.readTree(req.preview).toTags() }.getOrNull()?.let(::OfTags)
                    EncryptedContentType.CLEAR_TEXT -> null
                } ?: OfText(req.preview)
            }
            else -> null
        }
    }
}

/** Mirrors the Android `EventDetailModal` / `EncryptDecryptDetailModal`. */
@Composable
internal fun RequestDetailsDialog(
    req: PendingBunkerRequest,
    details: RequestDetails,
    onDismiss: () -> Unit,
) {
    val language by Strings.currentLanguage.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(min = 420.dp, max = 720.dp).padding(24.dp),
        title = { Text(req.type.describe(req.kind, req.encryptedContent, language)) },
        text = {
            val scroll = rememberScrollState()
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (details) {
                    is RequestDetails.OfEvent -> {
                        val event = details.event
                        val kindLabel = "${event.kind} - ${SignerDescriptions.signEventDescription(event.kind, language)}"
                        DetailSection(Strings.get("kind", language), kindLabel)
                        if (event.pubKey.isNotBlank()) {
                            DetailSection(Strings.get("pubkey", language), event.pubKey.toShortenHex(), copyValue = event.pubKey)
                        }
                        DetailSection(
                            Strings.get("date", language),
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(event.createdAt * 1000)),
                            copyValue = event.createdAt.toString(),
                        )
                        if (event.content.isNotEmpty()) {
                            DetailSection(contentLabel(language), event.content)
                        }
                        if (event.tags.isNotEmpty()) {
                            TagsSection(Strings.get("tags", language), event.tags)
                        }
                    }
                    is RequestDetails.OfTags -> TagsSection(Strings.get("tags", language), details.tags)
                    is RequestDetails.OfText -> DetailSection(contentLabel(language), details.text)
                }
            }
        },
        confirmButton = {
            AmberTextButton(text = Strings.get("d_close", language), onClick = onDismiss)
        },
    )
}

private fun contentLabel(language: String) = Strings.get("content", language).replaceFirstChar { it.uppercase() }

@Composable
private fun DetailSection(
    label: String,
    value: String,
    copyValue: String = value,
    monospace: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    val language by Strings.currentLanguage.collectAsState()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(copyValue))
                Toaster.toast(Strings.get("d_copied_clipboard", language))
            },
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = Strings.get("copy", language).trim(), modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider()
}

@Composable
private fun TagsSection(label: String, tags: List<List<String>>) {
    DetailSection(
        label = label,
        value = tags.joinToString("\n") { tag -> tag.joinToString(", ", "[", "]") { "\"$it\"" } },
        copyValue = tags.format(),
        monospace = true,
    )
}
