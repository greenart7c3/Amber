package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark

/**
 * Renders a subset of Markdown as styled Compose text.
 *
 * Supported syntax:
 *   ## Heading
 *   **bold**
 *   [link text](url)
 *   - bullet item
 *   1. numbered item
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val headingFontSize: TextUnit = MaterialTheme.typography.titleMedium.fontSize

    val annotatedString = remember(markdown, linkColor, headingFontSize) {
        buildAnnotatedString {
            val lines = markdown.trim().lines()
            for ((index, line) in lines.withIndex()) {
                if (index > 0) append("\n")
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("## ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = headingFontSize)) {
                            appendMarkdownInline(trimmed.removePrefix("## "), linkColor)
                        }
                    }
                    trimmed.startsWith("### ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendMarkdownInline(trimmed.removePrefix("### "), linkColor)
                        }
                    }
                    trimmed.startsWith("- ") -> {
                        append("\u2022 ")
                        appendMarkdownInline(trimmed.removePrefix("- "), linkColor)
                    }
                    trimmed.matches(Regex("^\\d+\\. .+")) -> {
                        val dotIdx = trimmed.indexOf(". ")
                        append(trimmed.substring(0, dotIdx + 2))
                        appendMarkdownInline(trimmed.substring(dotIdx + 2), linkColor)
                    }
                    else -> {
                        appendMarkdownInline(trimmed, linkColor)
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        modifier = modifier,
        style = style,
    )
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    text: String,
    linkColor: Color,
) {
    var remaining = text
    while (remaining.isNotEmpty()) {
        val consumed = when {
            remaining.startsWith("**") -> appendBold(remaining)
            remaining.startsWith("[") -> appendLink(remaining, linkColor)
            else -> appendPlain(remaining)
        }
        remaining = remaining.substring(consumed)
    }
}

/** `**bold**` at the start of [text]; a `**` without its closing pair stays as is. Returns the characters used. */
private fun AnnotatedString.Builder.appendBold(text: String): Int {
    val endIdx = text.indexOf("**", 2)
    if (endIdx == -1) {
        append("**")
        return 2
    }
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
        append(text.substring(2, endIdx))
    }
    return endIdx + 2
}

/** `[text](url)` at the start of [text]; anything else keeps its `[`. Returns the characters used. */
private fun AnnotatedString.Builder.appendLink(
    text: String,
    linkColor: Color,
): Int {
    val textEnd = text.indexOf("](")
    val urlEnd = if (textEnd > 0) text.indexOf(")", textEnd + 2) else -1
    if (urlEnd == -1) {
        append(text[0])
        return 1
    }
    val linkStyle = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    withLink(LinkAnnotation.Url(text.substring(textEnd + 2, urlEnd), linkStyle)) {
        append(text.substring(1, textEnd))
    }
    return urlEnd + 1
}

/** Plain text up to the next `**` or `[` (at least one character). Returns the characters used. */
private fun AnnotatedString.Builder.appendPlain(text: String): Int {
    val nextSpecial = listOf(text.indexOf("**"), text.indexOf("[")).filter { it >= 0 }.minOrNull() ?: text.length
    val count = if (nextSpecial == 0) 1 else nextSpecial
    append(text.substring(0, count))
    return count
}

@PreviewsLightDark
@Composable
private fun MarkdownTextPreview() {
    AmberPreview {
        MarkdownText(
            markdown = """
                ## Amber
                **Amber** is a Nostr event signer for Android.
                - Keep your keys safe
                - Sign events for [Nostr](https://nostr.com) apps
                1. Install a Nostr client
                2. Approve its requests
            """.trimIndent(),
            modifier = Modifier.padding(16.dp),
        )
    }
}
