package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.rememberTypeDisplayOrder
import java.awt.image.BufferedImage
import kotlinx.coroutines.flow.MutableSharedFlow

/** Global snackbar feed, the desktop stand-in for the mobile ToastManager. */
object Toaster {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun toast(message: String) {
        messages.tryEmit(message)
    }
}

/**
 * Amber's primary button. Sized to its content like a native desktop control;
 * pass [fillWidth] for the few centered forms (login/unlock) that want the
 * mobile-style full-width action.
 */
@Composable
fun AmberButton(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = ButtonBorder,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = orange, contentColor = Color.White),
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
    ) {
        Text(text)
    }
}

@Composable
fun AmberOutlinedButton(
    modifier: Modifier = Modifier,
    text: String,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = ButtonBorder,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
    ) {
        Text(text)
    }
}

/** "Remember my choice" selector shared by the approval cards. */
@Composable
fun RememberTypeSelector(
    value: RememberType,
    onValueChange: (RememberType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val language by Strings.currentLanguage.collectAsState()
    OutlinedButton(
        onClick = { expanded = true },
        shape = ButtonBorder,
    ) {
        Text(Strings.format("d_remember_prefix", value.label(language), language = language), style = MaterialTheme.typography.bodySmall)
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        rememberTypeDisplayOrder.forEach { type ->
            DropdownMenuItem(
                text = { Text(type.label(language)) },
                onClick = {
                    onValueChange(type)
                    expanded = false
                },
            )
        }
    }
}

@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 300.dp,
) {
    val image = remember(content) {
        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            768,
            768,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val bufferedImage = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until matrix.width) {
            for (y in 0 until matrix.height) {
                bufferedImage.setRGB(x, y, if (matrix.get(x, y)) 0x000000 else 0xFFFFFF)
            }
        }
        bufferedImage.toComposeImageBitmap()
    }
    Image(
        bitmap = image,
        contentDescription = Strings.get("d_qr_code"),
        modifier = modifier.size(size),
    )
}
