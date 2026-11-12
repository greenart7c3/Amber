package com.greenart7c3.nostrsigner.cli

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.PrintStream

/**
 * Draws [content] as a QR code with half-block characters (two pixel rows per
 * line), so a phone can scan it straight off a terminal.
 */
fun printQrCode(
    stream: PrintStream,
    content: String,
) {
    val matrix = runCatching {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hashMapOf(EncodeHintType.MARGIN to 1))
    }.getOrNull() ?: throw CliError("could not build a QR code for the pairing URI", Exit.ERROR)
    val line = StringBuilder()
    var y = 0
    while (y < matrix.height) {
        line.setLength(0)
        for (x in 0 until matrix.width) {
            val top = matrix.get(x, y)
            val bottom = y + 1 < matrix.height && matrix.get(x, y + 1)
            line.append(
                when {
                    top && bottom -> "\u2588"
                    top -> "\u2580"
                    bottom -> "\u2584"
                    else -> " "
                },
            )
        }
        stream.println(line)
        y += 2
    }
}
