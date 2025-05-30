package com.greenart7c3.nostrsigner.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.greenart7c3.nostrsigner.QrCodeScannerActivity

@Composable
fun SimpleQrCodeScanner(
    onScan: (String?) -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data?.getStringExtra("qr_result")
        onScan(text)
    }

    LaunchedEffect(Unit) {
        val intent = Intent(context, QrCodeScannerActivity::class.java)
        launcher.launch(intent)
    }
}
