package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

/** Renders [text] as a black-on-white QR code image for display in the bunker connect screen. */
fun qrCodeImageBitmap(text: String, size: Int = 320): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until size) {
        for (y in 0 until size) {
            image.setRGB(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
    }
    return image.toComposeImageBitmap()
}
