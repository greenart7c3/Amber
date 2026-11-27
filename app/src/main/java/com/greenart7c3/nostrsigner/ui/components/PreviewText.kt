package com.greenart7c3.nostrsigner.ui.components

internal const val MAX_PREVIEW_CHARS = 4_096
internal const val MAX_TAG_PREVIEW_CHARS = 1_024
internal const val MAX_EXPANDED_TAGS = 200
internal const val MAX_CLIPBOARD_CHARS = 128 * 1024

internal fun truncateForPreview(text: String, maxChars: Int = MAX_PREVIEW_CHARS): String = if (text.length > maxChars) {
    "${text.take(maxChars)}…"
} else {
    text
}
