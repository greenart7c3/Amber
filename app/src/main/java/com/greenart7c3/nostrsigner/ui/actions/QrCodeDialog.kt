package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.ui.InnerQrCodeDrawer
import com.greenart7c3.nostrsigner.ui.QrCodeDrawer
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.theme.Size35dp

@Suppress("NoComments")
@Composable
fun QrCodeScreen(
    content: String,
    modifier: Modifier = Modifier,
) {
    // Defense-in-depth (GHSA-8844-q5vh-9j8f, L4): this screen renders the
    // raw nsec/ncryptsec as a QR code (via Route.QrCode). Prevent
    // screenshots/screen recording for the lifetime of the screen.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size35dp),
            ) {
                QrCodeDrawer(content, Modifier.fillMaxSize())
            }
        }
    }
}

@Suppress("NoComments")
@Composable
fun QrCodeDialog(
    content: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        window?.attributes = window.attributes?.apply {
            screenBrightness = 1f
        }
        // Defense-in-depth (GHSA-8844-q5vh-9j8f, L4): this dialog renders the
        // raw nsec as a QR code. Prevent screenshots/screen recording for the
        // lifetime of the dialog. Mirrors SeedWordsPage/RandomPinInput.
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        onDispose {
            window?.attributes = window.attributes?.apply {
                screenBrightness = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(onCancel = onClose)
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                ) {
                    InnerQrCodeDrawer(content, Modifier.fillMaxSize())
                }
            }
        }
    }
}
