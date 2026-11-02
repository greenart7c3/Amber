package com.greenart7c3.nostrsigner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.client.android.Intents
import com.greenart7c3.nostrsigner.R
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun SimpleQrCodeScanner(
    onDismiss: () -> Unit = {},
    onScan: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionDenied by remember { mutableStateOf(false) }

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

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                qrLauncher.launch(scanOptions)
            } else {
                permissionDenied = true
            }
        }

    DisposableEffect(lifecycleOwner) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            qrLauncher.launch(scanOptions)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    if (permissionDenied) {
        AlertDialog(
            onDismissRequest = {
                permissionDenied = false
                onDismiss()
            },
            title = { Text(stringResource(R.string.camera_permission_required)) },
            text = { Text(stringResource(R.string.camera_permission_required_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                        onDismiss()
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.settings))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        permissionDenied = false
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
