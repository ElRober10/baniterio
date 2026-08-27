package com.baniterio.app.biometric

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

actual class BiometricAuthenticator(private val activity: FragmentActivity) {
    actual val estaDisponible: Boolean
        get() = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    actual suspend fun autenticar(): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) continuation.resume(false)
            }

            override fun onAuthenticationFailed() {
                // El usuario puede reintentar sin que la corrutina se resuelva todavía.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Acceder a la peña")
            .setSubtitle("Usa tu huella o Face ID")
            .setNegativeButtonText("Cancelar")
            .build()

        prompt.authenticate(info)
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator {
    val activity = LocalContext.current as FragmentActivity
    return remember(activity) { BiometricAuthenticator(activity) }
}
