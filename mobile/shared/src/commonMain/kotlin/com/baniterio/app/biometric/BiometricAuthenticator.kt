package com.baniterio.app.biometric

import androidx.compose.runtime.Composable

expect class BiometricAuthenticator {
    val estaDisponible: Boolean
    suspend fun autenticar(): Boolean
}

@Composable
expect fun rememberBiometricAuthenticator(): BiometricAuthenticator
