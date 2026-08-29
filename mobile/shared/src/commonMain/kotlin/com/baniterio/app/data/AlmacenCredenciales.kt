package com.baniterio.app.data

data class Credenciales(val telefono: String, val password: String)

/**
 * Guarda de forma segura el teléfono y la contraseña para poder re-autenticarse
 * tras un desbloqueo biométrico en cada arranque (modelo de sesión del móvil).
 * Android: EncryptedSharedPreferences. iOS: Keychain.
 * El token JWT NO se guarda aquí (vive solo en memoria en AuthRepository).
 */
expect class AlmacenCredenciales {
    fun guardar(telefono: String, password: String)
    fun leer(): Credenciales?
    fun borrar()
    val hayCredenciales: Boolean
}
