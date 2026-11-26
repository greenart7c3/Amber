package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.fasterxml.jackson.databind.JsonNode
import com.greenart7c3.nostrsigner.common.RELAY_AUTH_KIND
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.common.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.common.psbt.DecodedPsbt
import com.greenart7c3.nostrsigner.common.psbt.DecodedPsbtOutput
import com.greenart7c3.nostrsigner.common.psbt.PsbtDecoder
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.content
import com.greenart7c3.nostrsigner.commonsui.copy
import com.greenart7c3.nostrsigner.commonsui.d_close
import com.greenart7c3.nostrsigner.commonsui.d_copied_clipboard
import com.greenart7c3.nostrsigner.commonsui.date
import com.greenart7c3.nostrsigner.commonsui.event_kind
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.kind
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.psbt_amount_sats
import com.greenart7c3.nostrsigner.commonsui.psbt_change
import com.greenart7c3.nostrsigner.commonsui.psbt_decode_error
import com.greenart7c3.nostrsigner.commonsui.psbt_fee
import com.greenart7c3.nostrsigner.commonsui.psbt_raw
import com.greenart7c3.nostrsigner.commonsui.psbt_sending
import com.greenart7c3.nostrsigner.commonsui.pubkey
import com.greenart7c3.nostrsigner.commonsui.tags
import com.greenart7c3.nostrsigner.desktop.core.DateTimes
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContent
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContentType
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.utils.Hex
import org.jetbrains.compose.resources.stringResource

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

internal fun DetailEvent.kindName(): String {
    val name = Permission("sign_event", kind).localizedBlocking()
    val nip31AltTag = tags.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
    return if (nip31AltTag != null && isGenericKindLabel(name)) nip31AltTag else name
}

private fun DetailEvent.isGenericKindLabel(name: String) = name == getStringBlocking(Res.string.event_kind, kind.toString())

private fun DetailEvent.authenticatedRelay(): String? = tags.firstOrNull { it.size > 1 && it[0] == "relay" }?.get(1)

private fun JsonNode?.toTags(): List<List<String>> = this?.takeIf { it.isArray }?.map { tag -> tag.map { it.asText() } }.orEmpty()

private fun List<List<String>>.format(): String = joinToString(", ") { tag -> tag.joinToString(", ", "[", "]") { "\"$it\"" } }

internal sealed interface RequestDetails {
    data class OfEvent(val event: DetailEvent) : RequestDetails

    data class OfTags(val tags: List<List<String>>) : RequestDetails

    data class OfText(val text: String) : RequestDetails

    data class OfPsbt(val decoded: DecodedPsbt, val psbt: String) : RequestDetails

    fun summary(): String = when (this) {
        is OfEvent -> if (event.kind == RELAY_AUTH_KIND) {
            event.authenticatedRelay() ?: event.content
        } else {
            event.content
        }
        is OfTags -> tags.format()
        is OfText -> text
        is OfPsbt -> decoded.parseError?.let { getStringBlocking(Res.string.psbt_decode_error, it) }
            ?: listOfNotNull(
                "${getStringBlocking(Res.string.psbt_sending)}: ${sats(decoded.totalSent)}",
                "${getStringBlocking(Res.string.psbt_change)}: ${sats(decoded.totalChange)}",
                decoded.feeSats?.let { "${getStringBlocking(Res.string.psbt_fee)}: ${sats(it)}" },
            ).joinToString(" · ")
    }

    companion object {
        fun of(req: PendingBunkerRequest): RequestDetails? = when (req.type) {
            SignerType.SIGN_EVENT ->
                (DetailEvent.parse(req.result) ?: DetailEvent.parse(req.preview))?.let(::OfEvent)
            SignerType.DECRYPT_ZAP_EVENT -> DetailEvent.parse(req.preview)?.let(::OfEvent)
            SignerType.SIGN_PSBT -> OfPsbt(PsbtDecoder.decode(req.preview, Hex.decode(req.account.hexKey)), req.preview)
            in contentScopedSignerTypes, in nip44v3SignerTypes -> {
                val content = req.encryptedContent ?: EncryptedContent.classify(req.preview)
                when (content.type) {
                    EncryptedContentType.EVENT -> (DetailEvent.parse(req.sealPreview) ?: DetailEvent.parse(req.preview))?.let(::OfEvent)
                    EncryptedContentType.TAG_ARRAY -> runCatching { JacksonMapper.mapper.readTree(req.preview).toTags() }.getOrNull()?.let(::OfTags)
                    EncryptedContentType.CLEAR_TEXT -> null
                } ?: OfText(req.preview)
            }
            else -> null
        }
    }
}

private val DIALOG_TITLE_AND_BUTTONS_HEIGHT = 220.dp
private val MIN_DETAILS_HEIGHT = 120.dp
private val MAX_DETAILS_HEIGHT = 520.dp

@Composable
internal fun RequestDetailsDialog(
    req: PendingBunkerRequest,
    details: RequestDetails,
    onDismiss: () -> Unit,
) {
    val window = LocalWindowContentSize.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(if (LocalLayoutWidth.current == LayoutWidth.COMPACT) 12.dp else 24.dp),
        title = { Text(req.type.describe(req.kind, req.encryptedContent)) },
        text = {
            val scroll = rememberScrollState()
            Column(
                Modifier.heightIn(max = (window.height - DIALOG_TITLE_AND_BUTTONS_HEIGHT).coerceIn(MIN_DETAILS_HEIGHT, MAX_DETAILS_HEIGHT)).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (details) {
                    is RequestDetails.OfEvent -> {
                        val event = details.event
                        DetailSection(stringResource(Res.string.kind), "${event.kind} - ${event.kindName()}")
                        if (event.pubKey.isNotBlank()) {
                            DetailSection(stringResource(Res.string.pubkey), event.pubKey.toShortenHex(), copyValue = event.pubKey)
                        }
                        DetailSection(
                            stringResource(Res.string.date),
                            DateTimes.format(event.createdAt * 1000),
                            copyValue = event.createdAt.toString(),
                        )
                        if (event.content.isNotEmpty()) {
                            DetailSection(contentLabel(), event.content)
                        }
                        if (event.tags.isNotEmpty()) {
                            TagsSection(stringResource(Res.string.tags), event.tags)
                        }
                    }
                    is RequestDetails.OfTags -> TagsSection(stringResource(Res.string.tags), details.tags)
                    is RequestDetails.OfText -> DetailSection(contentLabel(), details.text)
                    is RequestDetails.OfPsbt -> PsbtSections(details)
                }
            }
        },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.d_close), onClick = onDismiss)
        },
    )
}

private fun sats(amount: Long) = getStringBlocking(Res.string.psbt_amount_sats, amount)

@Composable
private fun ColumnScope.PsbtSections(details: RequestDetails.OfPsbt) {
    val decoded = details.decoded
    val parseError = decoded.parseError
    if (parseError != null) {
        Text(
            stringResource(Res.string.psbt_decode_error, parseError),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    } else {
        val (change, sending) = decoded.outputs.partition { it.isChange }
        PsbtOutputs(stringResource(Res.string.psbt_sending), decoded.totalSent, sending)
        PsbtOutputs(stringResource(Res.string.psbt_change), decoded.totalChange, change)
        decoded.feeSats?.let { DetailSection(stringResource(Res.string.psbt_fee), sats(it)) }
    }
    HorizontalDivider()
    DetailSection(stringResource(Res.string.psbt_raw), details.psbt, monospace = true)
}

@Composable
private fun ColumnScope.PsbtOutputs(title: String, totalSats: Long, outputs: List<DecodedPsbtOutput>) {
    if (outputs.isEmpty()) return
    Text("$title: ${sats(totalSats)}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    outputs.forEach { output ->
        val addressOrNonSegwitScript = output.address ?: output.scriptPubKeyHex
        DetailSection(sats(output.valueSats), addressOrNonSegwitScript, monospace = output.address == null)
    }
}

private fun contentLabel() = getStringBlocking(Res.string.content).replaceFirstChar { it.uppercase() }

@Composable
private fun ColumnScope.DetailSection(
    label: String,
    value: String,
    copyValue: String = value,
    monospace: Boolean = false,
) {
    val copyText = rememberCopyText()
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
                Toaster.toast(getStringBlocking(Res.string.d_copied_clipboard))
            },
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(Res.string.copy).trim(), modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider()
}

@Composable
private fun ColumnScope.TagsSection(label: String, tags: List<List<String>>) {
    DetailSection(
        label = label,
        value = tags.joinToString("\n") { tag -> tag.joinToString(", ", "[", "]") { "\"$it\"" } },
        copyValue = tags.format(),
        monospace = true,
    )
}
