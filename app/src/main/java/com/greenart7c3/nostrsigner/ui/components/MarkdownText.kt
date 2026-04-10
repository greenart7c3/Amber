package com.greenart7c3.nostrsigner.ui.components

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
        when {
            remaining.startsWith("**") -> {
                val endIdx = remaining.indexOf("**", 2)
                if (endIdx != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(remaining.substring(2, endIdx))
                    }
                    remaining = remaining.substring(endIdx + 2)
                } else {
                    append("**")
                    remaining = remaining.substring(2)
                }
            }
            remaining.startsWith("[") -> {
                val textEnd = remaining.indexOf("](")
                if (textEnd > 0) {
                    val urlEnd = remaining.indexOf(")", textEnd + 2)
                    if (urlEnd != -1) {
                        val linkText = remaining.substring(1, textEnd)
                        val url = remaining.substring(textEnd + 2, urlEnd)
                        withLink(
                            LinkAnnotation.Url(
                                url,
                                TextLinkStyles(
                                    SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                            ),
                        ) {
                            append(linkText)
                        }
                        remaining = remaining.substring(urlEnd + 1)
                    } else {
                        append(remaining[0])
                        remaining = remaining.substring(1)
                    }
                } else {
                    append(remaining[0])
                    remaining = remaining.substring(1)
                }
            }
            else -> {
                val boldIdx = remaining.indexOf("**")
                val linkIdx = remaining.indexOf("[")
                val nextSpecial = when {
                    boldIdx == -1 && linkIdx == -1 -> remaining.length
                    boldIdx == -1 -> linkIdx
                    linkIdx == -1 -> boldIdx
                    else -> minOf(boldIdx, linkIdx)
                }
                val count = if (nextSpecial == 0) 1 else nextSpecial
                append(remaining.substring(0, count))
                remaining = remaining.substring(count)
            }
        }
    }
}
