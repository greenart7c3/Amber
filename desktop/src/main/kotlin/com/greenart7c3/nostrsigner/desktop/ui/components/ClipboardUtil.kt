package com.greenart7c3.nostrsigner.desktop.ui.components

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Plain AWT clipboard access — works uniformly across Linux/Windows/macOS without a Compose clipboard API. */
fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}
