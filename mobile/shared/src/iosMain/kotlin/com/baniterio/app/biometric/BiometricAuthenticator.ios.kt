package com.baniterio.app.biometric

import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay

actual class BiometricAuthenticator {
    actual val estaDisponible: Boolean = true

    actual suspend fun autenticar(): Boolean {
        // TODO: integrar LocalAuthentication (Face ID/Touch ID) cuando este target
        // se compile en el Mac mini. De momento simula un acceso correcto.
        delay(600)
        return true
    }
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator = BiometricAuthenticator()
