package com.baniterio.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// Deprecado en security-crypto 1.1.x pero funcional; endurecer más adelante.
actual class AlmacenCredenciales(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "baniterio_credenciales",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    actual fun guardar(telefono: String, password: String) {
        prefs.edit()
            .putString(CLAVE_TELEFONO, telefono)
            .putString(CLAVE_PASSWORD, password)
            .apply()
    }

    actual fun leer(): Credenciales? {
        val t = prefs.getString(CLAVE_TELEFONO, null) ?: return null
        val p = prefs.getString(CLAVE_PASSWORD, null) ?: return null
        return Credenciales(t, p)
    }

    actual fun borrar() {
        prefs.edit().clear().apply()
    }

    actual val hayCredenciales: Boolean get() = prefs.contains(CLAVE_TELEFONO)

    private companion object {
        const val CLAVE_TELEFONO = "telefono"
        const val CLAVE_PASSWORD = "password"
    }
}
