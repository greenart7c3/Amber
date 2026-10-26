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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fasterxml.jackson.databind.JsonNode
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.psbt.DecodedPsbt
import com.greenart7c3.nostrsigner.common.psbt.DecodedPsbtOutput
import com.greenart7c3.nostrsigner.common.psbt.PsbtDecoder
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContent
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContentType
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.SignerDescriptions
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.utils.Hex
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

    /** A sign_psbt request: what the transaction pays, mirroring Android's `SignPsbt`. */
    data class OfPsbt(val decoded: DecodedPsbt, val psbt: String) : RequestDetails

    /** The short text under the request's description on the card. */
    fun summary(language: String): String = when (this) {
        // An auth event says nothing in its content: show the relay instead.
        is OfEvent -> if (event.kind == 22242) {
            event.tags.firstOrNull { it.size > 1 && it[0] == "relay" }?.get(1) ?: event.content
        } else {
            event.content
        }
        is OfTags -> tags.format()
        is OfText -> text
        is OfPsbt -> decoded.parseError?.let { Strings.format("psbt_decode_error", it, language = language) }
            ?: listOfNotNull(
                "${Strings.get("psbt_sending", language)}: ${sats(decoded.totalSent, language)}",
                "${Strings.get("psbt_change", language)}: ${sats(decoded.totalChange, language)}",
                decoded.feeSats?.let { "${Strings.get("psbt_fee", language)}: ${sats(it, language)}" },
            ).joinToString(" · ")
    }

    companion object {
        /** Null when there is nothing to detail (connect, get_public_key, ping…). */
        fun of(req: PendingBunkerRequest): RequestDetails? = when {
            req.type == SignerType.SIGN_EVENT ->
                (DetailEvent.parse(req.result) ?: DetailEvent.parse(req.preview))?.let(::OfEvent)
            req.type == SignerType.DECRYPT_ZAP_EVENT -> DetailEvent.parse(req.preview)?.let(::OfEvent)
            req.type == SignerType.SIGN_PSBT -> OfPsbt(PsbtDecoder.decode(req.preview, Hex.decode(req.account.hexKey)), req.preview)
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
    // Fill small windows (leaving room for the title and buttons), cap on large ones.
    val window = LocalWindowContentSize.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(if (LocalLayoutWidth.current == LayoutWidth.COMPACT) 12.dp else 24.dp),
        title = { Text(req.type.describe(req.kind, req.encryptedContent, language)) },
        text = {
            val scroll = rememberScrollState()
            Column(
                Modifier.heightIn(max = (window.height - 220.dp).coerceIn(120.dp, 520.dp)).verticalScroll(scroll),
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
                    is RequestDetails.OfPsbt -> PsbtSections(details, language)
                }
            }
        },
        confirmButton = {
            AmberTextButton(text = Strings.get("d_close", language), onClick = onDismiss)
        },
    )
}

private fun sats(amount: Long, language: String) = Strings.format("psbt_amount_sats", amount, language = language)

/** Mirrors Android's `PsbtBody`: sending and change outputs, the fee, then the raw PSBT. */
@Composable
private fun PsbtSections(details: RequestDetails.OfPsbt, language: String) {
    val decoded = details.decoded
    if (decoded.parseError != null) {
        Text(
            Strings.format("psbt_decode_error", decoded.parseError, language = language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        val (change, sending) = decoded.outputs.partition { it.isChange }
        PsbtOutputs(Strings.get("psbt_sending", language), decoded.totalSent, sending, language)
        PsbtOutputs(Strings.get("psbt_change", language), decoded.totalChange, change, language)
        decoded.feeSats?.let { DetailSection(Strings.get("psbt_fee", language), sats(it, language)) }
    }
    HorizontalDivider()
    DetailSection(Strings.get("psbt_raw", language), details.psbt, monospace = true)
}

@Composable
private fun PsbtOutputs(title: String, totalSats: Long, outputs: List<DecodedPsbtOutput>, language: String) {
    if (outputs.isEmpty()) return
    Text("$title: ${sats(totalSats, language)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    outputs.forEach { output ->
        // Non-segwit scripts have no bech32 address: show the script itself.
        val destination = output.address ?: output.scriptPubKeyHex
        DetailSection(sats(output.valueSats, language), destination, monospace = output.address == null)
    }
}

private fun contentLabel(language: String) = Strings.get("content", language).replaceFirstChar { it.uppercase() }

@Composable
private fun DetailSection(
    label: String,
    value: String,
    copyValue: String = value,
    monospace: Boolean = false,
) {
    val copyText = rememberCopyText()
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
                copyText(copyValue)
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
