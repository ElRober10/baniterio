package com.baniterio.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

// Deprecado en security-crypto 1.1.x pero funcional; endurecer más adelante.
actual class AlmacenCredenciales(context: Context) {

    /**
     * Si el fichero de prefs existe pero la clave del Keystore ya no (restauración
     * de dispositivo a dispositivo, o invalidación al cambiar el bloqueo de pantalla),
     * `create()` lanza GeneralSecurityException/IOException y la app entraría en
     * bucle de crash al arrancar. Se borra el fichero corrupto y se reintenta una vez:
     * el coste es perder las credenciales guardadas → un login manual.
     */
    private val prefs: SharedPreferences = try {
        crearPrefs(context)
    } catch (e: GeneralSecurityException) {
        context.deleteSharedPreferences(NOMBRE_FICHERO)
        crearPrefs(context)
    } catch (e: IOException) {
        context.deleteSharedPreferences(NOMBRE_FICHERO)
        crearPrefs(context)
    }

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
        const val NOMBRE_FICHERO = "baniterio_credenciales"
        const val CLAVE_TELEFONO = "telefono"
        const val CLAVE_PASSWORD = "password"

        fun crearPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                NOMBRE_FICHERO,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
