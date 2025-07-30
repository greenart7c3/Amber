package com.greenart7c3.nostrsigner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun SimpleQrCodeScanner(onScan: (String?) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current

    val qrLauncher =
        rememberLauncherForActivityResult(ScanContract()) {
            if (it.contents != null) {
                onScan(it.contents)
            } else {
                onScan(null)
            }
        }

    val scanOptions =
        ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Point to the QR Code")
            setBeepEnabled(false)
            setOrientationLocked(false)
            addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
        }

    DisposableEffect(lifecycleOwner) {
        qrLauncher.launch(scanOptions)
        onDispose { }
    }
}
