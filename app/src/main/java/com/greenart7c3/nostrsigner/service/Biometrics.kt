package com.greenart7c3.nostrsigner.service

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import com.greenart7c3.nostrsigner.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object Biometrics {
    fun authenticate(
        title: String,
        context: Context,
        scope: CoroutineScope,
        keyguardLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
        onApproved: () -> Unit
    ) {
        val fragmentContext = context.getAppCompatActivity()!!
        val keyguardManager =
            fragmentContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (!keyguardManager.isDeviceSecure) {
            onApproved()
            return
        }

        @Suppress("DEPRECATION")
        fun keyguardPrompt() {
            val intent = keyguardManager.createConfirmDeviceCredentialIntent(
                context.getString(R.string.app_name),
                title
            )

            keyguardLauncher.launch(intent)
        }

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            keyguardPrompt()
            return
        }

        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_name))
            .setSubtitle(title)
            .setAllowedAuthenticators(authenticators)
            .build()

        val biometricPrompt = BiometricPrompt(
            fragmentContext,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> keyguardPrompt()
                        BiometricPrompt.ERROR_LOCKOUT -> keyguardPrompt()
                        else ->
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    "${context.getString(R.string.biometric_error)}: $errString",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    scope.launch {
                        Toast.makeText(
                            context,
                            context.getString(R.string.biometric_authentication_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onApproved()
                }
            }
        )

        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> biometricPrompt.authenticate(promptInfo)
            else -> keyguardPrompt()
        }
    }
}

fun Context.getAppCompatActivity(): AppCompatActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is AppCompatActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
