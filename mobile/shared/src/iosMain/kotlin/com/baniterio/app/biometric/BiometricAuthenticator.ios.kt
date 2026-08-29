package com.baniterio.app.biometric

import androidx.compose.runtime.Composable

actual class BiometricAuthenticator {
    // TODO: integrar LocalAuthentication (Face ID/Touch ID) cuando este target
    // se compile en el Mac mini. Hasta entonces el stub falla en cerrado a
    // propósito: DesbloqueoScreen es la puerta de entrada de la app, así que
    // devolver `true` dejaría entrar a cualquiera con el móvil desbloqueado.
    // `estaDisponible = false` hace además que LoginScreen no ofrezca guardar
    // credenciales para huella en iOS.
    actual val estaDisponible: Boolean = false

    actual suspend fun autenticar(): Boolean = false
}

@Composable
actual fun rememberBiometricAuthenticator(): BiometricAuthenticator = BiometricAuthenticator()
