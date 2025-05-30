package com.greenart7c3.nostrsigner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

class QrCodeScannerActivity : ComponentActivity() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var hasFailedToResumeCamera = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            setupScanner()
        } else {
            Toast.makeText(this, "Camera permission denied.", Toast.LENGTH_SHORT).show()
            sendResultAndFinish(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasCameraPermission()) {
            setupScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupScanner() {
        barcodeView = DecoratedBarcodeView(this).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            initializeFromIntent(intent)
            decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    result?.text?.let {
                        sendResultAndFinish(it)
                    }
                }

                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
            })
        }

        setContentView(barcodeView)
        safeResumeScanner()
    }

    private fun safeResumeScanner() {
        try {
            barcodeView.resume()
        } catch (e: Exception) {
            Log.e("QrScanner", "Camera resume failed: ${e.message}")
            if (!hasFailedToResumeCamera) {
                hasFailedToResumeCamera = true
                Toast.makeText(this, "Camera is in use by another app.", Toast.LENGTH_LONG).show()
                barcodeView.postDelayed({
                    sendResultAndFinish(null)
                }, 1500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::barcodeView.isInitialized && hasWindowFocus()) {
            safeResumeScanner()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::barcodeView.isInitialized) {
            barcodeView.pause()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::barcodeView.isInitialized) {
            if (hasFocus) safeResumeScanner() else barcodeView.pause()
        }
    }

    private fun sendResultAndFinish(result: String?) {
        setResult(RESULT_OK, Intent().putExtra("qr_result", result))
        finish()
    }
}
